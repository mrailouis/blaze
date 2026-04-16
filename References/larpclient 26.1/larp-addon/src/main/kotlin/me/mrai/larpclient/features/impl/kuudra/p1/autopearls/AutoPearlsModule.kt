package me.mrai.larpclient.features.impl.kuudra.p1.autopearls

import me.mrai.larpclient.features.impl.kuudra.p1.kuudrawaypoints.KuudraWaypointModule
import me.mrai.larpclient.features.impl.misc.other.etherwarp.EtherwarpController
import me.mrai.larpclient.mixin.MultiPlayerGameModeInvoker
import me.mrai.larpclient.module.BoolSetting
import me.mrai.larpclient.module.Module
import me.mrai.larpclient.module.ModuleCategory
import me.mrai.larpclient.module.SliderSetting
import net.minecraft.client.Minecraft
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket
import net.minecraft.network.protocol.game.ServerboundPlayerInputPacket
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket
import net.minecraft.network.protocol.game.ServerboundUseItemPacket
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.player.Input
import net.minecraft.world.item.Items
import net.minecraft.world.phys.Vec3

object AutoPearlsModule : Module(
    name = "Auto Pearls",
    description = "Automatically throws Kuudra waypoint pearls at the timed angles.",
    category = ModuleCategory.KUUDRA_P1
) {
    private data class QueuedThrow(val hint: KuudraWaypointModule.AutoPearlHint, val executeAt: Long)
    private data class QueuedEtherwarp(
        val key: String,
        val target: Vec3,
        val armAt: Long,
        val expiresAt: Long,
        val triggerPos: Vec3,
        val requireProximity: Boolean = true,
        var landedAt: Long? = null
    )
    private data class EtherwarpRoute(val origin: String, val destination: String, val target: Vec3)
    private data class EtherwarpNode(val id: String, val pos: Vec3)

    private const val PLACE_COOLDOWN_MS = 350L
    private const val PEARL_COOLDOWN_MS = 1000L
    private const val ETHERWARP_DELAY_MS = 75L
    private const val POST_LAND_DELAY_MS = 100L
    private const val ETHERWARP_TRIGGER_RADIUS = 3.25
    private const val MAX_ETHERWARP_RANGE = 61.0

    private val etherwarpExtras = BoolSetting("Etherwarp Extras", false)
    private val simulatedPingMs = SliderSetting("Simulated Ping", 0.0, 0.0, 500.0, 5.0)
    private val executedKeys = linkedSetOf<String>()
    private val queuedThrows = ArrayDeque<QueuedThrow>()
    private val queuedEtherwarps = ArrayDeque<QueuedEtherwarp>()

    private val xcEtherwarpTop = Vec3(-129.5, 79.0, -114.5)
    private val squareEtherwarpTop = Vec3(-137.5, 79.0, -87.5)
    private val shopEtherwarpTop = Vec3(-74.5, 79.0, -135.5)
    private val xMainPos = Vec3(-106.0, 78.0, -113.0)
    private val slashPilePos = Vec3(-106.0, 78.0, -99.0)
    private val equalsPilePos = Vec3(-98.0, 78.0, -99.0)
    private val triMainPos = Vec3(-94.0, 78.0, -106.0)

    private val etherwarpNodes = listOf(
        EtherwarpNode("SHOP", shopEtherwarpTop),
        EtherwarpNode("XC", xcEtherwarpTop),
        EtherwarpNode("SQUARE", squareEtherwarpTop),
        EtherwarpNode("X", xMainPos),
        EtherwarpNode("SLASH", slashPilePos),
        EtherwarpNode("EQUALS", equalsPilePos),
        EtherwarpNode("TRI", triMainPos)
    )

    private val etherwarpRoutes = listOf(
        EtherwarpRoute("TRI", "SHOP", shopEtherwarpTop),
        EtherwarpRoute("TRI", "XC", xcEtherwarpTop),
        EtherwarpRoute("TRI", "SQUARE", squareEtherwarpTop),
        EtherwarpRoute("X", "SHOP", shopEtherwarpTop),
        EtherwarpRoute("X", "XC", xcEtherwarpTop),
        EtherwarpRoute("X", "SQUARE", squareEtherwarpTop),
        EtherwarpRoute("SLASH", "SHOP", shopEtherwarpTop),
        EtherwarpRoute("SLASH", "XC", xcEtherwarpTop),
        EtherwarpRoute("SLASH", "SQUARE", squareEtherwarpTop),
        EtherwarpRoute("EQUALS", "SHOP", shopEtherwarpTop),
        EtherwarpRoute("EQUALS", "XC", xcEtherwarpTop),
        EtherwarpRoute("EQUALS", "SQUARE", squareEtherwarpTop)
    )

    init {
        settings += listOf(etherwarpExtras, simulatedPingMs)
    }

    override fun onDisable() {
        executedKeys.clear()
        queuedThrows.clear()
        queuedEtherwarps.clear()
    }

    override fun onTick() {
        val now = System.currentTimeMillis()
        while (queuedThrows.isNotEmpty() && queuedThrows.first().executeAt <= now) {
            val queued = queuedThrows.removeFirst()
            if (queued.hint.key in executedKeys) continue
            if (tryThrow(queued.hint)) {
                executedKeys += queued.hint.key
                maybeQueueEtherwarp(queued.hint, KuudraWaypointModule.getAutoPearlHints().groupBy { it.group }, now)
            }
        }
        val player = Minecraft.getInstance().player
        val etherwarpIterator = queuedEtherwarps.iterator()
        while (etherwarpIterator.hasNext()) {
            val queued = etherwarpIterator.next()
            if (queued.key in executedKeys) {
                etherwarpIterator.remove()
                continue
            }
            if (now > queued.expiresAt) {
                etherwarpIterator.remove()
                continue
            }
            if (now < queued.armAt || player == null) {
                continue
            }

            if (!queued.requireProximity && queued.landedAt == null) {
                queued.landedAt = queued.armAt
            } else {
                val landed = player.position().distanceTo(queued.triggerPos) <= ETHERWARP_TRIGGER_RADIUS
                if (landed && queued.landedAt == null) {
                    queued.landedAt = now
                }
            }

            val landingDelaySatisfied = queued.landedAt?.let { now - it >= POST_LAND_DELAY_MS + pingDelayMs() } == true
            if ((queued.landedAt != null && landingDelaySatisfied) || now >= queued.expiresAt - 150L) {
                if (tryEtherwarp(queued.target)) {
                    executedKeys += queued.key
                }
                etherwarpIterator.remove()
            }
        }

        val hints = KuudraWaypointModule.getAutoPearlHints()
        if (hints.isEmpty()) {
            executedKeys.clear()
            return
        }

        val elapsed = KuudraWaypointModule.getElapsedSupplyMs() ?: return
        if (elapsed <= 250L) {
            executedKeys.clear()
            queuedThrows.clear()
            queuedEtherwarps.clear()
        }

        executedKeys.retainAll(hints.mapTo(hashSetOf()) { it.key })
        val grouped = hints.groupBy { it.group }
        val reservedEtherwarpKeys = queuedEtherwarps.mapTo(hashSetOf()) { it.key }
        val reservedThrowKeys = queuedThrows.mapTo(hashSetOf()) { it.hint.key }

        val orderedGroups = if (KuudraWaypointModule.isInMainArea()) {
            listOf("MAIN", "PRE")
        } else if (KuudraWaypointModule.isInSecondaryArea()) {
            listOf("SECONDARY")
        } else {
            emptyList()
        }

        val hasPendingPre = grouped["PRE"].orEmpty().any { it.key !in executedKeys }
        for (group in orderedGroups) {
            if (queuedThrows.isNotEmpty()) break
            val hint = chooseHint(grouped[group].orEmpty()) ?: continue
            if (hint.key in executedKeys || hint.key in reservedEtherwarpKeys || hint.key in reservedThrowKeys) continue
            if (elapsed < hint.throwAtMs.coerceAtLeast(0L)) continue
            val pingDelay = pingDelayMs()
            if (pingDelay > 0L) {
                queuedThrows += QueuedThrow(hint, now + pingDelay)
            } else if (tryThrow(hint)) {
                executedKeys += hint.key
                if (group == "MAIN" && hasPendingPre) {
                    queuePairedPreThrow(hint, grouped["PRE"].orEmpty(), now)
                }
                maybeQueueEtherwarp(hint, grouped, now)
            }
        }
    }

    fun debugTriggerP1(): Boolean {
        val hints = KuudraWaypointModule.getAutoPearlHints().groupBy { it.group }
        queuedThrows.clear()
        queuedEtherwarps.clear()

        val preHint = choosePreHint(hints["PRE"].orEmpty())
        val mainHint = chooseMainFlatHint(hints["MAIN"].orEmpty())

        if (preHint != null) {
            val now = System.currentTimeMillis()
            if (!tryThrow(preHint)) return false
            executedKeys += preHint.key
            mainHint?.let {
                val delay = (preHint.travelMs - it.travelMs - PEARL_COOLDOWN_MS).coerceAtLeast(PLACE_COOLDOWN_MS)
                queuedThrows += QueuedThrow(it, now + delay + pingDelayMs())
            }
            return true
        }

        return mainHint?.let {
            val ok = tryThrow(it)
            if (ok) {
                executedKeys += it.key
                maybeQueueEtherwarp(it, hints, System.currentTimeMillis())
            }
            ok
        } == true
    }

    fun debugTriggerP2(): Boolean {
        val hints = KuudraWaypointModule.getAutoPearlHints().groupBy { it.group }
        val hint = chooseHint(hints["SECONDARY"].orEmpty()) ?: return false
        return tryThrow(hint)
    }

    private fun chooseHint(hints: List<KuudraWaypointModule.AutoPearlHint>): KuudraWaypointModule.AutoPearlHint? {
        if (hints.isEmpty()) return null
        val nonNegative = hints.filter { it.throwAtMs >= 0L }
        val pool = if (nonNegative.isNotEmpty()) nonNegative else hints
        return pool.maxWithOrNull(compareBy<KuudraWaypointModule.AutoPearlHint> { it.throwAtMs }.thenBy { it.label })
    }

    private fun choosePreHint(hints: List<KuudraWaypointModule.AutoPearlHint>): KuudraWaypointModule.AutoPearlHint? {
        val skyHints = hints.filter { it.label.contains("Sky", ignoreCase = true) }
        if (skyHints.isNotEmpty()) {
            return chooseHint(skyHints)
        }
        return chooseHint(hints)
    }

    private fun chooseMainFlatHint(hints: List<KuudraWaypointModule.AutoPearlHint>): KuudraWaypointModule.AutoPearlHint? {
        val flatHints = hints.filter { it.label.contains("Flat", ignoreCase = true) }
        if (flatHints.isNotEmpty()) {
            return chooseHint(flatHints)
        }
        return chooseHint(hints)
    }

    private fun queuePairedPreThrow(
        mainHint: KuudraWaypointModule.AutoPearlHint,
        preHints: List<KuudraWaypointModule.AutoPearlHint>,
        now: Long
    ) {
        val preHint = choosePreHint(preHints) ?: return
        if (preHint.key in executedKeys) return
        if (queuedThrows.any { it.hint.key == preHint.key }) return

        val delay = (mainHint.travelMs - preHint.travelMs - PEARL_COOLDOWN_MS).coerceAtLeast(PLACE_COOLDOWN_MS)
        queuedThrows += QueuedThrow(preHint, now + delay + pingDelayMs())
    }

    private fun tryThrow(hint: KuudraWaypointModule.AutoPearlHint): Boolean {
        val client = Minecraft.getInstance()
        val player = client.player ?: return false
        val gameMode = client.gameMode ?: return false
        val connection = client.connection ?: return false

        val pearlSlot = findPearlSlot(player) ?: return false
        val oldSlot = player.inventory.selectedSlot
        val oldYaw = player.yRot
        val oldPitch = player.xRot

        if (oldSlot != pearlSlot) {
            player.inventory.selectedSlot = pearlSlot
            connection.send(ServerboundSetCarriedItemPacket(pearlSlot))
        }

        player.yRot = hint.yaw
        player.xRot = hint.pitch
        connection.send(ServerboundMovePlayerPacket.Rot(hint.yaw, hint.pitch, player.onGround(), player.horizontalCollision))
        val result = gameMode.useItem(player, InteractionHand.MAIN_HAND)
        player.swing(InteractionHand.MAIN_HAND)

        player.yRot = oldYaw
        player.xRot = oldPitch
        connection.send(ServerboundMovePlayerPacket.Rot(oldYaw, oldPitch, player.onGround(), player.horizontalCollision))

        if (oldSlot != pearlSlot) {
            player.inventory.selectedSlot = oldSlot
            connection.send(ServerboundSetCarriedItemPacket(oldSlot))
        }

        return result.consumesAction()
    }

    private fun maybeQueueEtherwarp(mainHint: KuudraWaypointModule.AutoPearlHint, grouped: Map<String, List<KuudraWaypointModule.AutoPearlHint>>, now: Long) {
        if (!etherwarpExtras.value || mainHint.group != "MAIN") return
        if (choosePreHint(grouped["PRE"].orEmpty()) != null) return

        val origin = mainHint.label.substringBefore(' ').uppercase()
        val destination = KuudraWaypointModule.getSecondaryPriorityLabelForOrigin(origin)?.uppercase() ?: return
        val directRoute = etherwarpRoutes.firstOrNull { it.origin == origin && it.destination == destination }
        if (directRoute != null) {
            enqueueEtherwarpStep(
                key = "EW:$origin:$destination",
                target = directRoute.target,
                armAt = now + mainHint.travelMs + pingDelayMs(),
                expiresAt = now + mainHint.travelMs + 1200L + pingDelayMs(),
                triggerPos = mainDestinationFor(origin),
                requireProximity = false
            )
            return
        }

        if (destination != "SHOP") {
            return
        }

        val start = mainDestinationFor(origin)
        if (start == Vec3.ZERO) {
            return
        }
        val path = findEtherwarpPath(start, shopEtherwarpTop) ?: return
        var triggerPos = start
        var stepArmAt = now + (mainHint.travelMs / 2L).coerceAtLeast(150L)
        path.forEachIndexed { index, step ->
            enqueueEtherwarpStep(
                key = "EWPATH:$origin:SHOP:$index",
                target = step,
                armAt = stepArmAt,
                expiresAt = stepArmAt + 2000L + pingDelayMs(),
                triggerPos = triggerPos
            )
            triggerPos = step
            stepArmAt += POST_LAND_DELAY_MS + pingDelayMs()
        }
    }

    private fun enqueueEtherwarpStep(
        key: String,
        target: Vec3,
        armAt: Long,
        expiresAt: Long,
        triggerPos: Vec3,
        requireProximity: Boolean = true
    ) {
        if (key in executedKeys || queuedEtherwarps.any { it.key == key }) return
        queuedEtherwarps += QueuedEtherwarp(key, target, armAt, expiresAt, triggerPos, requireProximity)
    }

    private fun tryEtherwarp(target: Vec3): Boolean {
        val client = Minecraft.getInstance()
        val player = client.player ?: return false
        val gameMode = client.gameMode ?: return false
        val connection = client.connection ?: return false

        val etherwarpSlot = findEtherwarpSlot(player) ?: return false
        val oldSlot = player.inventory.selectedSlot
        val oldYaw = player.yRot
        val oldPitch = player.xRot
        val oldShift = player.isShiftKeyDown
        val (yaw, pitch) = yawPitch(player.eyePosition, target)

        if (oldSlot != etherwarpSlot) {
            player.inventory.selectedSlot = etherwarpSlot
            connection.send(ServerboundSetCarriedItemPacket(etherwarpSlot))
        }

        connection.send(ServerboundPlayerInputPacket(Input(false, false, false, false, false, true, false)))
        player.yRot = yaw
        player.xRot = pitch

        if (client.hasSingleplayerServer() || client.isLocalServer) {
            val simulated = EtherwarpController.simulateTeleportTo(client, target)
            player.yRot = oldYaw
            player.xRot = oldPitch
            if (oldSlot != etherwarpSlot) {
                player.inventory.selectedSlot = oldSlot
                connection.send(ServerboundSetCarriedItemPacket(oldSlot))
            }
            return simulated
        }

        connection.send(ServerboundMovePlayerPacket.Rot(yaw, pitch, player.onGround(), player.horizontalCollision))
        connection.send(ServerboundPlayerInputPacket(Input(false, false, false, false, false, true, false)))
        val level = client.level ?: return false
        (gameMode as MultiPlayerGameModeInvoker).callStartPrediction(level) { sequence ->
            ServerboundUseItemPacket(InteractionHand.MAIN_HAND, sequence, yaw, pitch)
        }

        player.yRot = oldYaw
        player.xRot = oldPitch
        connection.send(ServerboundMovePlayerPacket.Rot(oldYaw, oldPitch, player.onGround(), player.horizontalCollision))
        connection.send(ServerboundPlayerInputPacket(Input(false, false, false, false, false, oldShift, false)))

        if (oldSlot != etherwarpSlot) {
            player.inventory.selectedSlot = oldSlot
            connection.send(ServerboundSetCarriedItemPacket(oldSlot))
        }

        return true
    }

    private fun yawPitch(from: Vec3, to: Vec3): Pair<Float, Float> {
        val dx = to.x - from.x
        val dy = to.y - from.y
        val dz = to.z - from.z
        val horizontal = kotlin.math.sqrt(dx * dx + dz * dz)
        val yaw = Math.toDegrees(kotlin.math.atan2(dz, dx)).toFloat() - 90f
        val pitch = (-Math.toDegrees(kotlin.math.atan2(dy, horizontal))).toFloat()
        return yaw to pitch
    }

    private fun mainDestinationFor(origin: String): Vec3 {
        return when (origin) {
            "X" -> xMainPos
            "SLASH", "/" -> equalsPilePos
            "EQUALS", "=" -> slashPilePos
            "TRI" -> triMainPos
            else -> Vec3.ZERO
        }
    }

    private fun pingDelayMs(): Long = simulatedPingMs.value.toLong()

    private fun findEtherwarpPath(start: Vec3, target: Vec3): List<Vec3>? {
        if (start.distanceTo(target) <= MAX_ETHERWARP_RANGE) {
            return listOf(target)
        }

        val nodeById = etherwarpNodes.associateBy { it.id }
        val queue = ArrayDeque<String>()
        val previous = hashMapOf<String, String?>()

        val startNeighbors = etherwarpNodes.filter { start.distanceTo(it.pos) <= MAX_ETHERWARP_RANGE }
        for (node in startNeighbors) {
            queue += node.id
            previous[node.id] = null
        }

        while (queue.isNotEmpty()) {
            val currentId = queue.removeFirst()
            val current = nodeById[currentId] ?: continue
            if (current.pos.distanceTo(target) <= MAX_ETHERWARP_RANGE) {
                val path = mutableListOf<Vec3>(target)
                var walker: String? = currentId
                while (walker != null) {
                    val node = nodeById[walker] ?: break
                    path += node.pos
                    walker = previous[walker]
                }
                return path.asReversed().distinct()
            }

            for (neighbor in etherwarpNodes) {
                if (neighbor.id in previous) continue
                if (current.pos.distanceTo(neighbor.pos) > MAX_ETHERWARP_RANGE) continue
                previous[neighbor.id] = currentId
                queue += neighbor.id
            }
        }

        return null
    }

    private fun findPearlSlot(player: net.minecraft.client.player.LocalPlayer): Int? {
        for (slot in 0..8) {
            val stack = player.inventory.getItem(slot)
            if (stack.`is`(Items.ENDER_PEARL)) {
                return slot
            }
        }
        return null
    }

    private fun findEtherwarpSlot(player: net.minecraft.client.player.LocalPlayer): Int? {
        val slot = 0
        return if (player.inventory.getItem(slot).isEmpty) null else slot
    }
}
