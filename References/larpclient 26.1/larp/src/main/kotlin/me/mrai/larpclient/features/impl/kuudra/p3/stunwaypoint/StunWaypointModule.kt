package me.mrai.larpclient.features.impl.kuudra.p3.stunwaypoint

import me.mrai.larpclient.module.BoolSetting
import me.mrai.larpclient.module.ModeSetting
import me.mrai.larpclient.module.Module
import me.mrai.larpclient.module.ModuleCategory
import me.mrai.larpclient.module.SliderSetting
import me.mrai.larpclient.render.WorldBoxRenderer
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3

object StunWaypointModule : Module(
    name = "Stun Waypoint",
    description = "Renders a configurable stun waypoint for the selected pod.",
    category = ModuleCategory.KUUDRA_P3
) {
    private val targetPod = ModeSetting("Target Pod", listOf("Right Pod", "Left Pod", "Back Pod"), "Left Pod")
    private val red = SliderSetting("Waypoint Red", 255.0, 0.0, 255.0, 1.0)
    private val green = SliderSetting("Waypoint Green", 80.0, 0.0, 255.0, 1.0)
    private val blue = SliderSetting("Waypoint Blue", 80.0, 0.0, 255.0, 1.0)
    private val alpha = SliderSetting("Waypoint Alpha", 110.0, 0.0, 255.0, 1.0)
    private val depthless = BoolSetting("Depthless", true)

    init {
        settings += listOf(targetPod, red, green, blue, alpha, depthless)
    }

    override fun onDisable() = Unit

    fun onWorldChange() = Unit

    fun onChatMessage(rawMessage: String) = Unit

    override fun onTick() = Unit

    fun render(context: LevelRenderContext) {
        if (!enabled) return

        val renderPos = selectedPodPos()

        val colorR = (red.value / 255.0).toFloat()
        val colorG = (green.value / 255.0).toFloat()
        val colorB = (blue.value / 255.0).toFloat()
        val fillAlpha = (alpha.value / 255.0).toFloat()
        val style = WorldBoxRenderer.BoxStyle(
            fillRed = colorR,
            fillGreen = colorG,
            fillBlue = colorB,
            fillAlpha = fillAlpha,
            outlineRed = colorR,
            outlineGreen = colorG,
            outlineBlue = colorB,
            outlineAlpha = fillAlpha.coerceAtLeast(0.35f),
            outlineThickness = 0.035f
        )

        val box = AABB(
            renderPos.x - 0.5,
            renderPos.y,
            renderPos.z - 0.5,
            renderPos.x + 0.5,
            renderPos.y + 1.0,
            renderPos.z + 0.5
        )
        WorldBoxRenderer.render(context, listOf(box to style), depthless = depthless.value)
    }

    private fun selectedPodPos(): Vec3 = when (targetPod.selected) {
        "Right Pod" -> Vec3(-168.0, 28.0, -168.0)
        "Back Pod" -> Vec3(-156.0, 28.0, -157.0)
        else -> Vec3(-153.0, 27.0, -173.0)
    }
}
