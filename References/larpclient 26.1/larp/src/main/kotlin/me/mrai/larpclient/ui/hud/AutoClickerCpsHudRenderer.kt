package me.mrai.larpclient.ui.hud

import me.mrai.larpclient.features.impl.skyblock.general.autoclicker.AutoClickerModule
import me.mrai.larpclient.ui.font.HudTextRenderer
import net.minecraft.client.gui.GuiGraphicsExtractor

object AutoClickerCpsHudRenderer {
    private const val COLOR_AQUA = 0xFF1EE3E7.toInt()
    private const val COLOR_PINK = 0xFFFF5CD1.toInt()
    private const val COLOR_GRAY = 0xFF9A9A9A.toInt()
    private const val COLOR_WHITE = 0xFFFFFFFF.toInt()
    private const val COLOR_BOX = 0x22000000
    private const val COLOR_OUTLINE = 0x44FFFFFF
    private const val PREVIEW_CPS = 20

    fun render(graphics: GuiGraphicsExtractor) {
        if (!AutoClickerModule.enabled) return
        draw(graphics, AutoClickerModule.getLiveCps())
    }

    fun renderPreview(graphics: GuiGraphicsExtractor) {
        draw(graphics, PREVIEW_CPS)
    }

    fun getWidth(preview: Boolean = false): Float {
        val cps = if (preview) PREVIEW_CPS else AutoClickerModule.getLiveCps()
        val scale = HudPositionManager.config.autoClickerCpsScale

        return HudTextRenderer.width("C", scale).toFloat() +
            HudTextRenderer.width("P", scale).toFloat() +
            HudTextRenderer.width("S", scale).toFloat() +
            HudTextRenderer.width(" » ", scale).toFloat() +
            HudTextRenderer.width(cps.toString(), scale).toFloat() +
            8f * scale
    }

    fun getHeight(): Float {
        val scale = HudPositionManager.config.autoClickerCpsScale
        return HudTextRenderer.lineHeight(scale).toFloat() + 6f * scale
    }

    private fun draw(graphics: GuiGraphicsExtractor, cps: Int) {
        val scale = HudPositionManager.config.autoClickerCpsScale

        val x = HudPositionManager.config.autoClickerCps.x
        val y = HudPositionManager.config.autoClickerCps.y

        val c = "C"
        val p = "P"
        val s = "S"
        val chevrons = " » "
        val number = cps.toString()

        val totalWidth =
            HudTextRenderer.width(c, scale).toFloat() +
                HudTextRenderer.width(p, scale).toFloat() +
                HudTextRenderer.width(s, scale).toFloat() +
                HudTextRenderer.width(chevrons, scale).toFloat() +
                HudTextRenderer.width(number, scale).toFloat()

        val totalHeight = HudTextRenderer.lineHeight(scale).toFloat()
        val padX = 4f * scale
        val padY = 3f * scale

        graphics.fill(
            (x - padX).toInt(),
            (y - padY).toInt(),
            (x + totalWidth + padX).toInt(),
            (y + totalHeight + padY).toInt(),
            COLOR_BOX
        )

        drawOutline(
            graphics = graphics,
            x = (x - padX).toInt(),
            y = (y - padY).toInt(),
            width = (totalWidth + padX * 2f).toInt(),
            height = (totalHeight + padY * 2f).toInt()
        )

        var drawX = x

        HudTextRenderer.drawScaledText(graphics, c, drawX, y, COLOR_AQUA, scale, false)
        drawX += HudTextRenderer.width(c, scale)

        HudTextRenderer.drawScaledText(graphics, p, drawX, y, COLOR_PINK, scale, false)
        drawX += HudTextRenderer.width(p, scale)

        HudTextRenderer.drawScaledText(graphics, s, drawX, y, COLOR_AQUA, scale, false)
        drawX += HudTextRenderer.width(s, scale)

        HudTextRenderer.drawScaledText(graphics, chevrons, drawX, y, COLOR_GRAY, scale, false)
        drawX += HudTextRenderer.width(chevrons, scale)

        HudTextRenderer.drawScaledText(graphics, number, drawX, y, COLOR_WHITE, scale, false)
    }

    private fun drawOutline(
        graphics: GuiGraphicsExtractor,
        x: Int,
        y: Int,
        width: Int,
        height: Int
    ) {
        graphics.fill(x, y, x + width, y + 1, COLOR_OUTLINE)
        graphics.fill(x, y + height - 1, x + width, y + height, COLOR_OUTLINE)
        graphics.fill(x, y, x + 1, y + height, COLOR_OUTLINE)
        graphics.fill(x + width - 1, y, x + width, y + height, COLOR_OUTLINE)
    }
}
