package me.mrai.larpclient.features.impl.dungeons.f7.p3.autop3

import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import me.mrai.larpclient.features.impl.dungeons.general.dungeonbreakernuker.DungeonbreakerNukerModule
import me.mrai.larpclient.features.impl.dungeons.general.velocitybuffer.VelocityBufferModule
import me.mrai.larpclient.features.impl.skyblock.general.blink.BlinkModule
import com.mojang.blaze3d.platform.InputConstants
import me.mrai.larpclient.mixin.ClientInputAccessor
import me.mrai.larpclient.mixin.MinecraftInvoker
import me.mrai.larpclient.mixin.MultiPlayerGameModeInvoker
import me.mrai.larpclient.module.BoolSetting
import me.mrai.larpclient.module.ComponentSetting
import me.mrai.larpclient.module.KeybindSetting
import me.mrai.larpclient.module.Module
import me.mrai.larpclient.module.ModuleCategory
import me.mrai.larpclient.module.ModuleConfigManager
import me.mrai.larpclient.module.SliderSetting
import me.mrai.larpclient.render.WorldBoxRenderer
import me.mrai.larpclient.render.WorldRingRenderer
import me.mrai.larpclient.util.LarpChat
import me.mrai.larpclient.util.LarpLog
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.Minecraft
import net.minecraft.client.player.ClientInput
import net.minecraft.core.BlockPos
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket
import net.minecraft.network.protocol.game.ServerboundUseItemPacket
import net.minecraft.world.InteractionHand
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.player.Input
import net.minecraft.world.level.Level
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec2
import net.minecraft.world.phys.Vec3
import java.nio.file.Files
import java.nio.file.Path
import java.util.LinkedList
import java.util.Locale
import java.util.Queue
import kotlin.io.path.exists
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.sin
import kotlin.math.sqrt

