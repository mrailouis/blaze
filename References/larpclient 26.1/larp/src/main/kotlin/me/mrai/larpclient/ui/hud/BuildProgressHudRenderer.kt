package me.mrai.larpclient.ui.hud

import me.mrai.larpclient.features.impl.kuudra.p2.buildprogress.BuildProgressDisplayModule
import me.mrai.larpclient.ui.font.HudTextRenderer
import net.minecraft.client.gui.GuiGraphicsExtractor

object BuildProgressHudRenderer {
    fun render(graphics: GuiGraphicsExtractor) {
        if (!BuildProgressDisplayModule.enabled) return
        val text = BuildProgressDisplayModule.getDisplayText() ?: return
        val rendered = if (BuildProgressDisplayModule.isBold()) "§l$text" else text
        HudTextRenderer.drawScaledText(
            graphics,
            rendered,
            HudPositionManager.config.buildProgressHud.x,
            HudPositionManager.config.buildProgressHud.y,
            BuildProgressDisplayModule.getColor(),
            HudPositionManager.config.buildProgressHudScale,
            false
        )
    }

    fun renderPreview(graphics: GuiGraphicsExtractor) {
        HudTextRenderer.drawScaledText(
            graphics,
            getPreviewText(),
            HudPositionManager.config.buildProgressHud.x,
            HudPositionManager.config.buildProgressHud.y,
            BuildProgressDisplayModule.getColor(),
            HudPositionManager.config.buildProgressHudScale,
            false
        )
    }

    fun getPreviewText(): String {
        val base = "Build: 68%"
        return if (BuildProgressDisplayModule.isBold()) "§l$base" else base
    }

    fun getWidth(): Float = HudTextRenderer.width(getPreviewText(), HudPositionManager.config.buildProgressHudScale).toFloat()
    fun getHeight(): Float = HudTextRenderer.lineHeight(HudPositionManager.config.buildProgressHudScale, 0f).toFloat()
}
