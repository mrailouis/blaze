package me.mrai.larpclient.features.impl.dungeons.f7.predev.arrowalign

import me.mrai.larpclient.integration.AddonAutomationAccess
import me.mrai.larpclient.module.BoolSetting
import me.mrai.larpclient.module.Module
import me.mrai.larpclient.module.ModuleCategory
import me.mrai.larpclient.module.shownWhen
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.decoration.ItemFrame
import net.minecraft.world.phys.EntityHitResult

object ArrowAlignModule : Module(
    name = "Arrow Align",
    description = "Shows the required item-frame rotations for Arrow Align and can auto-rotate the looked-at frame when addon automation is available.",
    category = ModuleCategory.DUNGEONS_F7_PREDEV
) {
    private const val HOVER_COOLDOWN_TICKS = 2
    private const val MAX_FRAME_DISTANCE_SQ = 25.0

    private val showSolverSetting = BoolSetting("Show Solver", true)
    private val hoverSolverSetting = BoolSetting("Hover Solver", false)
        .shownWhen { showAutomationSettings }
    private val showAutomationSettings: Boolean
        get() = AddonAutomationAccess.hasAddonFeatures()
    private val automationAllowed: Boolean
        get() = AddonAutomationAccess.isAutomationEnabled()

    private val predictedRotations = IntArray(25) { -1 }
    private val recentClickTimes = LongArray(25)
    private var hoverCooldown = 0

    init {
        settings += showSolverSetting
        settings += hoverSolverSetting
    }

    override fun onEnable() {
        hoverCooldown = 0
    }

    override fun onDisable() {
        hoverCooldown = 0
    }

    override fun onTick() {
        if (hoverCooldown > 0) {
            hoverCooldown--
        }
        if (!enabled || !automationAllowed || !hoverSolverSetting.value) return

        val client = Minecraft.getInstance()
        if (client.screen != null || hoverCooldown > 0) return

        val player = client.player ?: return
        val level = client.level ?: return
        val gameMode = client.gameMode ?: return
        if (!ArrowAlignSolver.isNearDevice(player.position())) return

        val hit = client.hitResult as? EntityHitResult ?: return
        val frame = hit.entity as? ItemFrame ?: return

        val snapshot = ArrowAlignSolver.buildSnapshot(level, predictedRotations, recentClickTimes) ?: return
        val target = snapshot.frames.firstOrNull { it.frame.id == frame.id } ?: return
        if (target.clicksNeeded <= 0) return
        if (player.eyePosition.distanceToSqr(frame.position()) > MAX_FRAME_DISTANCE_SQ) return

        val result = gameMode.interact(player, frame, hit, InteractionHand.MAIN_HAND)
        if (result.consumesAction()) {
            player.swing(InteractionHand.MAIN_HAND)
            predictedRotations[target.index] = (target.currentRotation + 1) % 8
            recentClickTimes[target.index] = System.currentTimeMillis()
            hoverCooldown = HOVER_COOLDOWN_TICKS
        }
    }

    fun onWorldChange() {
        hoverCooldown = 0
        for (index in predictedRotations.indices) {
            predictedRotations[index] = -1
            recentClickTimes[index] = 0L
        }
    }

    fun render(context: LevelRenderContext) {
        if (!enabled || !showSolverSetting.value) return

        val client = Minecraft.getInstance()
        val player = client.player ?: return
        val level = client.level ?: return
        if (!ArrowAlignSolver.isNearDevice(player.position())) return

        val snapshot = ArrowAlignSolver.buildSnapshot(level, predictedRotations, recentClickTimes) ?: return
        if (snapshot.frames.isEmpty()) return

        val camera = client.gameRenderer.mainCamera
        val font = client.font
        val buffer = client.renderBuffers().bufferSource()
        val matrices = context.poseStack()

        for (frame in snapshot.frames) {
            val text = frame.clicksNeeded.toString()
            val color = if (frame.clicksNeeded == 0) 0xFF55FF55.toInt() else 0xFFFFAA00.toInt()
            val pos = frame.frame.position().add(0.0, 0.55, 0.0)

            matrices.pushPose()
            matrices.translate(pos.x - camera.position().x, pos.y - camera.position().y, pos.z - camera.position().z)
            matrices.mulPose(camera.rotation())
            val scale = 0.05f
            matrices.scale(scale, -scale, scale)

            val width = font.width(text)
            font.drawInBatch(
                text,
                -width / 2f,
                0f,
                color,
                true,
                matrices.last().pose(),
                buffer,
                Font.DisplayMode.SEE_THROUGH,
                0,
                15728880
            )
            matrices.popPose()
        }

        buffer.endBatch()
    }
}
