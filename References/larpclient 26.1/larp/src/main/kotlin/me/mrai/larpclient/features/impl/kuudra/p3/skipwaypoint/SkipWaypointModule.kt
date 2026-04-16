package me.mrai.larpclient.features.impl.kuudra.p3.skipwaypoint

import me.mrai.larpclient.module.BoolSetting
import me.mrai.larpclient.module.ComponentSetting
import me.mrai.larpclient.module.Module
import me.mrai.larpclient.module.ModuleCategory
import me.mrai.larpclient.module.SliderSetting
import me.mrai.larpclient.render.WorldBoxRenderer
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext
import net.minecraft.world.phys.AABB

object SkipWaypointModule : Module(
    name = "Skip Waypoint",
    description = "Renders a configurable skip waypoint box that can be depthless.",
    category = ModuleCategory.KUUDRA_P3
) {
    private val waypointX = ComponentSetting("Waypoint X", "-176.0")
    private val waypointY = ComponentSetting("Waypoint Y", "33.0")
    private val waypointZ = ComponentSetting("Waypoint Z", "-151.0")

    private val red = SliderSetting("Waypoint Red", 255.0, 0.0, 255.0, 1.0)
    private val green = SliderSetting("Waypoint Green", 80.0, 0.0, 255.0, 1.0)
    private val blue = SliderSetting("Waypoint Blue", 80.0, 0.0, 255.0, 1.0)
    private val alpha = SliderSetting("Waypoint Alpha", 110.0, 0.0, 255.0, 1.0)
    private val depthless = BoolSetting("Depthless", true)

    init {
        settings += listOf(
            waypointX, waypointY, waypointZ,
            red, green, blue, alpha,
            depthless
        )
    }

    fun render(context: LevelRenderContext) {
        if (!enabled) return

        val x = waypointX.text.toDoubleOrNull() ?: -176.0
        val y = waypointY.text.toDoubleOrNull() ?: 33.0
        val z = waypointZ.text.toDoubleOrNull() ?: -151.0

        val fillAlpha = (alpha.value / 255.0).toFloat()
        val outlineAlpha = (alpha.value / 255.0).toFloat().coerceAtLeast(0.35f)
        val colorR = (red.value / 255.0).toFloat()
        val colorG = (green.value / 255.0).toFloat()
        val colorB = (blue.value / 255.0).toFloat()

        val style = WorldBoxRenderer.BoxStyle(
            fillRed = colorR,
            fillGreen = colorG,
            fillBlue = colorB,
            fillAlpha = fillAlpha,
            outlineRed = colorR,
            outlineGreen = colorG,
            outlineBlue = colorB,
            outlineAlpha = outlineAlpha,
            outlineThickness = 0.035f
        )

        val core = AABB(x - 0.5, y - 0.5, z - 0.5, x + 0.5, y + 0.5, z + 0.5)
        WorldBoxRenderer.render(
            context,
            listOf(core to style),
            depthless = depthless.value
        )
    }
}
