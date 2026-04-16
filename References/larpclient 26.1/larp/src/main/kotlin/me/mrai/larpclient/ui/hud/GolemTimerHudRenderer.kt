package me.mrai.larpclient.ui.hud

import me.mrai.larpclient.features.impl.skyblock.golems.spawntimer.SpawnTimerModule
import me.mrai.larpclient.ui.font.HudTextRenderer
import net.minecraft.client.gui.GuiGraphicsExtractor

object GolemTimerHudRenderer {
    fun render(graphics: GuiGraphicsExtractor) {
        if (!SpawnTimerModule.enabled) return
        val text = SpawnTimerModule.getDisplayText() ?: return
        HudTextRenderer.drawScaledText(
            graphics,
            text,
            HudPositionManager.config.golemTimerHud.x,
            HudPositionManager.config.golemTimerHud.y,
            SpawnTimerModule.getDisplayColor(),
            HudPositionManager.config.golemTimerHudScale,
            false
        )
    }

    fun renderPreview(graphics: GuiGraphicsExtractor) {
        HudTextRenderer.drawScaledText(
            graphics,
            getPreviewText(),
            HudPositionManager.config.golemTimerHud.x,
            HudPositionManager.config.golemTimerHud.y,
            0xFF55FFFF.toInt(),
            HudPositionManager.config.golemTimerHudScale,
            false
        )
    }

    fun getPreviewText(): String = "+20.0"

    fun getWidth(): Float = HudTextRenderer.width(getPreviewText(), HudPositionManager.config.golemTimerHudScale).toFloat()

    fun getHeight(): Float = HudTextRenderer.lineHeight(HudPositionManager.config.golemTimerHudScale, 0f).toFloat()
}
