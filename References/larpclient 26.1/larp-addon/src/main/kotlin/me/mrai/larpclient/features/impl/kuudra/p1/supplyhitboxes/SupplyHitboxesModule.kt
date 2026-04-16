package me.mrai.larpclient.features.impl.kuudra.p1.supplyhitboxes

import me.mrai.larpclient.features.impl.kuudra.p1.summoncrates.SummonCratesSimulator
import me.mrai.larpclient.module.Module
import me.mrai.larpclient.module.ModuleCategory
import me.mrai.larpclient.render.WorldBoxRenderer
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext

object SupplyHitboxesModule : Module(
    name = "Supply Hitboxes",
    description = "Highlights simulated supply chests and their invisible pickup zombies.",
    category = ModuleCategory.KUUDRA_P1
) {
    private val chestStyle = WorldBoxRenderer.BoxStyle(
        fillRed = 0f,
        fillGreen = 0f,
        fillBlue = 0f,
        fillAlpha = 0f,
        outlineRed = 1f,
        outlineGreen = 0.7f,
        outlineBlue = 0.1f,
        outlineAlpha = 0.95f,
        outlineThickness = 0.035f
    )

    private val zombieStyle = WorldBoxRenderer.BoxStyle(
        fillRed = 0f,
        fillGreen = 0f,
        fillBlue = 0f,
        fillAlpha = 0f,
        outlineRed = 0f,
        outlineGreen = 1f,
        outlineBlue = 1f,
        outlineAlpha = 0.95f,
        outlineThickness = 0.03f
    )

    fun render(context: LevelRenderContext) {
        val state = SummonCratesSimulator.renderState()
        if (state.chestBoxes.isEmpty() && state.zombieBoxes.isEmpty()) return

        val boxes = buildList {
            state.chestBoxes.forEach { add(it to chestStyle) }
            state.zombieBoxes.forEach { add(it to zombieStyle) }
        }

        WorldBoxRenderer.render(context, boxes, depthless = false)
    }
}
