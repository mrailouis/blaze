package me.mrai.larpclient.ui.hud

import me.mrai.larpclient.features.impl.kuudra.p1.kuudrawaypoints.KuudraWaypointModule
import me.mrai.larpclient.ui.font.HudTextRenderer
import net.minecraft.client.gui.GuiGraphicsExtractor
import kotlin.math.max

object KuudraPriorityHudRenderer {
    private const val LINE_GAP = 1f

    fun render(graphics: GuiGraphicsExtractor) {
        if (!KuudraWaypointModule.shouldShowPriorityHud()) return
        draw(graphics, KuudraWaypointModule.getPriorityHudSnapshot())
    }

    fun renderPreview(graphics: GuiGraphicsExtractor) {
        draw(graphics, previewSnapshot())
    }

    fun getWidth(preview: Boolean = false): Float {
        val snapshot = if (preview) previewSnapshot() else KuudraWaypointModule.getPriorityHudSnapshot()
        val scale = HudPositionManager.config.kuudraPriorityScale
        val headerWidth = HudTextRenderer.width("§l${snapshot.title}", scale).toFloat()
        val preWidth = HudTextRenderer.width(snapshot.preLine, scale).toFloat()
        val goToWidth = HudTextRenderer.width(snapshot.goToLine, scale).toFloat()
        return max(headerWidth, max(preWidth, goToWidth))
    }

    fun getHeight(): Float {
        val lineHeight = HudTextRenderer.lineHeight(HudPositionManager.config.kuudraPriorityScale).toFloat()
        return lineHeight * 3f + LINE_GAP * 2f
    }

    private fun draw(graphics: GuiGraphicsExtractor, snapshot: KuudraWaypointModule.PriorityHudSnapshot) {
        val x = HudPositionManager.config.kuudraPriority.x
        val y = HudPositionManager.config.kuudraPriority.y
        val scale = HudPositionManager.config.kuudraPriorityScale
        val lineHeight = HudTextRenderer.lineHeight(scale).toFloat()

        HudTextRenderer.drawScaledText(graphics, "§l${snapshot.title}", x, y, 0xFFFFFFFF.toInt(), scale, false)
        HudTextRenderer.drawScaledText(graphics, snapshot.preLine, x, y + lineHeight + LINE_GAP, 0xFF55FFFF.toInt(), scale, false)
        HudTextRenderer.drawScaledText(graphics, snapshot.goToLine, x, y + (lineHeight + LINE_GAP) * 2f, 0xFF55FFFF.toInt(), scale, false)
    }

    private fun previewSnapshot(): KuudraWaypointModule.PriorityHudSnapshot {
        return if (KuudraWaypointModule.shouldShowPriorityHud()) {
            KuudraWaypointModule.getPriorityHudSnapshot()
        } else {
            KuudraWaypointModule.getPriorityHudSnapshot(preview = true)
        }
    }
}
