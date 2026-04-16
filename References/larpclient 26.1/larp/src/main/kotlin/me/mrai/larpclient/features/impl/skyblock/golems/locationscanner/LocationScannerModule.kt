package me.mrai.larpclient.features.impl.skyblock.golems.locationscanner

import me.mrai.larpclient.features.impl.skyblock.golems.GolemTrackerState
import me.mrai.larpclient.module.BoolSetting
import me.mrai.larpclient.module.Module
import me.mrai.larpclient.module.ModuleCategory
import me.mrai.larpclient.module.SliderSetting
import me.mrai.larpclient.render.WorldBoxRenderer
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.world.phys.AABB

object LocationScannerModule : Module(
    name = "Location Scanner",
    description = "Finds the End Stone Protector location and renders it in-world.",
    category = ModuleCategory.SKYBLOCK_GOLEMS
) {
    private val showScannerText = BoolSetting("Show Scanner Text", true)
    private val scanRadius = SliderSetting("Scan Radius", 160.0, 16.0, 320.0, 1.0)
    private val showBeaconBeam = BoolSetting("Show Beacon Beam", true)

    init {
        settings += listOf(showScannerText, scanRadius, showBeaconBeam)
    }

    fun render(context: LevelRenderContext) {
        if (!enabled) return

        val client = Minecraft.getInstance()
        val camera = client.gameRenderer.mainCamera
        val font = client.font
        val marker = GolemTrackerState.getLocationMarker() ?: return
        val label = GolemTrackerState.getLocationName() ?: return
        val player = client.player ?: return

        if (player.position().distanceTo(marker) > scanRadius.value) return

        val beamColor = if (GolemTrackerState.isStageFour()) {
            WorldBoxRenderer.BoxStyle(
                fillRed = 0.15f,
                fillGreen = 0.95f,
                fillBlue = 0.28f,
                fillAlpha = 0.38f,
                outlineRed = 0.35f,
                outlineGreen = 1.0f,
                outlineBlue = 0.45f,
                outlineAlpha = 0.9f
            )
        } else {
            WorldBoxRenderer.BoxStyle(
                fillRed = 1.0f,
                fillGreen = 0.18f,
                fillBlue = 0.18f,
                fillAlpha = 0.38f,
                outlineRed = 1.0f,
                outlineGreen = 0.4f,
                outlineBlue = 0.4f,
                outlineAlpha = 0.9f
            )
        }

        if (showBeaconBeam.value && player.position().distanceTo(marker) > 20.0) {
            val beam = AABB(
                marker.x - 0.08,
                marker.y,
                marker.z - 0.08,
                marker.x + 0.08,
                marker.y + 80.0,
                marker.z + 0.08
            )
            WorldBoxRenderer.render(context, listOf(beam to beamColor), depthless = true)
        }

        if (!showScannerText.value) return

        val matrices = context.poseStack()
        val buffer = client.renderBuffers().bufferSource()
        matrices.pushPose()
        matrices.translate(marker.x - camera.position().x, marker.y + 20.0 - camera.position().y, marker.z - camera.position().z)
        matrices.mulPose(camera.rotation())
        val scale = 0.08f
        matrices.scale(scale, -scale, scale)

        val text = "§d§l$label §7(${GolemTrackerState.getLobbyStageName()})"
        val width = font.width(text)
        font.drawInBatch(
            text,
            -width / 2f,
            0f,
            0xFFFF55FF.toInt(),
            true,
            matrices.last().pose(),
            buffer,
            Font.DisplayMode.SEE_THROUGH,
            0,
            15728880
        )

        buffer.endBatch()
        matrices.popPose()
    }
}
