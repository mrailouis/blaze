package me.mrai.larpclient.ui.hud

import me.mrai.larpclient.features.impl.dungeons.general.lastbreathutils.LastBreathUtilsModule
import me.mrai.larpclient.ui.font.HudTextRenderer
import net.minecraft.client.gui.GuiGraphicsExtractor

object LastBreathUtilsHudRenderer {
    private const val DISPLAY_TICKS = 50
    private const val PREVIEW_COLOR = 0xFF55FF55.toInt()

    private var text: String? = null
    private var timer = 0

    fun showHits(count: Int) {
        text = "$count LB's hit!"
        timer = DISPLAY_TICKS
    }

    fun render(graphics: GuiGraphicsExtractor) {
        if (!LastBreathUtilsModule.enabled) return
        if (timer <= 0) return
        val display = text ?: return

        val x = HudPositionManager.config.lastBreathUtilsTitle.x
        val y = HudPositionManager.config.lastBreathUtilsTitle.y
        val scale = HudPositionManager.config.lastBreathUtilsTitleScale
        val rendered = if (LastBreathUtilsModule.titleBold.value) "§l$display" else display

        HudTextRenderer.drawScaledText(graphics, rendered, x, y, 0xFF55FF55.toInt(), scale, false)
        timer--
    }

    fun renderPreview(graphics: GuiGraphicsExtractor) {
        val x = HudPositionManager.config.lastBreathUtilsTitle.x
        val y = HudPositionManager.config.lastBreathUtilsTitle.y
        val scale = HudPositionManager.config.lastBreathUtilsTitleScale
        HudTextRenderer.drawScaledText(graphics, getPreviewText(), x, y, PREVIEW_COLOR, scale, false)
    }

    fun getPreviewText(): String = if (LastBreathUtilsModule.titleBold.value) "§l3 LB's hit!" else "3 LB's hit!"
    fun getWidth(): Float = HudTextRenderer.width(getPreviewText(), HudPositionManager.config.lastBreathUtilsTitleScale).toFloat()
    fun getHeight(): Float = HudTextRenderer.lineHeight(HudPositionManager.config.lastBreathUtilsTitleScale, 0f).toFloat()
}
