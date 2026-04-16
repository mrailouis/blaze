package me.mrai.larpclient.render

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext
import net.minecraft.world.phys.AABB
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.sqrt

object WorldLineRenderer {
    data class LineSpec(
        val fromX: Double,
        val fromY: Double,
        val fromZ: Double,
        val toX: Double,
        val toY: Double,
        val toZ: Double,
        val thickness: Double,
        val red: Float,
        val green: Float,
        val blue: Float,
        val alpha: Float
    )

    private const val MIN_SEGMENT_LENGTH = 0.08
    private const val MAX_SEGMENTS_PER_LINE = 512
    private const val SEGMENT_OVERLAP = 0.002

    fun render(context: LevelRenderContext, lines: Collection<LineSpec>, depthless: Boolean = true) {
        if (lines.isEmpty()) return

        val boxes = ArrayList<Pair<AABB, WorldBoxRenderer.BoxStyle>>(lines.size * 16)
        for (line in lines) {
            appendBoxes(boxes, line)
        }

        if (boxes.isNotEmpty()) {
            WorldBoxRenderer.render(context, boxes, depthless)
        }
    }

    private fun appendBoxes(
        destination: MutableList<Pair<AABB, WorldBoxRenderer.BoxStyle>>,
        line: LineSpec
    ) {
        val dx = line.toX - line.fromX
        val dy = line.toY - line.fromY
        val dz = line.toZ - line.fromZ
        val length = sqrt(dx * dx + dy * dy + dz * dz)
        if (length <= 1.0e-4) return

        val thickness = line.thickness.coerceIn(0.006, 0.08)
        val halfThickness = thickness / 2.0 + SEGMENT_OVERLAP
        val segmentLength = max(MIN_SEGMENT_LENGTH, thickness * 2.5)
        val segments = ceil(length / segmentLength).toInt().coerceIn(1, MAX_SEGMENTS_PER_LINE)
        val style = WorldBoxRenderer.BoxStyle(
            fillRed = line.red,
            fillGreen = line.green,
            fillBlue = line.blue,
            fillAlpha = line.alpha,
            outlineRed = line.red,
            outlineGreen = line.green,
            outlineBlue = line.blue,
            outlineAlpha = (line.alpha * 0.92f).coerceAtMost(1f),
            outlineThickness = (thickness * 0.45).toFloat().coerceIn(0.01f, 0.03f)
        )

        for (segmentIndex in 0 until segments) {
            val startT = segmentIndex.toDouble() / segments.toDouble()
            val endT = (segmentIndex + 1).toDouble() / segments.toDouble()

            val x0 = lerp(line.fromX, line.toX, startT)
            val y0 = lerp(line.fromY, line.toY, startT)
            val z0 = lerp(line.fromZ, line.toZ, startT)
            val x1 = lerp(line.fromX, line.toX, endT)
            val y1 = lerp(line.fromY, line.toY, endT)
            val z1 = lerp(line.fromZ, line.toZ, endT)

            destination += AABB(
                minOf(x0, x1) - halfThickness,
                minOf(y0, y1) - halfThickness,
                minOf(z0, z1) - halfThickness,
                maxOf(x0, x1) + halfThickness,
                maxOf(y0, y1) + halfThickness,
                maxOf(z0, z1) + halfThickness
            ) to style
        }
    }

    private fun lerp(start: Double, end: Double, delta: Double): Double {
        return start + (end - start) * delta
    }
}
