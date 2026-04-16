package me.mrai.larpclient.ui.hud

import me.mrai.larpclient.features.impl.kuudra.p4.kuudradirection.KuudraDirectionModule
import me.mrai.larpclient.ui.font.HudTextRenderer
import net.minecraft.client.gui.GuiGraphicsExtractor

object KuudraDirectionHudRenderer {
    fun render(graphics: GuiGraphicsExtractor) {
        if (!KuudraDirectionModule.shouldRender()) return
        HudTextRenderer.drawScaledText(
            graphics,
            KuudraDirectionModule.getDisplayText(),
            HudPositionManager.config.kuudraDirectionHud.x,
            HudPositionManager.config.kuudraDirectionHud.y,
            KuudraDirectionModule.getColor(),
            HudPositionManager.config.kuudraDirectionHudScale,
            false
        )
    }

    fun renderPreview(graphics: GuiGraphicsExtractor) {
        HudTextRenderer.drawScaledText(
            graphics,
            "§lFRONT!",
            HudPositionManager.config.kuudraDirectionHud.x,
            HudPositionManager.config.kuudraDirectionHud.y,
            0xFF55FF55.toInt(),
            HudPositionManager.config.kuudraDirectionHudScale,
            false
        )
    }

    fun getWidth(): Float = HudTextRenderer.width("§lFRONT!", HudPositionManager.config.kuudraDirectionHudScale).toFloat()
    fun getHeight(): Float = HudTextRenderer.lineHeight(HudPositionManager.config.kuudraDirectionHudScale, 0f).toFloat()
}
