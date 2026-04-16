package me.mrai.larpclient.features.impl.dungeons.f7.general.witheresp

import me.mrai.larpclient.module.Module
import me.mrai.larpclient.module.ModuleCategory
import me.mrai.larpclient.module.SliderSetting
import me.mrai.larpclient.render.WorldBoxRenderer
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext
import net.minecraft.client.Minecraft
import net.minecraft.world.entity.boss.wither.WitherBoss

object WitherEspModule : Module(
    name = "Wither ESP",
    description = "Renders configurable boxes around withers.",
    category = ModuleCategory.DUNGEONS_F7_GENERAL
) {
    val fillRed = SliderSetting("Fill Red", 255.0, 0.0, 255.0, 1.0)
    val fillGreen = SliderSetting("Fill Green", 0.0, 0.0, 255.0, 1.0)
    val fillBlue = SliderSetting("Fill Blue", 0.0, 0.0, 255.0, 1.0)
    val fillAlpha = SliderSetting("Fill Alpha", 46.0, 0.0, 255.0, 1.0)

    val outlineRed = SliderSetting("Outline Red", 255.0, 0.0, 255.0, 1.0)
    val outlineGreen = SliderSetting("Outline Green", 0.0, 0.0, 255.0, 1.0)
    val outlineBlue = SliderSetting("Outline Blue", 0.0, 0.0, 255.0, 1.0)
    val outlineAlpha = SliderSetting("Outline Alpha", 255.0, 0.0, 255.0, 1.0)
    val outlineThickness = SliderSetting("Outline Thickness", 0.04, 0.01, 0.10, 0.005)

    init {
        settings += listOf(
            fillRed, fillGreen, fillBlue, fillAlpha,
            outlineRed, outlineGreen, outlineBlue, outlineAlpha,
            outlineThickness
        )
    }

    fun render(context: LevelRenderContext) {
        if (!enabled) return

        val level = Minecraft.getInstance().level ?: return
        val style = WorldBoxRenderer.BoxStyle(
            fillRed = (fillRed.value / 255.0).toFloat(),
            fillGreen = (fillGreen.value / 255.0).toFloat(),
            fillBlue = (fillBlue.value / 255.0).toFloat(),
            fillAlpha = (fillAlpha.value / 255.0).toFloat(),
            outlineRed = (outlineRed.value / 255.0).toFloat(),
            outlineGreen = (outlineGreen.value / 255.0).toFloat(),
            outlineBlue = (outlineBlue.value / 255.0).toFloat(),
            outlineAlpha = (outlineAlpha.value / 255.0).toFloat(),
            outlineThickness = outlineThickness.value.toFloat()
        )

        val boxes = level.entitiesForRendering()
            .filterIsInstance<WitherBoss>()
            .filter { it.isAlive }
            .map { it.boundingBox.inflate(0.02) to style }
            .toList()

        WorldBoxRenderer.render(context, boxes)
    }
}
