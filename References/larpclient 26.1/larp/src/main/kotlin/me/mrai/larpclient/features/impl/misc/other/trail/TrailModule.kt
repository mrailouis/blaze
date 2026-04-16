package me.mrai.larpclient.features.impl.misc.other.trail

import me.mrai.larpclient.module.BoolSetting
import me.mrai.larpclient.module.Module
import me.mrai.larpclient.module.ModuleCategory
import me.mrai.larpclient.module.SliderSetting
import net.minecraft.client.Minecraft
import net.minecraft.world.phys.Vec3

object TrailModule : Module(
    name = "Trail",
    description = "Renders a trail under your feet.",
    category = ModuleCategory.MISC_OTHER
) {
    val onlyWhenMoving = BoolSetting("Only When Moving", true)

    val length = SliderSetting("Length", 25.0, 1.0, 200.0, 1.0)
    val lineWidth = SliderSetting("Line Width", 2.0, 1.0, 6.0, 0.5)
    val yOffset = SliderSetting("Y Offset", 0.05, -1.0, 1.0, 0.01)

    val red = SliderSetting("Red", 72.0, 0.0, 255.0, 1.0)
    val green = SliderSetting("Green", 228.0, 0.0, 255.0, 1.0)
    val blue = SliderSetting("Blue", 231.0, 0.0, 255.0, 1.0)
    val alpha = SliderSetting("Alpha", 255.0, 0.0, 255.0, 1.0)

    private data class TrailPoint(val pos: Vec3)

    private val points = mutableListOf<TrailPoint>()
    private var lastDimensionKey: String? = null

    init {
        settings += onlyWhenMoving
        settings += length
        settings += lineWidth
        settings += yOffset
        settings += red
        settings += green
        settings += blue
        settings += alpha
    }

    override fun onDisable() {
        clear()
    }

    override fun onTick() {
        val client = Minecraft.getInstance()
        val level = client.level
        val player = client.player

        if (level == null || player == null) {
            clear()
            return
        }

        val dimensionKey = level.dimension().toString()
        if (lastDimensionKey != null && lastDimensionKey != dimensionKey) {
            clear()
        }
        lastDimensionKey = dimensionKey

        val pos = Vec3(
            player.x,
            player.y + yOffset.value,
            player.z
        )

        if (onlyWhenMoving.value) {
            val velocity = player.deltaMovement
            val horizontalSq = velocity.x * velocity.x + velocity.z * velocity.z
            if (horizontalSq < 0.0001) {
                trimToLength()
                return
            }
        }

        val last = points.lastOrNull()?.pos
        if (last == null || last.distanceToSqr(pos) >= 0.04) {
            points.add(TrailPoint(pos))
        }

        trimToLength()
    }

    fun getPoints(): List<Vec3> = points.map { it.pos }

    fun getColorArgb(): Int {
        val a = alpha.value.toInt().coerceIn(0, 255)
        val r = red.value.toInt().coerceIn(0, 255)
        val g = green.value.toInt().coerceIn(0, 255)
        val b = blue.value.toInt().coerceIn(0, 255)
        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }

    fun clear() {
        points.clear()
        lastDimensionKey = null
    }

    private fun trimToLength() {
        val maxLength = length.value
        if (points.size < 2) return

        var total = 0.0
        var keepFrom = points.lastIndex

        for (i in points.lastIndex downTo 1) {
            total += points[i].pos.distanceTo(points[i - 1].pos)
            if (total > maxLength) {
                keepFrom = i
                break
            }
            keepFrom = i - 1
        }

        if (keepFrom > 0) {
            points.subList(0, keepFrom).clear()
        }
    }
}