object AutoP3Module : Module(
    name = "Auto P3",
    description = "Floor 7 P3 ring editor with thin circular rings and dot commands.",
    category = ModuleCategory.DUNGEONS_F7_P3
) {
    enum class RingType(
        val wireName: String,
        val color: Triple<Float, Float, Float>
    ) {
        ALIGN("align", Triple(0.28f, 0.9f, 0.28f)),
        FAST_ALIGN("fastalign", Triple(0.16f, 0.68f, 0.16f)),
        STOP("stop", Triple(1.0f, 0.22f, 0.22f)),
        WALK("walk", Triple(0.16f, 0.92f, 0.92f)),
        JUMP("jump", Triple(1.0f, 0.58f, 0.18f)),
        BONZO("bonzo", Triple(0.32f, 0.6f, 1.0f)),
        FAST_BONZO("fastbonzo", Triple(1.0f, 0.5f, 0.84f)),
        EDGE("edge", Triple(0.2f, 0.2f, 0.2f)),
        MOVEMENT("movement", Triple(1.0f, 1.0f, 1.0f)),
        LOOK("look", Triple(0.22f, 0.9f, 0.22f)),
        BOOM("boom", Triple(1.0f, 0.88f, 0.2f)),
        LEAP("leap", Triple(1.0f, 0.52f, 0.84f)),
        USE("use", Triple(0.6f, 0.6f, 0.6f)),
        CHAT("chat", Triple(1.0f, 1.0f, 0.2f)),
        COMMAND("command", Triple(0.4f, 0.4f, 0.4f)),
        BLINK("blink", Triple(1.0f, 0.4f, 0.82f)),
        DUNGEONBREAKER("dungeonbreaker", Triple(1.0f, 0.18f, 0.18f));

        companion object {
            fun byName(name: String): RingType? = entries.firstOrNull {
                it.wireName.equals(name, ignoreCase = true) || (it == DUNGEONBREAKER && name.equals("dbreaker", ignoreCase = true))
            }
        }
    }

    data class P3Ring(
        var id: Int,
        var type: String,
        var x: Double,
        var y: Double,
        var z: Double,
        var radius: Double,
        var args: MutableMap<String, String> = linkedMapOf()
    ) {
        fun typeEnum(): RingType? = RingType.byName(type)
        fun center(): Vec3 = Vec3(x, y, z)
    }

    data class RecordedRoutePoint(
        var x: Double,
        var y: Double,
        var z: Double,
        var yaw: Float,
        var pitch: Float
    )

    data class RecordedRoute(
        var name: String,
        var points: MutableList<RecordedRoutePoint> = mutableListOf()
    )

    data class LandingZone(
        var id: Int,
        var minX: Int,
        var maxX: Int,
        var y: Int,
        var minZ: Int,
        var maxZ: Int
    ) {
        fun topSurfaceY(): Double = y + 1.0

        fun center(): Vec3 {
            return Vec3(
                (minX + maxX + 1) / 2.0,
                topSurfaceY(),
                (minZ + maxZ + 1) / 2.0
            )
        }

        fun worldBox(): AABB {
            return AABB(
                minX.toDouble(),
                y.toDouble(),
                minZ.toDouble(),
                maxX + 1.0,
                y + 1.0,
                maxZ + 1.0
            )
        }

        fun contains(x: Double, z: Double, margin: Double = 0.0): Boolean {
            return x in (minX + margin)..(maxX + 1.0 - margin) &&
                z in (minZ + margin)..(maxZ + 1.0 - margin)
        }

        fun clampX(x: Double, margin: Double = 0.0): Double {
            return x.coerceIn(minX + margin, maxX + 1.0 - margin)
        }

        fun clampZ(z: Double, margin: Double = 0.0): Double {
            return z.coerceIn(minZ + margin, maxZ + 1.0 - margin)
        }
    }

    private data class ActiveRecording(
        val name: String,
        val points: MutableList<RecordedRoutePoint> = mutableListOf()
    )

    private data class LandingZoneSelection(
        var firstCorner: BlockPos? = null
    )

    private data class TrajectoryPlan(
        val firstMove: Vec2,
        val path: List<Vec3>,
        val predictedLanding: Vec3?,
        val landsInZone: Boolean,
        val score: Double
    )

    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val ringRadius = SliderSetting("Ring Radius", 0.7, 0.25, 3.0, 0.05)
    private val ringThickness = SliderSetting("Ring Thickness", 0.12, 0.03, 0.5, 0.01)
    private val ringHeight = SliderSetting("Ring Height", 0.04, 0.01, 0.25, 0.01)
    private val fillAlpha = SliderSetting("Ring Fill Alpha", 42.0, 0.0, 255.0, 1.0)
    private val outlineAlpha = SliderSetting("Ring Outline Alpha", 215.0, 0.0, 255.0, 1.0)
    private val feedback = BoolSetting("Feedback", true)
    private val recordKey = KeybindSetting("Route Record Key")
    private val routePrefix = ComponentSetting("Route Prefix", "p3")

    private val baseDir: Path = FabricLoader.getInstance().configDir.resolve("larpclient/dungeons/f7/p3")
    private val ringsFile: Path = baseDir.resolve("rings.json")
    private val landingZonesFile: Path = baseDir.resolve("landing-zones.json")
    private val routesDir: Path = baseDir.resolve("routes")
    private val routeType = object : TypeToken<MutableList<P3Ring>>() {}.type
    private val landingZoneType = object : TypeToken<MutableList<LandingZone>>() {}.type
    private const val SINGLE_BLOCK_ALIGN_RADIUS = 0.7071067811865476
    private const val GRID_ALIGNMENT_EPSILON = 1.0E-3
    private const val INTERNAL_ALIGN_CENTER_KEY = "_aligncenter"
    private const val ALIGN_CENTER_EXACT = "exact"
    private const val EDGE_ZONE_SAFE_MARGIN = 0.18
    private const val EDGE_CENTER_TARGET_MARGIN = 0.35
    private const val EDGE_PROBE_FORWARD = 0.38
    private const val EDGE_PROBE_LATERAL = 0.23
    private const val PLAYER_AIR_ACCELERATION = 0.02
    private const val PLAYER_AIR_DRAG = 0.91
    private const val PLAYER_VERTICAL_DRAG = 0.98
    private const val EDGE_MAX_SIM_TICKS = 80
    private const val EDGE_PREVIEW_FALL_MARGIN = 6.0
    private const val EDGE_PRE_LANDING_SHIFT_HEIGHT = 1.25
    private const val EDGE_GROUND_SETTLE_TICKS = 4
    private const val EDGE_GROUND_STOP_SPEED = 0.0036
    private const val EDGE_GROUND_CENTER_TOLERANCE = 0.09

    private val rings = mutableListOf<P3Ring>()
    private val landingZones = mutableListOf<LandingZone>()
    private var nextRingId = 1
    private var nextLandingZoneId = 1
    private var recording: ActiveRecording? = null
    private var recordKeyWasPressed = false
    private val insideRingIds = linkedSetOf<Int>()
    private val deferredRingIds = linkedSetOf<Int>()
    private var dungeonbreakerLatched = false
    private var pendingJumpFromRing = false
    private var activeController: ActiveController? = null
    private var landingZoneSelection: LandingZoneSelection? = null
    private var landingZoneUseWasPressed = false
    private val airMoveOptions = listOf(
        Vec2.ZERO,
        Vec2(0f, 1f),
        Vec2(0f, -1f),
        Vec2(1f, 0f),
        Vec2(-1f, 0f),
        Vec2(1f, 1f),
        Vec2(-1f, 1f),
        Vec2(1f, -1f),
        Vec2(-1f, -1f)
    )

    init {
        settings += listOf(ringRadius, ringThickness, ringHeight, fillAlpha, outlineAlpha, feedback, recordKey, routePrefix)
        load()
    }

    override fun onDisable() {
        insideRingIds.clear()
        deferredRingIds.clear()
        pendingJumpFromRing = false
        activeController = null
        releaseDungeonbreakerLatch()
    }

    fun onWorldChange() {
        insideRingIds.clear()
        deferredRingIds.clear()
        pendingJumpFromRing = false
        activeController = null
        landingZoneSelection = null
        releaseDungeonbreakerLatch()
    }

    override fun onTick() {
        handleRecordKey()
        if (!enabled) return

        val player = Minecraft.getInstance().player ?: return

        val currentInside = linkedSetOf<Int>()
        val newlyEntered = mutableListOf<P3Ring>()
        val playerPos = player.position()

        for (ring in rings) {
            if (isInsideRing(ring, playerPos)) {
                currentInside += ring.id
                if (!insideRingIds.contains(ring.id)) {
                    newlyEntered += ring
                }
            }
        }

        handleEnteredRings(newlyEntered, player)

        insideRingIds.clear()
        insideRingIds += currentInside

        val currentRecording = recording
        if (currentRecording != null) {
            currentRecording.points += RecordedRoutePoint(
                x = playerPos.x,
                y = playerPos.y,
                z = playerPos.z,
                yaw = player.yRot,
                pitch = player.xRot
            )
        }

        if (insideRingIds.none { ringById(it)?.typeEnum() == RingType.DUNGEONBREAKER }) {
            releaseDungeonbreakerLatch()
        }

        val controller = activeController ?: return
        if (controller.tick(player)) {
            val completedController = controller
            activeController = null
            if (completedController is AlignController) {
                flushDeferredRings(player)
            }
        }
    }

    fun onClientTick(client: Minecraft) {
        handleLandingZoneSelection(client)
    }

    fun render(context: LevelRenderContext) {
        renderLandingZones(context)
        renderLandingZoneSelection(context)
        renderEdgeTrajectory(context)

        if (!enabled || rings.isEmpty()) return

        val specs = rings.mapNotNull { ring ->
            val type = ring.typeEnum() ?: return@mapNotNull null
            WorldRingRenderer.RingSpec(
                centerX = ring.x,
                centerY = ring.y,
                centerZ = ring.z,
                radius = ring.radius,
                thickness = ringThickness.value,
                height = ringHeight.value,
                red = type.color.first,
                green = type.color.second,
                blue = type.color.third,
                fillAlpha = (fillAlpha.value / 255.0).toFloat(),
                outlineAlpha = (outlineAlpha.value / 255.0).toFloat()
            )
        }
        WorldRingRenderer.render(context, specs)
    }

    fun supportedTypes(): List<String> = RingType.entries.map { it.wireName }

    fun landingZoneIds(): List<String> = landingZones.map { it.id.toString() }

    fun beginLandingZoneSelection(): Boolean {
        if (Minecraft.getInstance().player == null) return false
        landingZoneSelection = LandingZoneSelection()
        landingZoneUseWasPressed = Minecraft.getInstance().options.keyUse.isDown
        notify("Landing zone selection started. Right click the first block, then the second block on the same Y.")
        return true
    }

    fun addRingAtPlayer(typeName: String, args: Map<String, String>): Boolean {
        val type = RingType.byName(typeName) ?: return false
        val player = Minecraft.getInstance().player ?: return false
        val edgeZoneId = parseLandingZoneId(args["edge"])
        if (type == RingType.WALK && args.containsKey("edge")) {
            val edgeArg = args["edge"]
            if (edgeArg != null && edgeArg != "true" && edgeZoneId == null) {
                return false
            }
            if (edgeZoneId != null && landingZoneById(edgeZoneId) == null) {
                return false
            }
        }
        val radius = args["radius"]?.toDoubleOrNull() ?: ringRadius.value
        val exactPosition = args.containsKey("exact")
        val exactLook = args.containsKey("exactlook")
        val center = defaultRingCenter(type, player.position(), exactPosition, radius)
        val storedArgs = LinkedHashMap(args).apply {
            remove("exact")
            remove("exactlook")
        }
        if (type in setOf(RingType.ALIGN, RingType.FAST_ALIGN) && (exactPosition || args.containsKey("x") || args.containsKey("z"))) {
            storedArgs[INTERNAL_ALIGN_CENTER_KEY] = ALIGN_CENTER_EXACT
        }
        if (type in setOf(RingType.BONZO, RingType.FAST_BONZO)) {
            storedArgs.putIfAbsent("yaw", player.yRot.toString())
            storedArgs.putIfAbsent("pitch", player.xRot.toString())
            storedArgs.putIfAbsent("velbuffer", (!exactPosition).toString())
        } else if (type in setOf(RingType.LOOK, RingType.WALK, RingType.USE)) {
            storedArgs.putIfAbsent("yaw", defaultStoredYaw(player.yRot, exactLook).toString())
            storedArgs.putIfAbsent("pitch", 0f.toString())
        }

        val ring = P3Ring(
            id = nextRingId++,
            type = type.wireName,
            x = args["x"]?.toDoubleOrNull() ?: center.x,
            y = args["y"]?.toDoubleOrNull() ?: center.y,
            z = args["z"]?.toDoubleOrNull() ?: center.z,
            radius = radius,
            args = storedArgs
        )

        rings += ring
        save()
        notify("Added ${type.wireName} ring #${ring.id}.")
        return true
    }

    fun clearRings() {
        rings.clear()
        nextRingId = 1
        save()
        notify("Cleared all P3 rings.")
    }

    fun listRings(): List<String> {
        return rings.sortedBy { it.id }.map { ring ->
            val visibleArgs = ring.args.filterKeys { !it.startsWith("_") }
            val suffix = if (visibleArgs.isEmpty()) "" else " ${visibleArgs.entries.joinToString(" ") { formatRingArg(it.key, it.value) }}"
            "#${ring.id} ${ring.type} @ ${"%.1f".format(ring.x)}, ${"%.1f".format(ring.y)}, ${"%.1f".format(ring.z)} r=${"%.2f".format(ring.radius)}$suffix"
        }
    }

    fun removeNearestRing(): Boolean {
        val player = Minecraft.getInstance().player ?: return false
        val nearest = rings.minByOrNull { it.center().distanceToSqr(player.position()) } ?: return false
        rings.removeIf { it.id == nearest.id }
        save()
        notify("Removed ring #${nearest.id}.")
        return true
    }

    fun removeRing(id: Int): Boolean {
        val removed = rings.removeIf { it.id == id }
        if (removed) {
            save()
            notify("Removed ring #$id.")
        }
        return removed
    }

    fun beginRouteRecording(name: String): Boolean {
        if (recording != null) return false
        recording = ActiveRecording(name.trim())
        notify("Started P3 route recording $name.")
        return true
    }

    fun stopRouteRecording(): RecordedRoute? {
        val active = recording ?: return null
        recording = null
        if (active.points.isEmpty()) {
            notify("Discarded empty P3 route ${active.name}.")
            return null
        }

        val route = RecordedRoute(active.name, active.points)
        saveRoute(route)
        notify("Saved P3 route ${route.name}.json with ${route.points.size} points.")
        return route
    }

    fun routeNames(): List<String> {
        if (!routesDir.exists()) return emptyList()
        return Files.list(routesDir).use { stream ->
            stream
                .filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(".json") }
                .map { it.fileName.toString().removeSuffix(".json") }
                .sorted()
                .toList()
        }
    }

    fun deleteRoute(name: String): Boolean {
        val path = routePath(name)
        if (!Files.exists(path)) return false
        Files.delete(path)
        notify("Deleted P3 route ${normaliseRouteName(name)}.json.")
        return true
    }

    fun commandExamples(): List<String> = listOf(
        ".p3 add blink route=bridge packets=10",
        ".p3 add dungeonbreaker",
        ".p3 add command command=pc_mid",
        ".p3 add look yaw=90 pitch=12",
        ".p3 add look exactlook",
        ".p3 add align exact",
        ".p3 add walk edge[1]",
        ".p3 route record start bridge",
        ".p3 route record stop",
        ".p3 remove nearest"
    )

    fun onInputTick(clientInput: ClientInput) {
        val client = Minecraft.getInstance()
        val player = client.player ?: return
        val accessor = clientInput as ClientInputAccessor
        val current = accessor.`larpclient$getKeyPresses`()

        if (hasManualMovement(current)) {
            pendingJumpFromRing = false
            if (activeController != null) {
                activeController = null
                deferredRingIds.clear()
                notify("Cancelled P3 movement.")
            }
            return
        }

        if (pendingJumpFromRing && enabled && player.onGround() && !client.options.keyJump.isDown) {
            pendingJumpFromRing = false
            accessor.`larpclient$setKeyPresses`(
                Input(
                    current.forward(),
                    current.backward(),
                    current.left(),
                    current.right(),
                    true,
                    current.shift(),
                    current.sprint()
                )
            )
            return
        }

        val controller = activeController ?: return

        val override = controller.input(player) ?: return
        accessor.`larpclient$setKeyPresses`(override.input)
        accessor.`larpclient$setMoveVector`(override.moveVector)
        player.setYRot(override.yaw)
        player.setXRot(override.pitch)
        player.setYHeadRot(override.yaw)
        player.setYBodyRot(override.yaw)
    }

    private fun handleLandingZoneSelection(client: Minecraft) {
        val selection = landingZoneSelection ?: return
        if (client.screen != null) {
            landingZoneUseWasPressed = client.options.keyUse.isDown
            return
        }

        val usePressed = client.options.keyUse.isDown
        if (usePressed && !landingZoneUseWasPressed) {
            val hit = client.hitResult as? BlockHitResult
            if (hit?.type == HitResult.Type.BLOCK) {
                handleLandingZoneClick(hit.blockPos)
            }
        }
        landingZoneUseWasPressed = usePressed
    }

    private fun handleLandingZoneClick(blockPos: BlockPos) {
        val selection = landingZoneSelection ?: return
        val firstCorner = selection.firstCorner
        if (firstCorner == null) {
            selection.firstCorner = blockPos.immutable()
            notify("Landing zone first corner set at ${blockPos.x}, ${blockPos.y}, ${blockPos.z}.")
            return
        }

        if (blockPos.y != firstCorner.y) {
            notify("Landing zone corners must be on the same Y.")
            return
        }

        val zone = LandingZone(
            id = nextLandingZoneId++,
            minX = minOf(firstCorner.x, blockPos.x),
            maxX = maxOf(firstCorner.x, blockPos.x),
            y = blockPos.y,
            minZ = minOf(firstCorner.z, blockPos.z),
            maxZ = maxOf(firstCorner.z, blockPos.z)
        )
        landingZones += zone
        saveLandingZones()
        landingZoneSelection = null
        notify(
            "Saved landing zone #${zone.id} from ${zone.minX}, ${zone.y}, ${zone.minZ} to ${zone.maxX}, ${zone.y}, ${zone.maxZ}."
        )
    }

    private fun renderLandingZones(context: LevelRenderContext) {
        if (landingZones.isEmpty()) return
        val style = WorldBoxRenderer.BoxStyle(
            fillRed = 0.15f,
            fillGreen = 0.72f,
            fillBlue = 0.2f,
            fillAlpha = 0.10f,
            outlineRed = 0.2f,
            outlineGreen = 1.0f,
            outlineBlue = 0.28f,
            outlineAlpha = 0.95f,
            outlineThickness = 0.03f
        )
        val boxes = landingZones.map { it.worldBox().inflate(0.002) to style }
        WorldBoxRenderer.render(context, boxes)
    }

    private fun renderLandingZoneSelection(context: LevelRenderContext) {
        val preview = landingZoneSelectionPreview() ?: return
        val style = WorldBoxRenderer.BoxStyle(
            fillRed = 0.45f,
            fillGreen = 0.95f,
            fillBlue = 0.3f,
            fillAlpha = 0.12f,
            outlineRed = 0.55f,
            outlineGreen = 1.0f,
            outlineBlue = 0.38f,
            outlineAlpha = 0.98f,
            outlineThickness = 0.03f
        )
        WorldBoxRenderer.render(context, listOf(preview.inflate(0.002) to style))
    }

    private fun renderEdgeTrajectory(context: LevelRenderContext) = Unit

    private fun landingZoneSelectionPreview(): AABB? {
        val selection = landingZoneSelection ?: return null
        val firstCorner = selection.firstCorner ?: return null
        val hit = Minecraft.getInstance().hitResult as? BlockHitResult ?: return null
        if (hit.type != HitResult.Type.BLOCK || hit.blockPos.y != firstCorner.y) {
            return AABB(firstCorner)
        }
        return AABB(
            minOf(firstCorner.x, hit.blockPos.x).toDouble(),
            firstCorner.y.toDouble(),
            minOf(firstCorner.z, hit.blockPos.z).toDouble(),
            maxOf(firstCorner.x, hit.blockPos.x) + 1.0,
            firstCorner.y + 1.0,
            maxOf(firstCorner.z, hit.blockPos.z) + 1.0
        )
    }

    private fun handleRecordKey() {
        val key = recordKey.key
        if (key <= 0) return

        val client = Minecraft.getInstance()
        val pressed = client.screen == null && InputConstants.isKeyDown(client.window, key)
        if (pressed && !recordKeyWasPressed) {
            if (recording == null) {
                beginRouteRecording(nextRouteName())
            } else {
                stopRouteRecording()
            }
        }
        recordKeyWasPressed = pressed
    }

    private fun handleEnteredRings(
        newRings: List<P3Ring>,
        player: net.minecraft.client.player.LocalPlayer
    ) {
        if (newRings.isEmpty()) return

        val activeAlign = activeController as? AlignController
        if (activeAlign != null) {
            newRings
                .filterNot { it.id == activeAlign.ringId }
                .forEach { deferredRingIds += it.id }
            return
        }

        val alignRing = newRings.firstOrNull {
            when (it.typeEnum()) {
                RingType.ALIGN, RingType.FAST_ALIGN -> true
                else -> false
            }
        }

        if (alignRing != null) {
            activeController = AlignController.create(
                ringId = alignRing.id,
                ringType = alignRing.typeEnum() ?: return,
                target = resolveAlignTarget(alignRing)
            )
            newRings
                .filterNot { it.id == alignRing.id }
                .forEach { deferredRingIds += it.id }
            return
        }

        newRings.forEach(::triggerRing)
    }

    private fun flushDeferredRings(player: net.minecraft.client.player.LocalPlayer) {
        if (deferredRingIds.isEmpty()) return
        val deferred = deferredRingIds
            .mapNotNull(::ringById)
            .filter { isInsideRing(it, player.position()) }
        deferredRingIds.clear()
        handleEnteredRings(deferred, player)
    }

    private fun triggerRing(ring: P3Ring) {
        when (ring.typeEnum()) {
            RingType.CHAT -> {
                val message = ring.args["message"] ?: ring.args["text"] ?: return
                Minecraft.getInstance().player?.connection?.sendChat(message)
            }

            RingType.COMMAND -> {
                val command = ring.args["command"] ?: return
                Minecraft.getInstance().player?.connection?.sendCommand(command.removePrefix("/"))
            }

            RingType.LOOK -> {
                val player = Minecraft.getInstance().player ?: return
                val yaw = ring.args["yaw"]?.toFloatOrNull() ?: player.yRot
                val pitch = ring.args["pitch"]?.toFloatOrNull() ?: player.xRot
                player.yRot = yaw
                player.xRot = pitch
            }

            RingType.JUMP -> {
                pendingJumpFromRing = true
                notify("Jumping")
            }

            RingType.WALK -> {
                val player = Minecraft.getInstance().player ?: return
                val yaw = ring.args["yaw"]?.toFloatOrNull() ?: player.yRot
                val landingZone = parseLandingZoneId(ring.args["edge"])?.let(::landingZoneById)
                activeController = if (ring.args.containsKey("edge")) {
                    EdgeJumpController(ring.id, yaw, landingZone)
                } else {
                    WalkController(ring.id, yaw, sprint = true, sneak = false)
                }
            }

            RingType.EDGE -> {
                val player = Minecraft.getInstance().player ?: return
                val yaw = ring.args["yaw"]?.toFloatOrNull() ?: player.yRot
                activeController = WalkController(ring.id, yaw, sprint = false, sneak = true)
            }

            RingType.STOP -> {
                activeController = StopController(ring.id)
            }

            RingType.ALIGN, RingType.FAST_ALIGN -> {
                activeController = AlignController.create(
                    ringId = ring.id,
                    ringType = ring.typeEnum() ?: return,
                    target = resolveAlignTarget(ring)
                )
            }

            RingType.MOVEMENT -> {
                val routeName = ring.args["route"] ?: return
                val route = loadRoute(routeName) ?: return
                if (route.points.isNotEmpty()) {
                    activeController = RouteController(ring.id, route)
                }
            }

            RingType.DUNGEONBREAKER -> {
                if (!DungeonbreakerNukerModule.enabled) {
                    DungeonbreakerNukerModule.enabled = true
                    ModuleConfigManager.save()
                    dungeonbreakerLatched = true
                    notify("Enabled Dungeonbreaker from ring #${ring.id}.")
                }
            }

            RingType.BLINK -> {
                val route = ring.args["route"] ?: return
                val packets = ring.args["packets"]?.toIntOrNull()?.coerceAtLeast(1) ?: 17
                val ok = BlinkModule.addRingFromCommand(route, packets)
                if (ok) {
                    notify("Queued blink ring from P3 route $route.")
                } else {
                    notify("Failed to queue blink ring for $route.")
                }
            }

            RingType.BONZO -> useNamedHotbarItem(listOf("Bonzo", "Jerry-chine"), ring, enableVelocityBuffer = ring.args["velbuffer"]?.toBoolean() != false)
            RingType.FAST_BONZO -> useNamedHotbarItem(listOf("Bonzo", "Jerry-chine"), ring, enableVelocityBuffer = ring.args["velbuffer"]?.toBoolean() != false)
            RingType.LEAP -> useNamedHotbarItem(listOf("Spirit Leap"), ring)
            RingType.BOOM -> useNamedHotbarItem(listOf("Superboom", "TNT"), ring)
            RingType.USE -> {
                val needle = ring.args["item"] ?: currentHeldName() ?: return
                useNamedHotbarItem(listOf(needle), ring)
            }

            null -> {}
        }
    }

    private fun releaseDungeonbreakerLatch() {
        if (!dungeonbreakerLatched) return
        if (DungeonbreakerNukerModule.enabled) {
            DungeonbreakerNukerModule.enabled = false
            ModuleConfigManager.save()
        }
        dungeonbreakerLatched = false
    }

    private fun isInsideRing(ring: P3Ring, playerPos: Vec3): Boolean {
        val dx = playerPos.x - ring.x
        val dz = playerPos.z - ring.z
        val vertical = abs(playerPos.y - ring.y)
        return dx * dx + dz * dz <= ring.radius * ring.radius && vertical <= 2.5
    }

    private fun ringById(id: Int): P3Ring? = rings.firstOrNull { it.id == id }

    private fun landingZoneById(id: Int): LandingZone? = landingZones.firstOrNull { it.id == id }

    private fun parseLandingZoneId(raw: String?): Int? {
        val value = raw?.trim()?.takeUnless { it.equals("true", ignoreCase = true) } ?: return null
        return value.toIntOrNull()
    }

    private fun formatRingArg(key: String, value: String): String {
        return if (key == "edge" && !value.equals("true", ignoreCase = true)) {
            "edge[$value]"
        } else if (value.equals("true", ignoreCase = true)) {
            key
        } else {
            "$key=$value"
        }
    }

    private fun hasManualMovement(input: Input): Boolean {
        return input.forward() || input.backward() || input.left() || input.right() || input.jump() || input.shift()
    }

    private fun useNamedHotbarItem(needles: List<String>, ring: P3Ring, enableVelocityBuffer: Boolean = false) {
        val client = Minecraft.getInstance()
        val player = client.player ?: return
        val connection = client.connection ?: return
        val gameMode = client.gameMode ?: return
        val slot = findHotbarSlot(needles) ?: return
        val oldSlot = player.inventory.selectedSlot
        val yaw = ring.args["yaw"]?.toFloatOrNull() ?: player.yRot
        val pitch = ring.args["pitch"]?.toFloatOrNull() ?: player.xRot

        if (enableVelocityBuffer && !VelocityBufferModule.enabled) {
            client.execute {
                if (!VelocityBufferModule.enabled) {
                    VelocityBufferModule.enabled = true
                }
            }
        }

        if (oldSlot != slot) {
            player.inventory.selectedSlot = slot
            connection.send(ServerboundSetCarriedItemPacket(slot))
        }

        val oldYaw = player.yRot
        val oldPitch = player.xRot
        player.setYRot(yaw)
        player.setXRot(pitch)

        if (client.hasSingleplayerServer() || client.isLocalServer) {
            (client as MinecraftInvoker).callStartUseItem()
            player.setYRot(oldYaw)
            player.setXRot(oldPitch)
        } else {
            connection.send(ServerboundMovePlayerPacket.Rot(yaw, pitch, player.onGround(), player.horizontalCollision))
            val level = client.level ?: return
            (gameMode as MultiPlayerGameModeInvoker).callStartPrediction(level) { sequence ->
                ServerboundUseItemPacket(InteractionHand.MAIN_HAND, sequence, yaw, pitch)
            }
            player.setYRot(oldYaw)
            player.setXRot(oldPitch)
            connection.send(ServerboundMovePlayerPacket.Rot(oldYaw, oldPitch, player.onGround(), player.horizontalCollision))
        }

        if (oldSlot != slot) {
            player.inventory.selectedSlot = oldSlot
            connection.send(ServerboundSetCarriedItemPacket(oldSlot))
        }
    }

    private fun findHotbarSlot(needles: List<String>): Int? {
        val player = Minecraft.getInstance().player ?: return null
        for (slot in 0..8) {
            val stack = player.inventory.getItem(slot)
            if (stack.isEmpty) continue
            val name = stack.hoverName.string
            if (needles.any { name.contains(it, ignoreCase = true) }) {
                return slot
            }
        }
        return null
    }

    private fun currentHeldName(): String? {
        val player = Minecraft.getInstance().player ?: return null
        val stack = player.mainHandItem
        return if (stack.isEmpty) null else stack.hoverName.string
    }

    private fun loadRoute(name: String): RecordedRoute? {
        val path = routePath(name)
        if (!Files.exists(path)) return null
        return runCatching {
            Files.newBufferedReader(path).use { reader ->
                gson.fromJson(reader, RecordedRoute::class.java)
            }
        }.getOrElse {
            LarpLog.warn("Failed to load P3 route '$name' from $path: ${it.message ?: it.javaClass.simpleName}")
            null
        }
    }

    private fun load() {
        try {
            Files.createDirectories(baseDir)
            Files.createDirectories(routesDir)
            if (Files.exists(ringsFile)) {
                Files.newBufferedReader(ringsFile).use { reader ->
                    val loaded: MutableList<P3Ring> = gson.fromJson(reader, routeType) ?: mutableListOf()
                    rings.clear()
                    rings += loaded
                    nextRingId = (rings.maxOfOrNull { it.id } ?: 0) + 1
                }
            } else {
                save()
            }

            if (Files.exists(landingZonesFile)) {
                Files.newBufferedReader(landingZonesFile).use { reader ->
                    val loaded: MutableList<LandingZone> = gson.fromJson(reader, landingZoneType) ?: mutableListOf()
                    landingZones.clear()
                    landingZones += loaded
                    nextLandingZoneId = (landingZones.maxOfOrNull { it.id } ?: 0) + 1
                }
            } else {
                saveLandingZones()
            }
        } catch (throwable: Throwable) {
            LarpLog.warn("Failed to load P3 ring config from $ringsFile: ${throwable.message ?: throwable.javaClass.simpleName}")
        }
    }

    private fun save() {
        try {
            Files.createDirectories(baseDir)
            Files.newBufferedWriter(ringsFile).use { writer ->
                gson.toJson(rings, writer)
            }
        } catch (throwable: Throwable) {
            LarpLog.warn("Failed to save P3 ring config to $ringsFile: ${throwable.message ?: throwable.javaClass.simpleName}")
        }
    }

    private fun saveLandingZones() {
        try {
            Files.createDirectories(baseDir)
            Files.newBufferedWriter(landingZonesFile).use { writer ->
                gson.toJson(landingZones, writer)
            }
        } catch (throwable: Throwable) {
            LarpLog.warn("Failed to save landing zones to $landingZonesFile: ${throwable.message ?: throwable.javaClass.simpleName}")
        }
    }

    private fun saveRoute(route: RecordedRoute) {
        try {
            Files.createDirectories(routesDir)
            Files.newBufferedWriter(routePath(route.name)).use { writer ->
                gson.toJson(route, writer)
            }
        } catch (throwable: Throwable) {
            LarpLog.warn("Failed to save P3 route '${route.name}' to $routesDir: ${throwable.message ?: throwable.javaClass.simpleName}")
        }
    }

    private fun routePath(name: String): Path = routesDir.resolve("${normaliseRouteName(name)}.json")

    private fun normaliseRouteName(name: String): String {
        val cleaned = name.trim().removeSuffix(".json").replace(Regex("[^A-Za-z0-9._-]"), "-")
        return cleaned.ifBlank { "route" }
    }

    private fun nextRouteName(): String {
        val prefix = normalizedPrefix()
        val pattern = Regex("^${Regex.escape(prefix)}-(\\d+)$")
        val next = routeNames().asSequence()
            .mapNotNull { name -> pattern.matchEntire(name)?.groupValues?.get(1)?.toIntOrNull() }
            .maxOrNull()
            ?.plus(1)
            ?: 1
        return "$prefix-$next"
    }

    private fun normalizedPrefix(): String {
        val raw = routePrefix.text.trim()
        val sanitized = raw.ifBlank { "p3" }
            .lowercase()
            .map { ch ->
                when {
                    ch.isLetterOrDigit() -> ch
                    ch == '-' || ch == '_' -> ch
                    else -> '-'
                }
            }
            .joinToString("")
            .replace(Regex("-+"), "-")
            .trim('-')

        val value = sanitized.ifBlank { "p3" }
        if (routePrefix.text != value) {
            routePrefix.text = value
        }
        return value
    }

    private fun notify(message: String) {
        if (feedback.value) {
            LarpChat.send(message)
        }
    }

    /**
     * RSA copies a terminal-ready line when a real two-step align starts (see AlignRing.run).
     * We place the equivalent [.p3 add …] command on the system clipboard.
     */
    private fun copyAlignRingToClipboard(ringId: Int, target: Vec3, ringType: RingType) {
        val ring = ringById(ringId) ?: return
        val cmd = buildString {
            append(".p3 add ")
            append(ringType.wireName)
            append(" x=").append(formatP3Coord(target.x))
            append(" y=").append(formatP3Coord(target.y))
            append(" z=").append(formatP3Coord(target.z))
            append(" radius=").append(formatP3Coord(ring.radius))
            append(" exact")
        }
        runCatching {
            Minecraft.getInstance().keyboardHandler.setClipboard(cmd)
        }.onFailure {
            LarpLog.warn("P3 align clipboard failed: ${it.message ?: it.javaClass.simpleName}")
        }
        notify("Copied align ring to clipboard.")
    }

    private fun formatP3Coord(value: Double): String {
        return String.format(Locale.US, "%.6f", value).trimEnd('0').trimEnd('.').ifEmpty { "0" }
    }

    private fun snappedRingCenter(position: Vec3, exact: Boolean): Vec3 {
        if (exact) return position
        return Vec3(
            floor(position.x) + 0.5,
            floor(position.y),
            floor(position.z) + 0.5
        )
    }

    private fun defaultRingCenter(type: RingType, position: Vec3, exact: Boolean, radius: Double): Vec3 {
        if (exact) return position
        if (type == RingType.BONZO || type == RingType.FAST_BONZO) {
            val hit = Minecraft.getInstance().hitResult
            if (hit is BlockHitResult && hit.type == HitResult.Type.BLOCK) {
                val block = hit.blockPos
                return Vec3(block.x + 0.5, block.y.toDouble(), block.z + 0.5)
            }
        }
        if (type == RingType.ALIGN || type == RingType.FAST_ALIGN) {
            return defaultAlignRingCenter(position, radius)
        }
        return snappedRingCenter(position, exact = false)
    }

    private fun defaultAlignRingCenter(position: Vec3, radius: Double): Vec3 {
        return if (shouldUseIntersectionAlignTarget(radius)) {
            Vec3(round(position.x), floor(position.y), round(position.z))
        } else {
            snappedRingCenter(position, exact = false)
        }
    }

    private fun resolveAlignTarget(ring: P3Ring): Vec3 {
        if (ring.args[INTERNAL_ALIGN_CENTER_KEY] == ALIGN_CENTER_EXACT) {
            return ring.center()
        }
        if (!shouldUseIntersectionAlignTarget(ring.radius)) {
            return ring.center()
        }
        return Vec3(
            resolveIntersectionAlignCoordinate(ring.x),
            ring.y,
            resolveIntersectionAlignCoordinate(ring.z)
        )
    }

    private fun resolveIntersectionAlignCoordinate(coordinate: Double): Double {
        val nearestHalfStep = round(coordinate * 2.0) / 2.0
        return if (abs(coordinate - nearestHalfStep) <= GRID_ALIGNMENT_EPSILON) {
            round(coordinate)
        } else {
            coordinate
        }
    }

    private fun shouldUseIntersectionAlignTarget(radius: Double): Boolean {
        val footprint = round(radius / SINGLE_BLOCK_ALIGN_RADIUS).toInt().coerceAtLeast(1)
        return footprint % 2 == 0
    }

    private fun defaultStoredYaw(yaw: Float, exactLook: Boolean): Float {
        if (exactLook) return yaw
        val normalized = ((yaw % 360f) + 360f) % 360f
        val snapped = round(normalized / 90f) * 90f
        return if (snapped >= 360f) 0f else snapped
    }

    private data class InputOverride(
        val input: Input,
        val moveVector: Vec2,
        val yaw: Float,
        val pitch: Float
    )

    private fun inputOverride(
        moveVector: Vec2,
        yaw: Float,
        pitch: Float,
        jump: Boolean = false,
        sneak: Boolean = false,
        sprint: Boolean = false
    ): InputOverride {
        return InputOverride(
            input = Input(
                moveVector.y > 0.0f,
                moveVector.y < 0.0f,
                moveVector.x > 0.0f,
                moveVector.x < 0.0f,
                jump,
                sneak,
                sprint
            ),
            moveVector = moveVector,
            yaw = yaw,
            pitch = pitch
        )
    }

    private sealed interface ActiveController {
        val ringId: Int
        fun tick(player: net.minecraft.client.player.LocalPlayer): Boolean
        fun input(player: net.minecraft.client.player.LocalPlayer): InputOverride?
    }

    private data class WalkController(
        override val ringId: Int,
        val yaw: Float,
        val sprint: Boolean,
        val sneak: Boolean
    ) : ActiveController {
        override fun tick(player: net.minecraft.client.player.LocalPlayer): Boolean = false

        override fun input(player: net.minecraft.client.player.LocalPlayer): InputOverride {
            return InputOverride(
                input = Input(true, false, false, false, false, sneak, sprint),
                moveVector = Vec2(0f, 1f),
                yaw = yaw,
                pitch = player.xRot
            )
        }
    }

    private data class StopController(
        override val ringId: Int,
        var settledTicks: Int = 0
    ) : ActiveController {
        override fun tick(player: net.minecraft.client.player.LocalPlayer): Boolean {
            player.deltaMovement = player.deltaMovement.multiply(0.35, 1.0, 0.35)
            val horizontal = player.deltaMovement.horizontalDistanceSqr()
            settledTicks = if (horizontal < 0.0025) settledTicks + 1 else 0
            return settledTicks >= 3
        }

        override fun input(player: net.minecraft.client.player.LocalPlayer): InputOverride {
            return InputOverride(Input.EMPTY, Vec2.ZERO, player.yRot, player.xRot)
        }
    }

    private data class EdgeJumpController(
        override val ringId: Int,
        val yaw: Float,
        val landingZone: LandingZone?,
        var jumpIssued: Boolean = false,
        var leftGround: Boolean = false,
        var landedTicks: Int = 0,
        var previewPath: List<Vec3> = emptyList(),
        var previewLandsInZone: Boolean = false
    ) : ActiveController {
        override fun tick(player: net.minecraft.client.player.LocalPlayer): Boolean {
            if (!player.onGround()) {
                leftGround = true
                landedTicks = 0
            }
            if (leftGround && player.onGround()) {
                landedTicks++
                previewPath = emptyList()
                previewLandsInZone = landingZone?.contains(player.x, player.z) == true
                if (landingZone == null) {
                    return landedTicks >= 1
                }

                val centered = isNearLandingZoneCenter(player.position(), landingZone)
                val horizontalStopped = player.deltaMovement.horizontalDistanceSqr() <= EDGE_GROUND_STOP_SPEED
                return landedTicks >= EDGE_GROUND_SETTLE_TICKS && centered && horizontalStopped
            }
            return false
        }

        override fun input(player: net.minecraft.client.player.LocalPlayer): InputOverride {
            if (!player.onGround()) {
                jumpIssued = true
                leftGround = true
            }

            if (!leftGround) {
                val shouldJump = player.onGround() && isAtForwardEdge(player, yaw)
                if (shouldJump) {
                    jumpIssued = true
                }
                previewPath = emptyList()
                previewLandsInZone = false
                return inputOverride(
                    moveVector = Vec2(0f, 1f),
                    yaw = yaw,
                    pitch = player.xRot,
                    jump = shouldJump,
                    sprint = true
                )
            }

            if (player.onGround()) {
                val move = landingZone?.let { groundedLandingMove(player.position(), it, yaw) } ?: Vec2.ZERO
                return inputOverride(
                    moveVector = move,
                    yaw = yaw,
                    pitch = player.xRot,
                    sneak = true
                )
            }

            val move = if (landingZone != null) {
                val plan = chooseEdgeTrajectory(player, landingZone, yaw)
                previewPath = plan.path
                previewLandsInZone = plan.landsInZone
                plan.firstMove
            } else {
                previewPath = emptyList()
                previewLandsInZone = false
                Vec2(0f, 1f)
            }

            val sneak = landingZone != null && shouldPreLandSneak(player, landingZone)

            return inputOverride(
                moveVector = move,
                yaw = yaw,
                pitch = player.xRot,
                sneak = sneak,
                sprint = true
            )
        }
    }

    private data class AlignController(
        override val ringId: Int,
        val ringType: RingType,
        val target: Vec3,
        var plannedInputs: Queue<Pair<Float, Boolean>>? = null,
        var initialized: Boolean = false,
        var failed: Boolean = false
    ) : ActiveController {
        companion object {
            fun create(
                ringId: Int,
                ringType: RingType,
                target: Vec3
            ): AlignController {
                return AlignController(ringId, ringType, target)
            }

            private fun getDisplacementFromInput(walkSpeed: Double, sneaking: Boolean): Double {
                val appliedSpeed = if (sneaking) walkSpeed * 0.3 else walkSpeed
                val movementTicks = ceil(ln(0.003 / (0.098 * appliedSpeed)) / ln(0.546000082)).toInt()
                return 0.098 * appliedSpeed * (1.0 - 0.546000082.pow(movementTicks)) / 0.45399991799999995
            }

            private fun getDisplacementVector(velocity: Vec2): Vec2 {
                val magnitude = sqrt(velocity.x * velocity.x + velocity.y * velocity.y)
                if (magnitude < 1.0E-6f) {
                    return Vec2.ZERO
                }

                val movementTicks = ceil(ln(0.003 / magnitude) / ln(0.546000082)).toInt()
                val displacementMagnitude = if (movementTicks <= 0) {
                    magnitude.toDouble()
                } else {
                    magnitude * (1.0 - 0.546000082.pow(movementTicks)) / 0.45399991799999995
                }
                val scale = (displacementMagnitude / magnitude).toFloat()
                return Vec2(velocity.x * scale, velocity.y * scale)
            }
        }

        override fun tick(player: net.minecraft.client.player.LocalPlayer): Boolean {
            if (failed) return true
            if (!initialized) {
                if (!player.onGround()) return false
                if (!initialize(player)) {
                    failed = true
                    return true
                }
            }

            val queue = plannedInputs ?: return false
            if (queue.isNotEmpty()) return false

            val velocity = player.deltaMovement
            if (velocity.x == 0.0 && velocity.z == 0.0) {
                return true
            }
            if (velocity.horizontalDistance() > 0.09) {
                return false
            }

            val flatTarget = Vec3(target.x, player.position().y, target.z)
            return player.position().distanceToSqr(flatTarget) <= precision() * precision()
        }

        override fun input(player: net.minecraft.client.player.LocalPlayer): InputOverride? {
            if (failed) return null
            if (!initialized) {
                if (!player.onGround()) return null
                if (!initialize(player)) {
                    failed = true
                    return null
                }
            }

            val queue = plannedInputs ?: return null
            val next = queue.peek() ?: return null
            if (next.second && !player.isShiftKeyDown) {
                return InputOverride(
                    input = Input(false, false, false, false, false, true, false),
                    moveVector = Vec2.ZERO,
                    yaw = player.yRot,
                    pitch = player.xRot
                )
            }

            val (plannedYaw, sneak) = queue.poll() ?: return null
            val movement = plannedMoveVector(player.yRot, plannedYaw)
            if (movement.horizontalDistanceSquared() <= 1.0E-6f) {
                return null
            }

            val left = movement.x > 0.0f
            val right = movement.x < 0.0f
            val forward = movement.y > 0.0f
            val backward = movement.y < 0.0f

            return InputOverride(
                input = Input(forward, backward, left, right, false, sneak, false),
                moveVector = movement,
                yaw = player.yRot,
                pitch = player.xRot
            )
        }

        private fun precision(): Double {
            return if (ringType == RingType.FAST_ALIGN) 0.0625 else 1.0E-4
        }

        private fun initialize(player: net.minecraft.client.player.LocalPlayer): Boolean {
            val initialVelocity = player.deltaMovement
            val initialDisplacement = getDisplacementVector(Vec2(initialVelocity.x.toFloat(), initialVelocity.z.toFloat()))
            val futurePosition = player.position().add(initialDisplacement.x.toDouble(), 0.0, initialDisplacement.y.toDouble())
            val delta = Vec3(target.x - futurePosition.x, 0.0, target.z - futurePosition.z)
            val deltaLength = sqrt(delta.x * delta.x + delta.z * delta.z)
            var sneak = true
            var displacement = getDisplacementFromInput(player.abilities.walkingSpeed.toDouble() * 10.0, sneak)

            if (deltaLength < 0.01) {
                plannedInputs = LinkedList()
                initialized = true
                return true
            }

            if (deltaLength > 2.0 * displacement) {
                sneak = false
                displacement = getDisplacementFromInput(player.abilities.walkingSpeed.toDouble() * 10.0, sneak)
                if (deltaLength > 2.0 * displacement) {
                    return false
                }
            }

            val yaw = atan2(-delta.z, delta.x)
            val theta = acos((deltaLength / (2.0 * displacement)).coerceIn(-1.0, 1.0))
            plannedInputs = LinkedList<Pair<Float, Boolean>>().apply {
                add(((-Math.toDegrees(yaw + theta)).toFloat() - 90f) to sneak)
                add(((-Math.toDegrees(yaw - theta)).toFloat() - 90f) to sneak)
            }
            copyAlignRingToClipboard(ringId, target, ringType)
            initialized = true
            return true
        }

        private fun plannedMoveVector(currentYaw: Float, plannedYaw: Float): Vec2 {
            val plannedYawRadians = Math.toRadians(plannedYaw.toDouble())
            val worldX = -sin(plannedYawRadians)
            val worldZ = cos(plannedYawRadians)

            val currentYawRadians = Math.toRadians(currentYaw.toDouble())
            val left = worldX * cos(currentYawRadians) + worldZ * sin(currentYawRadians)
            val forward = -worldX * sin(currentYawRadians) + worldZ * cos(currentYawRadians)
            return Vec2(left.toFloat(), forward.toFloat())
        }

        private fun Vec2.horizontalDistanceSquared(): Float = x * x + y * y
    }

    private fun chooseEdgeTrajectory(
        player: net.minecraft.client.player.LocalPlayer,
        landingZone: LandingZone,
        yaw: Float
    ): TrajectoryPlan {
        return airMoveOptions
            .asSequence()
            .map { simulateEdgeTrajectory(player, landingZone, yaw, it) }
            .minByOrNull(TrajectoryPlan::score)
            ?: TrajectoryPlan(Vec2.ZERO, listOf(player.position()), null, false, Double.POSITIVE_INFINITY)
    }

    private fun simulateEdgeTrajectory(
        player: net.minecraft.client.player.LocalPlayer,
        landingZone: LandingZone,
        yaw: Float,
        firstMove: Vec2
    ): TrajectoryPlan {
        var position = player.position()
        var velocity = player.deltaMovement
        val path = ArrayList<Vec3>(EDGE_MAX_SIM_TICKS + 1)
        path += position
        var bestHorizontalScore = landingZoneScore(position, landingZone)

        for (tick in 0 until EDGE_MAX_SIM_TICKS) {
            val move = if (tick == 0) firstMove else guidedAirMove(position, landingZone, yaw)
            val movedVelocity = applyAirAcceleration(velocity, move, yaw)
            val nextPosition = position.add(movedVelocity)
            path += nextPosition

            val planeCrossing = zonePlaneCrossing(position, nextPosition, landingZone)
            if (planeCrossing != null) {
                val landedInside = landingZone.contains(planeCrossing.x, planeCrossing.z)
                val landingScore = if (landedInside) {
                    distanceToCenterScore(planeCrossing, landingZone)
                } else {
                    32.0 + landingZoneScore(planeCrossing, landingZone)
                }
                return TrajectoryPlan(firstMove, path, planeCrossing, landedInside, landingScore)
            }

            bestHorizontalScore = min(bestHorizontalScore, landingZoneScore(nextPosition, landingZone))
            velocity = applyAirPostMovement(player, movedVelocity)
            position = nextPosition

            if (position.y < landingZone.topSurfaceY() - EDGE_PREVIEW_FALL_MARGIN && velocity.y <= 0.0) {
                break
            }
        }

        val fallback = path.last()
        val verticalPenalty = abs(fallback.y - landingZone.topSurfaceY()) * 0.2
        return TrajectoryPlan(firstMove, path, null, false, 96.0 + bestHorizontalScore + verticalPenalty)
    }

    private fun guidedAirMove(position: Vec3, landingZone: LandingZone, yaw: Float): Vec2 {
        val target = preferredLandingTarget(landingZone)
        val targetX = target.x
        val targetZ = target.z
        return discreteLocalMove(yaw, targetX - position.x, targetZ - position.z)
    }

    private fun groundedLandingMove(position: Vec3, landingZone: LandingZone, yaw: Float): Vec2 {
        val target = preferredLandingTarget(landingZone)
        val deltaX = target.x - position.x
        val deltaZ = target.z - position.z
        if (abs(deltaX) <= EDGE_GROUND_CENTER_TOLERANCE && abs(deltaZ) <= EDGE_GROUND_CENTER_TOLERANCE) {
            return Vec2.ZERO
        }
        return discreteLocalMove(yaw, deltaX, deltaZ)
    }

    private fun preferredLandingTarget(landingZone: LandingZone): Vec3 {
        val center = landingZone.center()
        return Vec3(
            landingZone.clampX(center.x, landingInteriorMargin(landingZone)),
            center.y,
            landingZone.clampZ(center.z, landingInteriorMargin(landingZone))
        )
    }

    private fun discreteLocalMove(yaw: Float, worldX: Double, worldZ: Double): Vec2 {
        val local = localMoveVector(yaw, worldX, worldZ)
        return Vec2(axisMove(local.x), axisMove(local.y))
    }

    private fun axisMove(value: Float): Float {
        return when {
            value > 0.12f -> 1f
            value < -0.12f -> -1f
            else -> 0f
        }
    }

    private fun localMoveVector(yaw: Float, worldX: Double, worldZ: Double): Vec2 {
        val yawRadians = Math.toRadians(yaw.toDouble())
        val left = worldX * cos(yawRadians) + worldZ * sin(yawRadians)
        val forward = -worldX * sin(yawRadians) + worldZ * cos(yawRadians)
        return Vec2(left.toFloat(), forward.toFloat())
    }

    private fun applyAirAcceleration(velocity: Vec3, move: Vec2, yaw: Float): Vec3 {
        val lengthSqr = (move.x * move.x + move.y * move.y).toDouble()
        if (lengthSqr <= 1.0E-7) return velocity

        val normalized = if (lengthSqr > 1.0) {
            val scale = (1.0 / sqrt(lengthSqr)).toFloat()
            Vec2(move.x * scale, move.y * scale)
        } else {
            move
        }

        val scaledLeft = normalized.x.toDouble() * PLAYER_AIR_ACCELERATION
        val scaledForward = normalized.y.toDouble() * PLAYER_AIR_ACCELERATION
        val yawRadians = Math.toRadians(yaw.toDouble())
        val sinYaw = sin(yawRadians)
        val cosYaw = cos(yawRadians)
        val worldX = scaledLeft * cosYaw - scaledForward * sinYaw
        val worldZ = scaledForward * cosYaw + scaledLeft * sinYaw
        return velocity.add(worldX, 0.0, worldZ)
    }

    private fun applyAirPostMovement(player: net.minecraft.client.player.LocalPlayer, movedVelocity: Vec3): Vec3 {
        val gravity = effectiveGravity(player, movedVelocity.y)
        return Vec3(
            movedVelocity.x * PLAYER_AIR_DRAG,
            (movedVelocity.y - gravity) * PLAYER_VERTICAL_DRAG,
            movedVelocity.z * PLAYER_AIR_DRAG
        )
    }

    private fun effectiveGravity(player: net.minecraft.client.player.LocalPlayer, verticalVelocity: Double): Double {
        val gravity = player.getAttributeValue(Attributes.GRAVITY)
        return if (verticalVelocity <= 0.0 && player.hasEffect(MobEffects.SLOW_FALLING)) {
            min(gravity, 0.01)
        } else {
            gravity
        }
    }

    private fun zonePlaneCrossing(from: Vec3, to: Vec3, landingZone: LandingZone): Vec3? {
        val topY = landingZone.topSurfaceY()
        if (from.y < topY || to.y > topY || to.y >= from.y) {
            return null
        }
        val deltaY = from.y - to.y
        if (deltaY <= 1.0E-6) return null
        val t = (from.y - topY) / deltaY
        val x = from.x + (to.x - from.x) * t
        val z = from.z + (to.z - from.z) * t
        return Vec3(x, topY, z)
    }

    private fun landingZoneScore(position: Vec3, landingZone: LandingZone): Double {
        val minX = landingZone.minX + EDGE_ZONE_SAFE_MARGIN
        val maxX = landingZone.maxX + 1.0 - EDGE_ZONE_SAFE_MARGIN
        val minZ = landingZone.minZ + EDGE_ZONE_SAFE_MARGIN
        val maxZ = landingZone.maxZ + 1.0 - EDGE_ZONE_SAFE_MARGIN
        val dx = when {
            position.x < minX -> minX - position.x
            position.x > maxX -> position.x - maxX
            else -> 0.0
        }
        val dz = when {
            position.z < minZ -> minZ - position.z
            position.z > maxZ -> position.z - maxZ
            else -> 0.0
        }
        return if (dx == 0.0 && dz == 0.0) {
            val centerScore = distanceToCenterScore(position, landingZone) * 3.0
            val edgePenalty = edgeMarginPenalty(position, landingZone)
            centerScore + edgePenalty
        } else {
            dx * dx + dz * dz
        }
    }

    private fun distanceToCenterScore(position: Vec3, landingZone: LandingZone): Double {
        val center = landingZone.center()
        val dx = position.x - center.x
        val dz = position.z - center.z
        return dx * dx + dz * dz
    }

    private fun edgeMarginPenalty(position: Vec3, landingZone: LandingZone): Double {
        val edgeDistance = distanceToLandingZoneEdge(position, landingZone)
        val preferredMargin = landingInteriorMargin(landingZone)
        if (edgeDistance >= preferredMargin) return 0.0
        val shortfall = preferredMargin - edgeDistance
        return shortfall * shortfall * 10.0
    }

    private fun distanceToLandingZoneEdge(position: Vec3, landingZone: LandingZone): Double {
        val left = position.x - landingZone.minX
        val right = landingZone.maxX + 1.0 - position.x
        val north = position.z - landingZone.minZ
        val south = landingZone.maxZ + 1.0 - position.z
        return minOf(left, right, north, south)
    }

    private fun landingInteriorMargin(landingZone: LandingZone): Double {
        val halfWidth = (landingZone.maxX - landingZone.minX + 1) / 2.0
        val halfDepth = (landingZone.maxZ - landingZone.minZ + 1) / 2.0
        return minOf(EDGE_CENTER_TARGET_MARGIN, halfWidth - 0.08, halfDepth - 0.08).coerceAtLeast(0.12)
    }

    private fun shouldPreLandSneak(
        player: net.minecraft.client.player.LocalPlayer,
        landingZone: LandingZone
    ): Boolean {
        return player.deltaMovement.y <= 0.0 &&
            player.y - landingZone.topSurfaceY() <= EDGE_PRE_LANDING_SHIFT_HEIGHT
    }

    private fun isNearLandingZoneCenter(position: Vec3, landingZone: LandingZone): Boolean {
        val target = preferredLandingTarget(landingZone)
        return abs(position.x - target.x) <= EDGE_GROUND_CENTER_TOLERANCE &&
            abs(position.z - target.z) <= EDGE_GROUND_CENTER_TOLERANCE
    }

    private fun isAtForwardEdge(player: net.minecraft.client.player.LocalPlayer, yaw: Float): Boolean {
        if (!player.onGround()) return false

        val level = player.level()
        val position = player.position()
        val yawRadians = Math.toRadians(yaw.toDouble())
        val forwardX = -sin(yawRadians)
        val forwardZ = cos(yawRadians)
        val leftX = cos(yawRadians)
        val leftZ = sin(yawRadians)
        val groundY = player.boundingBox.minY - 0.05

        val currentSupported = hasSupportBelow(level, position.x, groundY, position.z)
        if (!currentSupported) return false

        val probes = listOf(
            Vec3(
                position.x + forwardX * EDGE_PROBE_FORWARD + leftX * EDGE_PROBE_LATERAL,
                groundY,
                position.z + forwardZ * EDGE_PROBE_FORWARD + leftZ * EDGE_PROBE_LATERAL
            ),
            Vec3(
                position.x + forwardX * EDGE_PROBE_FORWARD - leftX * EDGE_PROBE_LATERAL,
                groundY,
                position.z + forwardZ * EDGE_PROBE_FORWARD - leftZ * EDGE_PROBE_LATERAL
            )
        )
        return probes.none { hasSupportBelow(level, it.x, it.y, it.z) }
    }

    private fun hasSupportBelow(level: Level, x: Double, y: Double, z: Double): Boolean {
        val blockPos = BlockPos.containing(x, y - 0.5, z)
        return !level.getBlockState(blockPos).getCollisionShape(level, blockPos).isEmpty
    }

    private data class RouteController(
        override val ringId: Int,
        val route: RecordedRoute,
        var index: Int = 0
    ) : ActiveController {
        override fun tick(player: net.minecraft.client.player.LocalPlayer): Boolean {
            while (index < route.points.size) {
                val point = route.points[index]
                val dx = point.x - player.x
                val dy = point.y - player.y
                val dz = point.z - player.z
                if (dx * dx + dy * dy + dz * dz > 0.35 * 0.35) {
                    return false
                }
                index++
            }
            return true
        }

        override fun input(player: net.minecraft.client.player.LocalPlayer): InputOverride? {
            val point = route.points.getOrNull(index) ?: return null
            val dx = point.x - player.x
            val dz = point.z - player.z
            val needsJump = point.y - player.y > 0.45
            val yaw = Math.toDegrees(atan2(-dx, dz)).toFloat()
            return InputOverride(
                input = Input(true, false, false, false, needsJump, false, true),
                moveVector = Vec2(0f, 1f),
                yaw = yaw,
                pitch = point.pitch
            )
        }
    }
}
