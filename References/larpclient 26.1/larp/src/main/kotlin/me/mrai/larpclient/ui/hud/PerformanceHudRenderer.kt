package me.mrai.larpclient.ui.hud

import me.mrai.larpclient.features.impl.skyblock.general.performancehud.PerformanceHudModule
import me.mrai.larpclient.features.impl.skyblock.general.performancehud.PerformanceStatsTracker
import me.mrai.larpclient.ui.font.HudTextRenderer
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import java.util.Locale

object PerformanceHudRenderer {
    private data class Metric(
        val label: String,
        val value: String
    )

    fun render(graphics: GuiGraphicsExtractor) {
        if (!PerformanceHudModule.enabled) return
        draw(graphics, preview = false)
    }

    fun renderPreview(graphics: GuiGraphicsExtractor) {
        draw(graphics, preview = true)
    }

    fun getWidth(preview: Boolean = true): Float {
        val (width, _) = measure(preview)
        return width.toFloat() * HudPositionManager.config.performanceHudScale
    }

    fun getHeight(preview: Boolean = true): Float {
        val (_, height) = measure(preview)
        return height.toFloat() * HudPositionManager.config.performanceHudScale
    }

    private fun draw(graphics: GuiGraphicsExtractor, preview: Boolean) {
        val metrics = metrics(preview)
        if (metrics.isEmpty()) return

        val pose = graphics.pose()
        pose.pushMatrix()
        pose.translate(
            HudPositionManager.config.performanceHud.x,
            HudPositionManager.config.performanceHud.y
        )
        pose.scale(
            HudPositionManager.config.performanceHudScale,
            HudPositionManager.config.performanceHudScale
        )

        var width = 1
        var height = 1
        val horizontal = PerformanceHudModule.isHorizontal()
        val lineHeight = Minecraft.getInstance().font.lineHeight

        for (metric in metrics) {
            val metricWidth = drawMetric(
                graphics = graphics,
                label = metric.label,
                value = metric.value,
                x = if (horizontal) width else 1,
                y = height
            )

            if (horizontal) {
                width += metricWidth
            } else {
                width = maxOf(width, metricWidth)
                height += lineHeight
            }
        }

        pose.popMatrix()
    }

    private fun measure(preview: Boolean): Pair<Int, Int> {
        val metrics = metrics(preview)
        if (metrics.isEmpty()) return 0 to 0

        var width = 1
        var height = 1
        val horizontal = PerformanceHudModule.isHorizontal()
        val lineHeight = Minecraft.getInstance().font.lineHeight

        for (metric in metrics) {
            val metricWidth = HudTextRenderer.width(metric.label) + HudTextRenderer.width(metric.value)
            if (horizontal) {
                width += metricWidth
            } else {
                width = maxOf(width, metricWidth)
                height += lineHeight
            }
        }

        return width to if (horizontal) lineHeight else height
    }

    private fun drawMetric(
        graphics: GuiGraphicsExtractor,
        label: String,
        value: String,
        x: Int,
        y: Int
    ): Int {
        val labelWidth = HudTextRenderer.width(label)
        HudTextRenderer.drawScaledText(
            graphics,
            label,
            x.toFloat(),
            y.toFloat(),
            PerformanceHudModule.nameColor(),
            1f,
            true
        )
        HudTextRenderer.drawScaledText(
            graphics,
            value,
            (x + labelWidth).toFloat(),
            y.toFloat(),
            PerformanceHudModule.valueColor(),
            1f,
            true
        )
        return labelWidth + HudTextRenderer.width(value)
    }

    private fun metrics(preview: Boolean): List<Metric> {
        if (!preview) {
            PerformanceStatsTracker.refreshPingAverage()
        }

        val items = mutableListOf<Metric>()
        if (PerformanceHudModule.showTPS.value) {
            val value = if (preview) "20.0 " else "${formatTps(PerformanceStatsTracker.averageTps)} "
            items += Metric("TPS: ", value)
        }
        if (PerformanceHudModule.showFPS.value) {
            val value = if (preview) "694 " else "${Minecraft.getInstance().fps} "
            items += Metric("FPS: ", value)
        }
        if (PerformanceHudModule.showPing.value) {
            val value = if (preview) "42ms " else "${PerformanceStatsTracker.averagePing}ms "
            items += Metric("Ping: ", value)
        }
        return items
    }

    private fun formatTps(value: Float): String = String.format(Locale.US, "%.1f", value)
}
