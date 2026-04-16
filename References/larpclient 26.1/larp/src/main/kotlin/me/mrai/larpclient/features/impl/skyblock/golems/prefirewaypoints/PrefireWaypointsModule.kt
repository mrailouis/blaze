package me.mrai.larpclient.features.impl.skyblock.golems.prefirewaypoints

import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import me.mrai.larpclient.features.impl.skyblock.golems.GolemTrackerState
import me.mrai.larpclient.module.BoolSetting
import me.mrai.larpclient.module.ModeSetting
import me.mrai.larpclient.module.Module
import me.mrai.larpclient.module.ModuleCategory
import me.mrai.larpclient.module.SliderSetting
import me.mrai.larpclient.render.WorldBoxRenderer
import me.mrai.larpclient.util.LarpLog
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object PrefireWaypointsModule : Module(
    name = "Prefire Waypoints",
    description = "Renders dynamic golem prefire aim waypoints near saved standing spots.",
    category = ModuleCategory.SKYBLOCK_GOLEMS
) {
    private const val BOW_SPEED = 3.0
    private const val ARROW_GRAVITY = 0.05
    private const val DEFAULT_ACTIVATION_RADIUS = 10.0
    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val configPath: Path = FabricLoader.getInstance().configDir.resolve("larpclient/golem_prefire_spots.json")

    private val renderStyle = ModeSetting("Render Style", listOf("Outline", "Filled", "Both"), "Both")
    private val waypointSize = SliderSetting("Waypoint Size", 0.85, 0.25, 3.0, 0.05)
    private val waypointDistance = SliderSetting("Waypoint Distance", 38.0, 8.0, 100.0, 1.0)
    private val textScale = SliderSetting("Text Scale", 0.08, 0.03, 0.20, 0.005)
    private val activationRadius = SliderSetting("Activation Radius", DEFAULT_ACTIVATION_RADIUS, 2.0, 20.0, 0.5)
    private val red = SliderSetting("Waypoint Red", 255.0, 0.0, 255.0, 1.0)
    private val green = SliderSetting("Waypoint Green", 120.0, 0.0, 255.0, 1.0)
    private val blue = SliderSetting("Waypoint Blue", 40.0, 0.0, 255.0, 1.0)
    private val alpha = SliderSetting("Waypoint Alpha", 110.0, 0.0, 255.0, 1.0)
    private val depthless = BoolSetting("Depthless", true)
    private val showTimerOnWaypoint = BoolSetting("Show Timer", true)

    private val savedLaunchSpots = linkedMapOf<Area, Vec3>()

    init {
        settings += listOf(
            renderStyle,
            waypointSize,
            waypointDistance,
            textScale,
            activationRadius,
            red,
            green,
            blue,
            alpha,
            depthless,
            showTimerOnWaypoint
        )
        loadSpots()
    }

    fun setSpot(areaInput: String, position: Vec3): Boolean {
        val area = Area.fromInput(areaInput) ?: return false
        savedLaunchSpots[area] = position
        saveSpots()
        return true
    }

    fun areaOptions(): String = Area.entries.joinToString(", ") { it.commandLabel }

    fun render(context: LevelRenderContext) {
        if (!enabled) return

        val client = Minecraft.getInstance()
        val player = client.player ?: return
        val match = Area.entries
            .mapNotNull { area -> savedLaunchSpots[area]?.let { area to it } }
            .filter { (_, saved) -> horizontalDistance(player.position(), saved) <= activationRadius.value }
            .minByOrNull { (_, saved) -> horizontalDistance(player.position(), saved) }
            ?: return

        val (area, _) = match
        val eyePos = player.eyePosition
        val targetPos = area.target.add(0.0, 1.0, 0.0)
        val aimDir = solveAimVector(eyePos, targetPos) ?: return
        val waypointPos = eyePos.add(aimDir.scale(waypointDistance.value))

        val colorR = (red.value / 255.0).toFloat()
        val colorG = (green.value / 255.0).toFloat()
        val colorB = (blue.value / 255.0).toFloat()
        val fillAlpha = (alpha.value / 255.0).toFloat()
        val outlineAlpha = fillAlpha.coerceAtLeast(0.35f)
        val style = when (renderStyle.selected) {
            "Outline" -> WorldBoxRenderer.BoxStyle(
                fillRed = colorR, fillGreen = colorG, fillBlue = colorB, fillAlpha = 0f,
                outlineRed = colorR, outlineGreen = colorG, outlineBlue = colorB, outlineAlpha = 1f
            )
            "Filled" -> WorldBoxRenderer.BoxStyle(
                fillRed = colorR, fillGreen = colorG, fillBlue = colorB, fillAlpha = fillAlpha,
                outlineRed = colorR, outlineGreen = colorG, outlineBlue = colorB, outlineAlpha = 0f
            )
            else -> WorldBoxRenderer.BoxStyle(
                fillRed = colorR, fillGreen = colorG, fillBlue = colorB, fillAlpha = fillAlpha,
                outlineRed = colorR, outlineGreen = colorG, outlineBlue = colorB, outlineAlpha = outlineAlpha
            )
        }

        val half = waypointSize.value / 2.0
        val box = AABB(
            waypointPos.x - half,
            waypointPos.y - half,
            waypointPos.z - half,
            waypointPos.x + half,
            waypointPos.y + half,
            waypointPos.z + half
        )
        WorldBoxRenderer.render(context, listOf(box to style), depthless = depthless.value)

        renderWaypointLabel(
            context,
            waypointPos.add(0.0, 1.1, 0.0),
            area,
            if (showTimerOnWaypoint.value) GolemTrackerState.getCountdownText() else null
        )
    }

    private fun renderWaypointLabel(context: LevelRenderContext, pos: Vec3, area: Area, timerText: String?) {
        val client = Minecraft.getInstance()
        val camera = client.gameRenderer.mainCamera
        val font = client.font
        val buffer = client.renderBuffers().bufferSource()
        val matrices = context.poseStack()
        val stage = GolemTrackerState.getLobbyStageName()
        val line1 = "§6§l${area.commandLabel}"
        val line2 = timerText?.takeIf { it.isNotBlank() } ?: "§7$stage"

        matrices.pushPose()
        matrices.translate(pos.x - camera.position().x, pos.y - camera.position().y, pos.z - camera.position().z)
        matrices.mulPose(camera.rotation())
        val scale = textScale.value.toFloat()
        matrices.scale(scale, -scale, scale)

        val topY = -font.lineHeight.toFloat() - 1f
        drawCentered(font, buffer, matrices, line1, topY, 0xFFFFAA00.toInt())
        drawCentered(font, buffer, matrices, line2, 2f, colorForTimer(line2))
        buffer.endBatch()
        matrices.popPose()
    }

    private fun drawCentered(
        font: Font,
        buffer: MultiBufferSource.BufferSource,
        matrices: com.mojang.blaze3d.vertex.PoseStack,
        text: String,
        y: Float,
        color: Int
    ) {
        val width = font.width(text)
        font.drawInBatch(
            text,
            -width / 2f,
            y,
            color,
            true,
            matrices.last().pose(),
            buffer,
            Font.DisplayMode.SEE_THROUGH,
            0,
            15728880
        )
    }

    private fun colorForTimer(text: String): Int {
        return when {
            text.startsWith("-") -> 0xFFFF5555.toInt()
            text.startsWith("+") -> 0xFF55FFFF.toInt()
            else -> 0xFFFFFFFF.toInt()
        }
    }

    private fun solveAimVector(origin: Vec3, target: Vec3): Vec3? {
        val delta = target.subtract(origin)
        val horizontal = Vec3(delta.x, 0.0, delta.z)
        val distance = horizontal.length()
        if (distance < 0.001) return null

        val y = delta.y
        val speedSq = BOW_SPEED * BOW_SPEED
        val discriminant = speedSq * speedSq - ARROW_GRAVITY * (ARROW_GRAVITY * distance * distance + 2.0 * y * speedSq)
        if (discriminant < 0.0) return null

        val tanTheta = (speedSq + sqrt(discriminant)) / (ARROW_GRAVITY * distance)
        val angle = atan(tanTheta)
        val horizontalDir = horizontal.normalize()
        val cosAngle = cos(angle)
        return Vec3(horizontalDir.x * cosAngle, sin(angle), horizontalDir.z * cosAngle).normalize()
    }

    private fun horizontalDistance(a: Vec3, b: Vec3): Double {
        val dx = a.x - b.x
        val dz = a.z - b.z
        return sqrt(dx * dx + dz * dz)
    }

    private fun loadSpots() {
        try {
            if (!Files.exists(configPath)) return
            val type = object : TypeToken<Map<String, SavedSpot>>() {}.type
            val parsed: Map<String, SavedSpot> = gson.fromJson(Files.readString(configPath), type) ?: return
            savedLaunchSpots.clear()
            parsed.forEach { (name, spot) ->
                val area = Area.fromInput(name) ?: return@forEach
                savedLaunchSpots[area] = Vec3(spot.x, spot.y, spot.z)
            }
        } catch (throwable: Throwable) {
            LarpLog.warn("Failed to load golem prefire spots: ${throwable.message ?: throwable.javaClass.simpleName}")
        }
    }

    private fun saveSpots() {
        try {
            Files.createDirectories(configPath.parent)
            val payload = savedLaunchSpots.entries.associate { (area, vec) ->
                area.commandLabel to SavedSpot(vec.x, vec.y, vec.z)
            }
            Files.writeString(
                configPath,
                gson.toJson(payload),
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE
            )
        } catch (throwable: Throwable) {
            LarpLog.warn("Failed to save golem prefire spots: ${throwable.message ?: throwable.javaClass.simpleName}")
        }
    }

    private data class SavedSpot(val x: Double, val y: Double, val z: Double)

    enum class Area(val commandLabel: String, val target: Vec3, private val aliases: Set<String>) {
        LEFT("Left", Vec3(-648.5, 7.5, -218.5), setOf("left")),
        MID_FRONT("MidFront", Vec3(-643.5, 7.5, -268.5), setOf("midfront", "mid front")),
        MID_BACK("MidBack", Vec3(-726.5, 7.5, -283.5), setOf("midback", "mid back", "middleback", "middle behind", "middlebehind")),
        RIGHT_FRONT("RightFront", Vec3(-638.5, 7.5, -327.5), setOf("rightfront", "right front")),
        RIGHT_BACK("RightBack", Vec3(-677.5, 7.5, -331.5), setOf("rightback", "right back", "right behind", "rightbehind"));

        companion object {
            fun fromInput(raw: String): Area? {
                val normalized = raw.trim().lowercase()
                return entries.firstOrNull { area ->
                    normalized == area.commandLabel.lowercase() || normalized in area.aliases
                }
            }
        }
    }
}
