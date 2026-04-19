package me.mrai.blaze.render.world

import me.mrai.blaze.Blaze
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3

object WorldBoxRenderer {
    private var warned = false

    data class BoxStyle(
        val fillRed: Float,
        val fillGreen: Float,
        val fillBlue: Float,
        val fillAlpha: Float,
        val outlineRed: Float,
        val outlineGreen: Float,
        val outlineBlue: Float,
        val outlineAlpha: Float,
        val outlineThickness: Float = 0.03f
    )

    data class SurfaceQuad(
        val a: Vec3,
        val b: Vec3,
        val c: Vec3,
        val d: Vec3,
        val style: BoxStyle
    )

    fun render(
        context: LevelRenderContext,
        boxes: List<Pair<AABB, BoxStyle>>,
        quads: List<SurfaceQuad> = emptyList(),
        depthless: Boolean = true
    ) {
        if ((boxes.isEmpty() && quads.isEmpty()) || warned) {
            return
        }

        warned = true
        Blaze.logger.warn("Blaze ESP rendering is temporarily disabled on Minecraft 26.1 to avoid a renderer crash.")
    }
}
