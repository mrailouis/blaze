package me.mrai.larpclient.ui.hud

import me.mrai.larpclient.features.impl.dungeons.f7.p1.maxorhphud.MaxorHpHudModule
import me.mrai.larpclient.ui.font.HudTextRenderer
import net.minecraft.client.gui.GuiGraphicsExtractor

object MaxorHpHudRenderer {
    fun render(graphics: GuiGraphicsExtractor) {
        if (!MaxorHpHudModule.enabled) return
        val text = MaxorHpHudModule.getDisplayText() ?: return
        val rendered = if (MaxorHpHudModule.bold.value) "§l$text" else text
        HudTextRenderer.drawScaledText(
            graphics,
            rendered,
            HudPositionManager.config.maxorHpHud.x,
            HudPositionManager.config.maxorHpHud.y,
            MaxorHpHudModule.getColor(),
            HudPositionManager.config.maxorHpHudScale,
            false
        )
    }

    fun renderPreview(graphics: GuiGraphicsExtractor) {
        HudTextRenderer.drawScaledText(
            graphics,
            getPreviewText(),
            HudPositionManager.config.maxorHpHud.x,
            HudPositionManager.config.maxorHpHud.y,
            MaxorHpHudModule.getColor(),
            HudPositionManager.config.maxorHpHudScale,
            false
        )
    }

    fun getPreviewText(): String {
        val base = "25.2%"
        return if (MaxorHpHudModule.bold.value) "§l$base" else base
    }

    fun getWidth(): Float = HudTextRenderer.width(getPreviewText(), HudPositionManager.config.maxorHpHudScale).toFloat()
    fun getHeight(): Float = HudTextRenderer.lineHeight(HudPositionManager.config.maxorHpHudScale, 0f).toFloat()
}
