package me.mrai.larpclient.ui.hud

import me.mrai.larpclient.features.impl.skyblock.general.blink.BlinkModule
import me.mrai.larpclient.ui.font.HudTextRenderer
import net.minecraft.client.gui.GuiGraphicsExtractor

object BlinkHudRenderer {
    private const val COLOR_TEXT = 0xFFFFFFFF.toInt()
    private const val COLOR_BOX = 0x22000000
    private const val COLOR_OUTLINE = 0x66FFFFFF

    fun render(graphics: GuiGraphicsExtractor) {
        if (!BlinkModule.shouldShowBlinkHud()) return
        draw(graphics, BlinkModule.getBlinkHudText())
    }

    fun renderPreview(graphics: GuiGraphicsExtractor) {
        draw(graphics, BlinkModule.getBlinkHudText(preview = true))
    }

    fun getWidth(preview: Boolean = false): Float {
        val scale = HudPositionManager.config.blinkHudScale
        return HudTextRenderer.width(BlinkModule.getBlinkHudText(preview), scale).toFloat() + 8f * scale
    }

    fun getHeight(): Float {
        val scale = HudPositionManager.config.blinkHudScale
        return HudTextRenderer.lineHeight(scale).toFloat() + 6f * scale
    }

    private fun draw(graphics: GuiGraphicsExtractor, text: String) {
        val scale = HudPositionManager.config.blinkHudScale
        val x = HudPositionManager.config.blinkHud.x
        val y = HudPositionManager.config.blinkHud.y
        val textWidth = HudTextRenderer.width(text, scale).toFloat()
        val textHeight = HudTextRenderer.lineHeight(scale).toFloat()
        val padX = 4f * scale
        val padY = 3f * scale

        graphics.fill(
            (x - padX).toInt(),
            (y - padY).toInt(),
            (x + textWidth + padX).toInt(),
            (y + textHeight + padY).toInt(),
            COLOR_BOX
        )
        drawOutline(
            graphics,
            (x - padX).toInt(),
            (y - padY).toInt(),
            (textWidth + padX * 2f).toInt(),
            (textHeight + padY * 2f).toInt()
        )

        HudTextRenderer.drawScaledText(graphics, text, x, y, COLOR_TEXT, scale, false)
    }

    private fun drawOutline(graphics: GuiGraphicsExtractor, x: Int, y: Int, width: Int, height: Int) {
        graphics.fill(x, y, x + width, y + 1, COLOR_OUTLINE)
        graphics.fill(x, y + height - 1, x + width, y + height, COLOR_OUTLINE)
        graphics.fill(x, y, x + 1, y + height, COLOR_OUTLINE)
        graphics.fill(x + width - 1, y, x + width, y + height, COLOR_OUTLINE)
    }
}
