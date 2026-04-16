package me.mrai.larpclient.features.impl.dungeons.f7.p5.arrowstackwaypoints

import me.mrai.larpclient.module.ModeSetting
import me.mrai.larpclient.module.Module
import me.mrai.larpclient.module.ModuleCategory
import me.mrai.larpclient.module.SliderSetting
import me.mrai.larpclient.render.WorldBoxRenderer
import me.mrai.larpclient.render.WorldRingRenderer
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext
import net.minecraft.client.Minecraft
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import kotlin.math.abs

object ArrowStackWaypointsModule : Module(
    name = "Arrow Stack Waypoints",
    description = "Switches the active Floor 7 P5 arrow stack waypoint based on which color ring you enter.",
    category = ModuleCategory.DUNGEONS_F7_P5
) {
    private val renderStyle = ModeSetting("Render Style", listOf("Outline", "Filled", "Both"), "Both")
    private val size = SliderSetting("Waypoint Size", 1.0, 0.25, 3.0, 0.05)
    private val ringFillAlpha = SliderSetting("Ring Fill Alpha", 70.0, 0.0, 255.0, 1.0)
    private val waypointFillAlpha = SliderSetting("Waypoint Fill Alpha", 56.0, 0.0, 255.0, 1.0)

    private const val RING_RADIUS = 5.0
    private const val RENDER_RING_RADIUS = 3.5
    private const val MAX_VERTICAL_TRIGGER_DELTA = 3.0
    private const val RENDER_RING_Y_OFFSET = 1.02

    private val triggers = listOf(
        Trigger("Green", Vec3(51.0, 4.0, 71.0), Vec3(31.0, 21.0, 89.0), Color(0.22f, 0.95f, 0.35f)),
        Trigger("Orange", Vec3(59.0, 4.0, 79.0), Vec3(82.0, 21.0, 61.0), Color(1.0f, 0.62f, 0.18f)),
        Trigger("Blue", Vec3(48.0, 5.0, 108.0), Vec3(82.0, 20.0, 98.0), Color(0.26f, 0.56f, 1.0f)),
        Trigger("Red", Vec3(40.0, 4.0, 84.0), Vec3(28.0, 21.0, 59.0), Color(1.0f, 0.24f, 0.24f)),
        Trigger("Red", Vec3(17.0, 5.0, 85.0), Vec3(28.0, 21.0, 59.0), Color(1.0f, 0.24f, 0.24f)),
        Trigger("Purple", Vec3(30.0, 5.0, 101.0), Vec3(55.0, 20.0, 126.0), Color(0.72f, 0.34f, 1.0f)),
        Trigger("Purple", Vec3(83.0, 4.0, 102.0), Vec3(58.0, 20.0, 126.0), Color(0.72f, 0.34f, 1.0f))
    )

    private var activeTrigger: Trigger? = null

    init {
        settings += listOf(renderStyle, size, ringFillAlpha, waypointFillAlpha)
    }

    override fun onDisable() {
        activeTrigger = null
    }

    fun onWorldChange() {
        activeTrigger = null
    }

    override fun onTick() {
        if (!enabled) return

        val player = Minecraft.getInstance().player ?: return
        val playerPos = player.position()

        val matched = triggers.firstOrNull { it.contains(playerPos) }
        if (matched != null) {
            activeTrigger = matched
        }
    }

    fun render(context: LevelRenderContext) {
        if (!enabled) return

        Minecraft.getInstance().player ?: return

        WorldRingRenderer.render(
            context,
            triggers.map { trigger ->
                WorldRingRenderer.RingSpec(
                    centerX = trigger.center.x,
                    centerY = trigger.center.y + RENDER_RING_Y_OFFSET,
                    centerZ = trigger.center.z,
                    radius = RENDER_RING_RADIUS,
                    thickness = 0.24,
                    height = 0.04,
                    red = trigger.color.r,
                    green = trigger.color.g,
                    blue = trigger.color.b,
                    fillAlpha = (ringFillAlpha.value / 255.0).toFloat(),
                    outlineAlpha = 0.92f
                )
            }
        )

        val active = activeTrigger ?: return
        val half = size.value / 2.0
        val waypoint = active.waypoint
        val color = active.color

        val style = when (renderStyle.selected) {
            "Outline" -> WorldBoxRenderer.BoxStyle(
                fillRed = color.r,
                fillGreen = color.g,
                fillBlue = color.b,
                fillAlpha = 0f,
                outlineRed = color.r,
                outlineGreen = color.g,
                outlineBlue = color.b,
                outlineAlpha = 0.96f,
                outlineThickness = 0.04f
            )
            "Filled" -> WorldBoxRenderer.BoxStyle(
                fillRed = color.r,
                fillGreen = color.g,
                fillBlue = color.b,
                fillAlpha = (waypointFillAlpha.value / 255.0).toFloat(),
                outlineRed = color.r,
                outlineGreen = color.g,
                outlineBlue = color.b,
                outlineAlpha = 0f,
                outlineThickness = 0.04f
            )
            else -> WorldBoxRenderer.BoxStyle(
                fillRed = color.r,
                fillGreen = color.g,
                fillBlue = color.b,
                fillAlpha = (waypointFillAlpha.value / 255.0).toFloat(),
                outlineRed = color.r,
                outlineGreen = color.g,
                outlineBlue = color.b,
                outlineAlpha = 0.96f,
                outlineThickness = 0.04f
            )
        }

        val box = AABB(
            waypoint.x - half,
            waypoint.y - half,
            waypoint.z - half,
            waypoint.x + half,
            waypoint.y + half,
            waypoint.z + half
        )

        WorldBoxRenderer.render(context, listOf(box to style))
    }

    private data class Trigger(
        val name: String,
        val center: Vec3,
        val waypoint: Vec3,
        val color: Color
    ) {
        fun contains(playerPos: Vec3): Boolean {
            return playerPos.subtract(center).horizontalDistance() <= RING_RADIUS &&
                abs(playerPos.y - center.y) <= MAX_VERTICAL_TRIGGER_DELTA
        }
    }

    private data class Color(val r: Float, val g: Float, val b: Float)
}
