package me.mrai.larpclient.features.impl.dungeons.f7.p4.threebythreehighlight

import me.mrai.larpclient.module.Module
import me.mrai.larpclient.module.ModuleCategory
import me.mrai.larpclient.render.WorldBoxRenderer
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import kotlin.math.sin

object ThreeByThreeHighlightModule : Module(
    name = "3x3 Highlight",
    description = "Highlights the Floor 7 P4 3x3 arrow stack with a rainbow overlay.",
    category = ModuleCategory.DUNGEONS_F7_P4
) {
    private const val RENDER_DISTANCE = 48.0

    private val highlightTargets = linkedSetOf<BlockPos>().apply {
        for (x in 53..55) {
            for (z in 113..115) {
                add(BlockPos(x, 63, z))
            }
        }
    }
    private val highlightCenter = Vec3(54.5, 63.5, 114.5)

    fun render(context: LevelRenderContext) {
        if (!enabled) return

        val client = Minecraft.getInstance()
        val player = client.player ?: return
        client.level ?: return

        if (player.position().distanceToSqr(highlightCenter) > RENDER_DISTANCE * RENDER_DISTANCE) return

        val boxes = highlightTargets.mapIndexed { index, pos ->
            AABB(pos).inflate(0.002) to rainbowStyle(index, highlightTargets.size)
        }
        WorldBoxRenderer.render(context, boxes, depthless = true)
    }

    private fun rainbowStyle(index: Int, size: Int, fillAlpha: Float = 0.33f): WorldBoxRenderer.BoxStyle {
        val time = System.currentTimeMillis() / 900.0
        val phase = (index.toDouble() / size.coerceAtLeast(1).toDouble()) * Math.PI * 2.0
        val red = ((sin(time + phase) + 1.0) * 0.5).toFloat()
        val green = ((sin(time + phase + (Math.PI * 2.0 / 3.0)) + 1.0) * 0.5).toFloat()
        val blue = ((sin(time + phase + (Math.PI * 4.0 / 3.0)) + 1.0) * 0.5).toFloat()
        return WorldBoxRenderer.BoxStyle(
            fillRed = red,
            fillGreen = green,
            fillBlue = blue,
            fillAlpha = fillAlpha,
            outlineRed = red,
            outlineGreen = green,
            outlineBlue = blue,
            outlineAlpha = 1.0f,
            outlineThickness = 0.035f
        )
    }
}
