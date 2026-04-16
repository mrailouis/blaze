package me.mrai.larpclient.ui.hud

import me.mrai.larpclient.features.impl.kuudra.p1.kuudrawaypoints.KuudraWaypointModule
import me.mrai.larpclient.ui.font.HudTextRenderer
import net.minecraft.client.gui.GuiGraphicsExtractor

object KuudraGoToHudRenderer {
    fun render(graphics: GuiGraphicsExtractor) {
        if (!KuudraWaypointModule.shouldShowPriorityHud()) return
        draw(graphics, KuudraWaypointModule.getPriorityHudSnapshot().goToLine)
    }

    fun renderPreview(graphics: GuiGraphicsExtractor) {
        val line = if (KuudraWaypointModule.shouldShowPriorityHud()) {
            KuudraWaypointModule.getPriorityHudSnapshot().goToLine
        } else {
            KuudraWaypointModule.getPriorityHudSnapshot(preview = true).goToLine
        }
        draw(graphics, line)
    }

    fun getWidth(preview: Boolean = false): Float {
        val line = if (preview) {
            if (KuudraWaypointModule.shouldShowPriorityHud()) {
                KuudraWaypointModule.getPriorityHudSnapshot().goToLine
            } else {
                KuudraWaypointModule.getPriorityHudSnapshot(preview = true).goToLine
            }
        } else {
            KuudraWaypointModule.getPriorityHudSnapshot().goToLine
        }
        return HudTextRenderer.width("§l${formatLine(line)}", HudPositionManager.config.kuudraGoToScale).toFloat()
    }

    fun getHeight(): Float {
        return HudTextRenderer.lineHeight(HudPositionManager.config.kuudraGoToScale).toFloat()
    }

    private fun draw(graphics: GuiGraphicsExtractor, line: String) {
        HudTextRenderer.drawScaledText(
            graphics,
            "§l${formatLine(line)}",
            HudPositionManager.config.kuudraGoTo.x,
            HudPositionManager.config.kuudraGoTo.y,
            0xFF55FFFF.toInt(),
            HudPositionManager.config.kuudraGoToScale,
            false
        )
    }

    private fun formatLine(line: String): String {
        return line.replaceFirst("Go to:", "Go To").replaceFirst("Go to", "Go To")
    }
}
