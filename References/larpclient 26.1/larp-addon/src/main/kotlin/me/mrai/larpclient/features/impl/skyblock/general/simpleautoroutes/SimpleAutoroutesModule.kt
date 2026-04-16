package me.mrai.larpclient.features.impl.skyblock.general.simpleautoroutes

import com.google.gson.GsonBuilder
import com.mojang.blaze3d.platform.InputConstants
import me.mrai.larpclient.features.impl.misc.other.etherwarp.EtherwarpController
import me.mrai.larpclient.module.KeybindSetting
import me.mrai.larpclient.module.Module
import me.mrai.larpclient.module.ModuleCategory
import me.mrai.larpclient.module.SliderSetting
import me.mrai.larpclient.render.WorldRingRenderer
import me.mrai.larpclient.util.LarpBranding
import me.mrai.larpclient.util.LarpLog
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.Minecraft
import net.minecraft.client.player.LocalPlayer
import net.minecraft.core.BlockPos
import net.minecraft.world.level.ClipContext
import net.minecraft.world.item.Items
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import org.lwjgl.glfw.GLFW
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import kotlin.math.abs

object SimpleAutoroutesModule : Module(
    name = "Simple Autoroutes",
    description = "Etherwarp routes with await click and raytrace gating.",
    category = ModuleCategory.SKYBLOCK_GENERAL
) {
    data class ActionResult(
        val ok: Boolean,
        val message: String
    )

    data class RouteNode(
        var id: String = UUID.randomUUID().toString(),
        var x: Double,
        var y: Double,
        var z: Double,
        var targetX: Double,
        var targetY: Double,
        var targetZ: Double,
        var start: Boolean = false,
        var awaitClick: Boolean = false,
        var awaitEtherwarpRaytrace: Boolean = false
    )

    data class Route(
        var name: String,
        var dimension: String,
        var nodes: MutableList<RouteNode> = mutableListOf()
    )

    private data class Config(
        var routes: MutableList<Route> = mutableListOf()
    )

    private data class NodeRuntime(
        var triggered: Boolean = false,
        var clickSatisfied: Boolean = false
    )

    private data class ActivationCandidate(
        val node: RouteNode,
        val distanceSqr: Double
    )

    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val configPath: Path =
        FabricLoader.getInstance().configDir.resolve("larpclient/simple_autoroutes.json")

    private val triggerKey = KeybindSetting("Trigger Key")
    private val nodeRadius = SliderSetting("Node Radius", 0.55, 0.15, 3.0, 0.05)
    private val ringThickness = SliderSetting("Ring Thickness", 0.055, 0.01, 0.24, 0.005)
    private val ringHeight = SliderSetting("Ring Height", 0.03, 0.01, 0.20, 0.01)
    private val targetRadius = SliderSetting("Target Radius", 0.24, 0.08, 1.0, 0.01)

    private var config = Config()
    private val runtimeStates = hashMapOf<String, NodeRuntime>()

    private var currentAwaitNodeId: String? = null
    private var lastAttackDown = false
    private var lastUseDown = false
    private var lastTriggerDown = false
    private var lastDimensionKey: String? = null
    private var actionCooldownTicks = 0

    private const val MAX_VERTICAL_DELTA = 2.0
    private const val RAYTRACE_RANGE = 61.0
    private const val TRIGGER_FILL_ALPHA = 0.16f
    private const val TRIGGER_OUTLINE_ALPHA = 0.92f
    private const val TARGET_FILL_ALPHA = 0.08f
    private const val TARGET_OUTLINE_ALPHA = 0.68f
    private const val ACTION_COOLDOWN_AFTER_USE_TICKS = 2

    init {
        settings += listOf(
            triggerKey,
            nodeRadius,
            ringThickness,
            ringHeight,
            targetRadius
        )
        load()
    }

    override fun onDisable() {
        resetRuntime(clearDimension = false)
    }

    override fun onTick() {
        val client = Minecraft.getInstance()
        val player = client.player ?: run {
            resetRuntime()
            return
        }
        val level = client.level ?: run {
            resetRuntime()
            return
        }

        val dimensionKey = level.dimension().toString()
        if (lastDimensionKey != null && lastDimensionKey != dimensionKey) {
            resetRuntime(clearInputs = false, clearDimension = false)
        }
        lastDimensionKey = dimensionKey
        if (actionCooldownTicks > 0) {
            actionCooldownTicks--
        }

        val manualTrigger = pollManualTrigger(client, active = client.screen == null)
        if (client.screen != null) {
            return
        }

        currentAwaitNodeId?.let { nodeId ->
            if (manualTrigger) {
                runtimeStates.getOrPut(nodeId) { NodeRuntime() }.clickSatisfied = true
            }
        }

        runRoutes(player, dimensionKey)
    }

    fun render(context: LevelRenderContext) {
        if (!enabled) return

        val client = Minecraft.getInstance()
        val player = client.player ?: return
        val level = client.level ?: return
        val dimensionKey = level.dimension().toString()

        val rings = mutableListOf<WorldRingRenderer.RingSpec>()
        for (route in config.routes) {
            if (route.dimension != dimensionKey) continue

            for (node in route.nodes) {
                val state = runtimeStates[node.id]
                val waitingClick = currentAwaitNodeId == node.id && node.awaitClick && state?.clickSatisfied != true
                val waitingTrace = currentAwaitNodeId == node.id &&
                    node.awaitEtherwarpRaytrace &&
                    !passesEtherwarpRaytrace(node, player)

                val triggerColor = when {
                    waitingClick || waitingTrace -> LarpBranding.GOLD
                    node.start -> LarpBranding.GREEN
                    else -> LarpBranding.AQUA
                }
                val targetColor = LarpBranding.PINK

                rings += ringSpec(
                    centerX = node.x,
                    centerY = node.y + 0.02,
                    centerZ = node.z,
                    radius = nodeRadius.value,
                    thickness = ringThickness.value,
                    height = ringHeight.value,
                    argb = triggerColor,
                    fillAlpha = TRIGGER_FILL_ALPHA,
                    outlineAlpha = TRIGGER_OUTLINE_ALPHA
                )

                rings += ringSpec(
                    centerX = node.targetX,
                    centerY = node.targetY + 0.02,
                    centerZ = node.targetZ,
                    radius = targetRadius.value,
                    thickness = (ringThickness.value * 0.75).coerceAtLeast(0.02),
                    height = ringHeight.value,
                    argb = targetColor,
                    fillAlpha = TARGET_FILL_ALPHA,
                    outlineAlpha = TARGET_OUTLINE_ALPHA
                )
            }
        }

        if (rings.isNotEmpty()) {
            WorldRingRenderer.render(context, rings)
        }
    }

    fun onWorldChange() {
        resetRuntime()
    }

    fun routeNames(): List<String> {
        return config.routes
            .map { it.name }
            .sortedBy { it.lowercase() }
    }

    fun nodeIndexSuggestions(routeName: String): List<String> {
        val route = findRoute(routeName) ?: return emptyList()
        return route.nodes.indices.map(Int::toString)
    }

    fun describeRoutes(): List<String> {
        return config.routes
            .sortedBy { it.name.lowercase() }
            .map { route ->
                "${route.name} [${displayDimension(route.dimension)}] - ${route.nodes.size} node${if (route.nodes.size == 1) "" else "s"}"
            }
    }

    fun describeRoute(routeName: String): List<String> {
        val route = findRoute(routeName) ?: return emptyList()
        if (route.nodes.isEmpty()) {
            return listOf("${route.name} [${displayDimension(route.dimension)}] - empty")
        }

        val header = "${route.name} [${displayDimension(route.dimension)}] - ${route.nodes.size} node${if (route.nodes.size == 1) "" else "s"}"
        val lines = route.nodes.mapIndexed { index, node ->
            val flags = buildList {
                if (node.start) add("start")
                if (node.awaitClick) add("click")
                if (node.awaitEtherwarpRaytrace) add("trace")
            }.joinToString(", ")

            buildString {
                append("#")
                append(index)
                append(" -> ")
                append(formatVec(node.targetX, node.targetY, node.targetZ))
                if (flags.isNotBlank()) {
                    append(" [")
                    append(flags)
                    append("]")
                }
            }
        }

        return listOf(header) + lines
    }

    fun addEtherwarpNode(
        routeName: String,
        start: Boolean,
        awaitClick: Boolean,
        awaitEtherwarpRaytrace: Boolean
    ): ActionResult {
        val cleanedRouteName = routeName.trim()
        if (cleanedRouteName.isBlank()) {
            return ActionResult(false, "Route name cannot be blank.")
        }

        val client = Minecraft.getInstance()
        val player = client.player ?: return ActionResult(false, "Player is unavailable.")
        val level = client.level ?: return ActionResult(false, "World is unavailable.")
        val target = raytraceEtherwarpTarget(player) ?: return ActionResult(
            false,
            "Look at a valid etherwarp block with free headroom first."
        )

        val dimensionKey = level.dimension().toString()
        val route = findRoute(cleanedRouteName)
        if (route != null && route.dimension != dimensionKey) {
            return ActionResult(
                false,
                "Route '$cleanedRouteName' belongs to ${displayDimension(route.dimension)}, not ${displayDimension(dimensionKey)}."
            )
        }

        val activeRoute = route ?: Route(
            name = cleanedRouteName,
            dimension = dimensionKey
        ).also { config.routes += it }

        activeRoute.nodes += RouteNode(
            x = player.x,
            y = player.y,
            z = player.z,
            targetX = target.x,
            targetY = target.y,
            targetZ = target.z,
            start = start,
            awaitClick = awaitClick,
            awaitEtherwarpRaytrace = awaitEtherwarpRaytrace
        )
        save()

        return ActionResult(
            true,
            "Added etherwarp node #${activeRoute.nodes.lastIndex} to '${activeRoute.name}'."
        )
    }

    fun removeNearestNode(routeName: String): ActionResult {
        val route = findRoute(routeName) ?: return ActionResult(false, "Unknown route '$routeName'.")
        if (route.nodes.isEmpty()) return ActionResult(false, "Route '${route.name}' has no nodes.")

        val player = Minecraft.getInstance().player ?: return ActionResult(false, "Player is unavailable.")
        val index = route.nodes.indices.minByOrNull { idx ->
            distanceToNodeSqr(route.nodes[idx], player.position())
        } ?: return ActionResult(false, "Route '${route.name}' has no nodes.")

        return removeNode(route.name, index)
    }

    fun removeNode(routeName: String, index: Int): ActionResult {
        val route = findRoute(routeName) ?: return ActionResult(false, "Unknown route '$routeName'.")
        if (index !in route.nodes.indices) {
            return ActionResult(false, "Invalid node index for route '${route.name}'.")
        }

        val removed = route.nodes.removeAt(index)
        runtimeStates.remove(removed.id)
        if (currentAwaitNodeId == removed.id) {
            currentAwaitNodeId = null
        }
        save()

        return ActionResult(true, "Removed node #$index from '${route.name}'.")
    }

    fun clearRoute(routeName: String): ActionResult {
        val route = findRoute(routeName) ?: return ActionResult(false, "Unknown route '$routeName'.")
        route.nodes.forEach { runtimeStates.remove(it.id) }
        route.nodes.clear()
        currentAwaitNodeId = null
        save()
        return ActionResult(true, "Cleared route '${route.name}'.")
    }

    fun deleteRoute(routeName: String): ActionResult {
        val iterator = config.routes.iterator()
        while (iterator.hasNext()) {
            val route = iterator.next()
            if (!route.name.equals(routeName, ignoreCase = true)) continue

            route.nodes.forEach { runtimeStates.remove(it.id) }
            iterator.remove()
            currentAwaitNodeId = null
            save()
            return ActionResult(true, "Deleted route '${route.name}'.")
        }

        return ActionResult(false, "Unknown route '$routeName'.")
    }

    private fun runRoutes(player: LocalPlayer, dimensionKey: String) {
        val routes = config.routes.filter { it.dimension == dimensionKey }
        val nodes = routes.flatMap { it.nodes }

        if (nodes.isEmpty()) {
            currentAwaitNodeId = null
            runtimeStates.clear()
            return
        }

        val playerPos = player.position()
        val candidates = collectActivationCandidates(routes, playerPos)
        resetExitedNodes(nodes, candidates.mapTo(hashSetOf()) { it.node.id })

        if (candidates.isEmpty()) {
            if (currentAwaitNodeId != null) {
                currentAwaitNodeId = null
            }
            return
        }

        val node = selectCandidate(candidates) ?: return
        val runtime = runtimeStates.getOrPut(node.id) { NodeRuntime() }

        if (currentAwaitNodeId != node.id) {
            currentAwaitNodeId = node.id
            runtime.clickSatisfied = false
        }

        if (shouldAwait(node, runtime, player)) {
            return
        }

        if (actionCooldownTicks > 0) {
            return
        }

        if (!performEtherwarp(player, node)) {
            return
        }

        runtime.triggered = true
        currentAwaitNodeId = null
        actionCooldownTicks = ACTION_COOLDOWN_AFTER_USE_TICKS
    }

    private fun collectActivationCandidates(routes: List<Route>, playerPos: Vec3): List<ActivationCandidate> {
        val bestByNodeId = linkedMapOf<String, ActivationCandidate>()

        for (route in routes) {
            for ((index, node) in route.nodes.withIndex()) {
                registerActivationCandidate(
                    bestByNodeId = bestByNodeId,
                    node = node,
                    active = isInsideSourceRing(node, playerPos),
                    distanceSqr = distanceToSourceRingSqr(node, playerPos)
                )

                val previousNode = route.nodes.getOrNull(index - 1) ?: continue
                registerActivationCandidate(
                    bestByNodeId = bestByNodeId,
                    node = node,
                    active = isInsideTargetRing(previousNode, playerPos),
                    distanceSqr = distanceToTargetRingSqr(previousNode, playerPos)
                )
            }
        }

        return bestByNodeId.values
            .filterNot { runtimeStates.getOrPut(it.node.id) { NodeRuntime() }.triggered }
    }

    private fun registerActivationCandidate(
        bestByNodeId: MutableMap<String, ActivationCandidate>,
        node: RouteNode,
        active: Boolean,
        distanceSqr: Double
    ) {
        if (!active) return

        val candidate = ActivationCandidate(
            node = node,
            distanceSqr = distanceSqr
        )
        val existing = bestByNodeId[node.id]
        if (existing == null || candidate.distanceSqr < existing.distanceSqr) {
            bestByNodeId[node.id] = candidate
        }
    }

    private fun selectCandidate(candidates: List<ActivationCandidate>): RouteNode? {
        currentAwaitNodeId?.let { awaitingId ->
            candidates.firstOrNull { it.node.id == awaitingId }?.let { return it.node }
        }
        return candidates.minByOrNull { it.distanceSqr }?.node
    }

    private fun resetExitedNodes(nodes: List<RouteNode>, activeNodeIds: Set<String>) {
        val validIds = nodes.mapTo(hashSetOf()) { it.id }
        runtimeStates.keys.removeIf { it !in validIds }

        for (node in nodes) {
            if (node.id in activeNodeIds) continue

            runtimeStates[node.id]?.apply {
                triggered = false
                clickSatisfied = false
            }
            if (currentAwaitNodeId == node.id) {
                currentAwaitNodeId = null
            }
        }
    }

    private fun shouldAwait(node: RouteNode, runtime: NodeRuntime, player: LocalPlayer): Boolean {
        if (node.awaitClick && !runtime.clickSatisfied) {
            return true
        }
        if (node.awaitEtherwarpRaytrace && !passesEtherwarpRaytrace(node, player)) {
            return true
        }
        return false
    }

    private fun passesEtherwarpRaytrace(node: RouteNode, player: LocalPlayer): Boolean {
        val level = player.level()
        val eyePos = Vec3(node.x, node.y + 1.54, node.z)
        val target = Vec3(node.targetX, node.targetY, node.targetZ)
        val direction = target.subtract(eyePos)
        if (direction.lengthSqr() < 1.0e-6) return false

        val normalized = direction.normalize()
        val hit = level.clip(
            ClipContext(
                eyePos,
                eyePos.add(normalized.scale(RAYTRACE_RANGE)),
                ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE,
                player
            )
        )
        if (hit.type != HitResult.Type.BLOCK) return false

        val expectedBlock = BlockPos.containing(node.targetX, node.targetY - 1.0, node.targetZ)
        return hit.blockPos == expectedBlock
    }

    private fun performEtherwarp(player: LocalPlayer, node: RouteNode): Boolean {
        if (!isHoldingTeleportShovel(player)) return false

        val client = Minecraft.getInstance()
        val target = Vec3(node.targetX, node.targetY, node.targetZ)
        if (!canOccupy(player, target)) return false

        return EtherwarpController.performEtherwarpTo(
            client = client,
            target = target,
            lookOrigin = Vec3(node.x, node.y + 1.54, node.z),
            lightweightPackets = true,
            forceSneakPacket = true
        )
    }

    private fun raytraceEtherwarpTarget(player: LocalPlayer): Vec3? {
        val level = player.level()
        val start = player.eyePosition
        val look = player.getViewVector(1.0f)
        val end = start.add(look.scale(RAYTRACE_RANGE))

        val hit = level.clip(
            ClipContext(
                start,
                end,
                ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE,
                player
            )
        )
        if (hit.type != HitResult.Type.BLOCK) return null

        val blockPos = hit.blockPos
        val destination = Vec3(blockPos.x + 0.5, blockPos.y + 1.0, blockPos.z + 0.5)
        return if (canOccupy(player, destination)) destination else null
    }

    private fun canOccupy(player: LocalPlayer, destination: Vec3): Boolean {
        val offset = destination.subtract(player.position())
        return player.level().noCollision(player, player.boundingBox.move(offset))
    }

    private fun isHoldingTeleportShovel(player: LocalPlayer): Boolean {
        return player.mainHandItem.`is`(Items.DIAMOND_SHOVEL)
    }

    private fun isInsideSourceRing(node: RouteNode, playerPos: Vec3): Boolean {
        return isInsideRing(
            centerX = node.x,
            centerY = node.y,
            centerZ = node.z,
            radius = nodeRadius.value,
            playerPos = playerPos
        )
    }

    private fun isInsideTargetRing(node: RouteNode, playerPos: Vec3): Boolean {
        return isInsideRing(
            centerX = node.targetX,
            centerY = node.targetY,
            centerZ = node.targetZ,
            radius = targetRadius.value,
            playerPos = playerPos
        )
    }

    private fun isInsideRing(
        centerX: Double,
        centerY: Double,
        centerZ: Double,
        radius: Double,
        playerPos: Vec3
    ): Boolean {
        if (abs(playerPos.y - centerY) > MAX_VERTICAL_DELTA) return false

        val dx = playerPos.x - centerX
        val dz = playerPos.z - centerZ
        return dx * dx + dz * dz <= radius * radius
    }

    private fun distanceToNodeSqr(node: RouteNode, playerPos: Vec3): Double {
        val dx = playerPos.x - node.x
        val dy = playerPos.y - node.y
        val dz = playerPos.z - node.z
        return dx * dx + dy * dy + dz * dz
    }

    private fun distanceToSourceRingSqr(node: RouteNode, playerPos: Vec3): Double {
        return distanceToPointSqr(node.x, node.y, node.z, playerPos)
    }

    private fun distanceToTargetRingSqr(node: RouteNode, playerPos: Vec3): Double {
        return distanceToPointSqr(node.targetX, node.targetY, node.targetZ, playerPos)
    }

    private fun distanceToPointSqr(x: Double, y: Double, z: Double, playerPos: Vec3): Double {
        val dx = playerPos.x - x
        val dy = playerPos.y - y
        val dz = playerPos.z - z
        return dx * dx + dy * dy + dz * dz
    }

    private fun pollManualTrigger(client: Minecraft, active: Boolean): Boolean {
        val attackDown = active && client.options.keyAttack.isDown
        val useDown = active && client.options.keyUse.isDown
        val triggerDown = active &&
            triggerKey.key != GLFW.GLFW_KEY_UNKNOWN &&
            InputConstants.isKeyDown(client.window, triggerKey.key)

        val triggered = (attackDown && !lastAttackDown) ||
            (useDown && !lastUseDown) ||
            (triggerDown && !lastTriggerDown)

        lastAttackDown = attackDown
        lastUseDown = useDown
        lastTriggerDown = triggerDown

        return triggered
    }

    private fun ringSpec(
        centerX: Double,
        centerY: Double,
        centerZ: Double,
        radius: Double,
        thickness: Double,
        height: Double,
        argb: Int,
        fillAlpha: Float,
        outlineAlpha: Float
    ): WorldRingRenderer.RingSpec {
        val (red, green, blue) = rgb(argb)
        return WorldRingRenderer.RingSpec(
            centerX = centerX,
            centerY = centerY,
            centerZ = centerZ,
            radius = radius,
            thickness = thickness,
            height = height,
            red = red,
            green = green,
            blue = blue,
            fillAlpha = fillAlpha,
            outlineAlpha = outlineAlpha
        )
    }

    private fun rgb(argb: Int): Triple<Float, Float, Float> {
        return Triple(
            ((argb ushr 16) and 0xFF) / 255f,
            ((argb ushr 8) and 0xFF) / 255f,
            (argb and 0xFF) / 255f
        )
    }

    private fun displayDimension(dimension: String): String {
        return dimension.substringAfterLast(':', dimension)
    }

    private fun formatVec(x: Double, y: Double, z: Double): String {
        return "${formatNumber(x)}, ${formatNumber(y)}, ${formatNumber(z)}"
    }

    private fun formatNumber(value: Double): String {
        val rounded = kotlin.math.round(value * 100.0) / 100.0
        return if (rounded % 1.0 == 0.0) rounded.toInt().toString() else rounded.toString()
    }

    private fun findRoute(name: String): Route? {
        return config.routes.firstOrNull { it.name.equals(name.trim(), ignoreCase = true) }
    }

    private fun resetRuntime(clearInputs: Boolean = true, clearDimension: Boolean = true) {
        runtimeStates.clear()
        currentAwaitNodeId = null
        actionCooldownTicks = 0
        if (clearInputs) {
            lastAttackDown = false
            lastUseDown = false
            lastTriggerDown = false
        }
        if (clearDimension) {
            lastDimensionKey = null
        }
    }

    private fun load() {
        try {
            Files.createDirectories(configPath.parent)
            config = if (Files.exists(configPath)) {
                gson.fromJson(Files.readString(configPath), Config::class.java) ?: Config()
            } else {
                Config()
            }

            config.routes.forEach { route ->
                route.nodes.forEach { node ->
                    if (node.id.isBlank()) {
                        node.id = UUID.randomUUID().toString()
                    }
                }
            }
            save()
        } catch (throwable: Throwable) {
            LarpLog.warn("Failed to load simple autoroutes config from $configPath: ${throwable.message ?: throwable.javaClass.simpleName}")
            config = Config()
        }
    }

    private fun save() {
        try {
            Files.createDirectories(configPath.parent)
            Files.writeString(configPath, gson.toJson(config))
        } catch (throwable: Throwable) {
            LarpLog.warn("Failed to save simple autoroutes config to $configPath: ${throwable.message ?: throwable.javaClass.simpleName}")
        }
    }
}
