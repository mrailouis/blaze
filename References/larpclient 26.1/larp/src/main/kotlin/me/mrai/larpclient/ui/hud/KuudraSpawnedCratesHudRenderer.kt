package me.mrai.larpclient.ui.hud

import me.mrai.larpclient.features.impl.kuudra.p1.kuudrawaypoints.KuudraWaypointModule
import me.mrai.larpclient.ui.font.HudTextRenderer
import net.minecraft.client.gui.GuiGraphicsExtractor
import kotlin.math.max

object KuudraSpawnedCratesHudRenderer {
    private const val LINE_GAP = 1f
    private const val STATUS_GAP = 4f
    private const val LABEL_COLOR = 0xFF00AAAA.toInt()
    private const val CHECK_COLOR = 0xFF55FF55.toInt()
    private const val CROSS_COLOR = 0xFFFF5555.toInt()
    private const val CHECK_SYMBOL = "✓"
    private const val CROSS_SYMBOL = "✗"

    fun render(graphics: GuiGraphicsExtractor) {
        if (!KuudraWaypointModule.shouldShowSpawnedCratesHud()) return
        draw(graphics, KuudraWaypointModule.getSpawnedCratesHudSnapshot())
    }

    fun renderPreview(graphics: GuiGraphicsExtractor) {
        draw(graphics, previewSnapshot())
    }

    fun getWidth(preview: Boolean = false): Float {
        val snapshot = if (preview) previewSnapshot() else KuudraWaypointModule.getSpawnedCratesHudSnapshot()
        val scale = HudPositionManager.config.kuudraSpawnedCratesScale
        return snapshot.entries.fold(0f) { widest, entry ->
            val labelWidth = HudTextRenderer.width("§l${entry.label}:", scale).toFloat()
            val statusWidth = HudTextRenderer.width(if (entry.spawned) CHECK_SYMBOL else CROSS_SYMBOL, scale).toFloat()
            max(widest, labelWidth + statusWidth + STATUS_GAP * scale)
        }
    }

    fun getHeight(preview: Boolean = false): Float {
        val snapshot = if (preview) previewSnapshot() else KuudraWaypointModule.getSpawnedCratesHudSnapshot()
        val lineHeight = HudTextRenderer.lineHeight(HudPositionManager.config.kuudraSpawnedCratesScale).toFloat()
        return lineHeight * snapshot.entries.size + LINE_GAP * (snapshot.entries.size - 1).coerceAtLeast(0)
    }

    private fun draw(graphics: GuiGraphicsExtractor, snapshot: KuudraWaypointModule.SpawnedCratesHudSnapshot) {
        val x = HudPositionManager.config.kuudraSpawnedCrates.x
        val y = HudPositionManager.config.kuudraSpawnedCrates.y
        val scale = HudPositionManager.config.kuudraSpawnedCratesScale
        val lineHeight = HudTextRenderer.lineHeight(scale).toFloat()

        snapshot.entries.forEachIndexed { index, entry ->
            val drawY = y + index * (lineHeight + LINE_GAP)
            val label = "§l${entry.label}:"
            HudTextRenderer.drawScaledText(graphics, label, x, drawY, LABEL_COLOR, scale, false)
            val statusX = x + HudTextRenderer.width(label, scale).toFloat() + STATUS_GAP * scale
            HudTextRenderer.drawScaledText(
                graphics,
                if (entry.spawned) CHECK_SYMBOL else CROSS_SYMBOL,
                statusX,
                drawY,
                if (entry.spawned) CHECK_COLOR else CROSS_COLOR,
                scale,
                false
            )
        }
    }

    private fun previewSnapshot(): KuudraWaypointModule.SpawnedCratesHudSnapshot {
        return if (KuudraWaypointModule.shouldShowSpawnedCratesHud()) {
            KuudraWaypointModule.getSpawnedCratesHudSnapshot()
        } else {
            KuudraWaypointModule.getSpawnedCratesHudSnapshot(preview = true)
        }
    }
}
