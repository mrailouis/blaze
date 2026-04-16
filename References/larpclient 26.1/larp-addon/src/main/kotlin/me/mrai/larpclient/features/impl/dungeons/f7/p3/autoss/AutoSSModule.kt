package me.mrai.larpclient.features.impl.dungeons.f7.p3.autoss

import me.mrai.larpclient.module.BoolSetting
import me.mrai.larpclient.module.ComponentSetting
import me.mrai.larpclient.module.Module
import me.mrai.larpclient.module.ModuleCategory
import me.mrai.larpclient.module.SliderSetting
import me.mrai.larpclient.util.LarpChat
import me.mrai.larpclient.util.LarpBranding
import net.minecraft.client.Minecraft
import net.minecraft.client.player.LocalPlayer
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket
import net.minecraft.world.InteractionHand
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.ButtonBlock
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt

object AutoSSModule : Module(
    name = "Auto SS",
    description = "Ports Simon Shut Up AutoSS for Floor 7 P3 Simon Says.",
    category = ModuleCategory.DUNGEONS_F7_P3
) {
    private const val TOTAL_CYCLES = 5
    private const val BOSS_START_MESSAGE = "[BOSS] Goldor: Who dares trespass into my domain?"
    private const val RESET_COOLDOWN_MS = 500L
    private const val BUTTON_WAIT_TIMEOUT_MS = 1_000L
    private const val CLICK_COOLDOWN_MS = 60L
    private val stationPosition = Vec3(108.5, 120.0, 94.0)
    private val startButtonPos = BlockPos(110, 121, 91)
    private val startButtonLook = Vec3(111.0, 121.5, 91.5)

    private val buttonPositions = listOf(
        BlockPos(110, 123, 92), BlockPos(110, 123, 93), BlockPos(110, 123, 94), BlockPos(110, 123, 95),
        BlockPos(110, 122, 92), BlockPos(110, 122, 93), BlockPos(110, 122, 94), BlockPos(110, 122, 95),
        BlockPos(110, 121, 92), BlockPos(110, 121, 93), BlockPos(110, 121, 94), BlockPos(110, 121, 95),
        BlockPos(110, 120, 92), BlockPos(110, 120, 93), BlockPos(110, 120, 94), BlockPos(110, 120, 95)
    )

    private val buttonLookPositions = listOf(
        Vec3(111.0, 123.5, 92.5), Vec3(111.0, 123.5, 93.5), Vec3(111.0, 123.5, 94.5), Vec3(111.0, 123.5, 95.5),
        Vec3(111.0, 122.5, 92.5), Vec3(111.0, 122.5, 93.5), Vec3(111.0, 122.5, 94.5), Vec3(111.0, 122.5, 95.5),
        Vec3(111.0, 121.5, 92.5), Vec3(111.0, 121.5, 93.5), Vec3(111.0, 121.5, 94.5), Vec3(111.0, 121.5, 95.5),
        Vec3(111.0, 120.5, 92.5), Vec3(111.0, 120.5, 93.5), Vec3(111.0, 120.5, 94.5), Vec3(111.0, 120.5, 95.5)
    )

    private val obsidianPositions = listOf(
        BlockPos(111, 123, 92), BlockPos(111, 123, 93), BlockPos(111, 123, 94), BlockPos(111, 123, 95),
        BlockPos(111, 122, 92), BlockPos(111, 122, 93), BlockPos(111, 122, 94), BlockPos(111, 122, 95),
        BlockPos(111, 121, 92), BlockPos(111, 121, 93), BlockPos(111, 121, 94), BlockPos(111, 121, 95),
        BlockPos(111, 120, 92), BlockPos(111, 120, 93), BlockPos(111, 120, 94), BlockPos(111, 120, 95)
    )

    private val delaySetting = SliderSetting("Delay", 250.0, 0.0, 2_000.0, 10.0)
    private val autoStartSetting = ComponentSetting("Auto Start", "")
    private val noRotateSetting = BoolSetting("No Rotate", false)
    private val noSwingSetting = BoolSetting("No Swing", false)
    private val announceProgressSetting = BoolSetting("Announce Progress", false)

    private val buttonPresent = BooleanArray(buttonPositions.size)
    private val obsidianLit = BooleanArray(obsidianPositions.size)
    private var startButtonPresent = false
    private var snapshotReady = false

    private val solution = mutableListOf<Int>()
    private val prophecy = mutableListOf<Int>()
    private var phase = 0
    private var prophet = 0
    private var completedCycles = 0
    private var lastResetAt = 0L
    private val pendingAutoStarts = mutableListOf<Long>()
    private var automation: AutomationState? = null

    private data class AutomationState(
        val sequence: List<Int>,
        val cycleNumber: Int,
        var index: Int = 0,
        var rotateStartedAt: Long = 0L,
        var startYaw: Float = 0f,
        var startPitch: Float = 0f,
        var waitingSince: Long = 0L,
        var lastClickAt: Long = 0L
    )

    init {
        settings += listOf(
            delaySetting,
            autoStartSetting,
            noRotateSetting,
            noSwingSetting,
            announceProgressSetting
        )
    }

    override fun onDisable() {
        clearState(clearSnapshot = true)
    }

    fun onWorldChange() {
        clearState(clearSnapshot = true)
    }

    fun onChatMessage(message: String) {
        if (message != BOSS_START_MESSAGE) return

        clearPuzzle()
        scheduleAutoStart()
    }

    override fun onTick() {
        val client = Minecraft.getInstance()
        val player = client.player ?: run {
            clearState(clearSnapshot = true)
            return
        }
        val level = client.level ?: run {
            clearState(clearSnapshot = true)
            return
        }

        if (!snapshotReady) {
            primeSnapshot(level)
            snapshotReady = true
        }

        pollWorld(level)
        handleAutoStart(player)

        if (!enabled) {
            automation = null
            return
        }

        automation?.let { state ->
            if (!isAtSS(player)) {
                automation = null
                return
            }
            tickAutomation(player, state)
        }
    }

    private fun pollWorld(level: net.minecraft.client.multiplayer.ClientLevel) {
        for (index in buttonPositions.indices) {
            val present = isSimonButton(level.getBlockState(buttonPositions[index]).block)
            if (present != buttonPresent[index]) {
                buttonPresent[index] = present
            }
        }

        for (index in obsidianPositions.indices) {
            val lit = level.getBlockState(obsidianPositions[index]).block == Blocks.SEA_LANTERN
            if (lit != obsidianLit[index]) {
                obsidianLit[index] = lit
                if (lit) {
                    solution += index
                    prophecy += index
                }
            }
        }

        val startPresent = isSimonButton(level.getBlockState(startButtonPos).block)
        if (snapshotReady && startPresent != startButtonPresent) {
            startButtonPresent = startPresent
            if (startPresent) {
                tryResetFromStartButton()
            }
        } else {
            startButtonPresent = startPresent
        }

        val indicatorIndex = firstMissingProphecyIndex()
        if (indicatorIndex != null) {
            val indicatorPresent = buttonPresent[indicatorIndex]
            val previousIndicator = lastIndicatorPresent
            lastIndicatorPresent = indicatorPresent
            if (indicatorPresent && !previousIndicator) {
                onIndicatorPressed()
            } else if (!indicatorPresent && previousIndicator) {
                solution.clear()
            }
        } else {
            lastIndicatorPresent = false
        }
    }

    private var lastIndicatorPresent = false

    private fun onIndicatorPressed() {
        phase += 1
        if (phase == 1 && solution.size == 3) {
            if (solution.isNotEmpty()) {
                solution.removeAt(0)
            }
            phase = 2
            prophet = 1
        } else if (phase == solution.size + 1) {
            prophecy.getOrNull(prophet)?.let { solution.add(0, it) }
        } else if (phase != solution.size) {
            return
        }

        phase = solution.size
        if (enabled && solution.isNotEmpty()) {
            queueAutomation()
        }
    }

    private fun queueAutomation() {
        val cycle = (completedCycles + 1).coerceAtMost(TOTAL_CYCLES)
        automation = AutomationState(sequence = solution.toList(), cycleNumber = cycle)
    }

    private fun tickAutomation(player: LocalPlayer, state: AutomationState) {
        val now = System.currentTimeMillis()
        if (state.index >= state.sequence.size) {
            finishAutomation(state.cycleNumber)
            return
        }

        if (state.lastClickAt != 0L && now - state.lastClickAt < CLICK_COOLDOWN_MS) {
            return
        }

        val buttonIndex = state.sequence[state.index]
        val targetLook = buttonLookPositions[buttonIndex]

        if (!noRotateSetting.value) {
            if (state.rotateStartedAt == 0L) {
                state.rotateStartedAt = now
                state.startYaw = player.yRot
                state.startPitch = player.xRot
            }

            val duration = delaySetting.value.toLong().coerceAtLeast(0L)
            val progress = if (duration == 0L) 1.0 else ((now - state.rotateStartedAt).toDouble() / duration.toDouble()).coerceIn(0.0, 1.0)
            val (targetYaw, targetPitch) = yawPitchTo(player, targetLook)
            player.yRot = lerpAngle(state.startYaw, targetYaw, progress.toFloat())
            player.xRot = lerp(state.startPitch, targetPitch, progress.toFloat())
            player.yHeadRot = player.yRot
            player.yBodyRot = player.yRot

            if (progress < 1.0) {
                return
            }
        }

        if (!buttonPresent[buttonIndex]) {
            if (state.waitingSince == 0L) {
                state.waitingSince = now
            }
            if (now - state.waitingSince < BUTTON_WAIT_TIMEOUT_MS) {
                return
            }
        }

        if (pressButton(buttonPositions[buttonIndex], noSwingSetting.value)) {
            state.index += 1
            state.rotateStartedAt = 0L
            state.waitingSince = 0L
            state.lastClickAt = now
        } else {
            automation = null
        }
    }

    private fun finishAutomation(cycleNumber: Int) {
        automation = null
        if (cycleNumber > completedCycles) {
            completedCycles = cycleNumber
            if (announceProgressSetting.value) {
                sendPartyProgress(cycleNumber)
            }
        }
    }

    private fun sendPartyProgress(cycleNumber: Int) {
        if (cycleNumber !in 1..TOTAL_CYCLES) return
        Minecraft.getInstance().player?.connection?.sendCommand("pc [${LarpBranding.BRAND_NAME}] SS $cycleNumber/$TOTAL_CYCLES")
    }

    private fun handleAutoStart(player: LocalPlayer) {
        if (pendingAutoStarts.isEmpty()) return
        val now = System.currentTimeMillis()
        val iterator = pendingAutoStarts.iterator()
        while (iterator.hasNext()) {
            val triggerAt = iterator.next()
            if (triggerAt > now) continue
            if (isAtSS(player) && (noRotateSetting.value || isLookingClose(player, startButtonLook))) {
                pressButton(startButtonPos, noSwingSetting.value)
            }
            iterator.remove()
        }
    }

    private fun scheduleAutoStart() {
        pendingAutoStarts.clear()
        val raw = autoStartSetting.text.trim()
        if (raw.isEmpty()) {
            return
        }

        val delays = raw.split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { it.toLongOrNull() }

        if (delays.isEmpty() || delays.any { it == null }) {
            LarpChat.send("Auto SS: invalid Auto Start configuration.")
            return
        }

        val now = System.currentTimeMillis()
        delays.filterNotNull().forEach { pendingAutoStarts += now + it }
    }

    private fun tryResetFromStartButton() {
        val now = System.currentTimeMillis()
        if (now - lastResetAt < RESET_COOLDOWN_MS) {
            return
        }

        clearPuzzle()
        lastResetAt = now
    }

    private fun clearState(clearSnapshot: Boolean) {
        clearPuzzle()
        pendingAutoStarts.clear()
        if (clearSnapshot) {
            snapshotReady = false
            buttonPresent.fill(false)
            obsidianLit.fill(false)
            startButtonPresent = false
            lastIndicatorPresent = false
        }
    }

    private fun clearPuzzle() {
        phase = 0
        prophet = 0
        completedCycles = 0
        solution.clear()
        prophecy.clear()
        automation = null
        lastIndicatorPresent = false
    }

    private fun primeSnapshot(level: net.minecraft.client.multiplayer.ClientLevel) {
        for (index in buttonPositions.indices) {
            buttonPresent[index] = isSimonButton(level.getBlockState(buttonPositions[index]).block)
        }
        for (index in obsidianPositions.indices) {
            obsidianLit[index] = level.getBlockState(obsidianPositions[index]).block == Blocks.SEA_LANTERN
        }
        startButtonPresent = isSimonButton(level.getBlockState(startButtonPos).block)
        lastIndicatorPresent = firstMissingProphecyIndex()?.let { buttonPresent[it] } ?: false
    }

    private fun firstMissingProphecyIndex(): Int? {
        return buttonPositions.indices.firstOrNull { index -> index !in prophecy }
    }

    private fun isAtSS(player: LocalPlayer): Boolean {
        return player.position().distanceTo(stationPosition) < 1.0
    }

    private fun isLookingClose(player: LocalPlayer, target: Vec3): Boolean {
        val (targetYaw, targetPitch) = yawPitchTo(player, target)
        return abs(wrapDegrees(player.yRot - targetYaw)) < 5f && abs(player.xRot - targetPitch) < 5f
    }

    private fun pressButton(pos: BlockPos, noSwing: Boolean): Boolean {
        val client = Minecraft.getInstance()
        val player = client.player ?: return false
        val gameMode = client.gameMode ?: return false
        val connection = client.connection ?: return false

        connection.send(ServerboundMovePlayerPacket.Rot(player.yRot, player.xRot, player.onGround(), player.horizontalCollision))
        val side = clickSide(client.gameRenderer.mainCamera.position(), Vec3.atCenterOf(pos))
        val hit = BlockHitResult(Vec3.atCenterOf(pos), side, pos, false)
        val result = gameMode.useItemOn(player, InteractionHand.MAIN_HAND, hit)
        if (result.consumesAction() && !noSwing) {
            player.swing(InteractionHand.MAIN_HAND)
        }
        return result.consumesAction()
    }

    private fun isSimonButton(block: net.minecraft.world.level.block.Block): Boolean = block is ButtonBlock

    private fun clickSide(camera: Vec3, center: Vec3): Direction {
        val dx = camera.x - center.x
        val dy = camera.y - center.y
        val dz = camera.z - center.z

        return when {
            abs(dx) >= abs(dy) && abs(dx) >= abs(dz) -> if (dx >= 0.0) Direction.EAST else Direction.WEST
            abs(dz) >= abs(dx) && abs(dz) >= abs(dy) -> if (dz >= 0.0) Direction.SOUTH else Direction.NORTH
            else -> if (dy >= 0.0) Direction.UP else Direction.DOWN
        }
    }

    private fun yawPitchTo(player: LocalPlayer, target: Vec3): Pair<Float, Float> {
        val eye = player.eyePosition
        val dx = target.x - eye.x
        val dy = target.y - eye.y
        val dz = target.z - eye.z
        val horizontal = sqrt(dx * dx + dz * dz)
        val yaw = Math.toDegrees(atan2(dz, dx)).toFloat() - 90f
        val pitch = -Math.toDegrees(atan2(dy, horizontal)).toFloat()
        return yaw to pitch
    }

    private fun lerp(start: Float, end: Float, progress: Float): Float {
        return start + (end - start) * progress
    }

    private fun lerpAngle(start: Float, end: Float, progress: Float): Float {
        return start + wrapDegrees(end - start) * progress
    }

    private fun wrapDegrees(value: Float): Float {
        var wrapped = value % 360f
        if (wrapped >= 180f) wrapped -= 360f
        if (wrapped < -180f) wrapped += 360f
        return wrapped
    }
}
