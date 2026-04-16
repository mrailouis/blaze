package me.mrai.larpclient.features.impl.skyblock.general.performancehud

import me.mrai.larpclient.module.BoolSetting
import me.mrai.larpclient.module.ModeSetting
import me.mrai.larpclient.module.Module
import me.mrai.larpclient.module.ModuleCategory
import me.mrai.larpclient.module.SliderSetting
import kotlin.math.roundToInt

object PerformanceHudModule : Module(
    name = "Performance HUD",
    description = "Shows performance information on the screen.",
    category = ModuleCategory.SKYBLOCK_GENERAL
) {
    private const val HORIZONTAL = "Horizontal"

    val nameRed = SliderSetting("Name Red", 50.0, 0.0, 255.0, 1.0)
    val nameGreen = SliderSetting("Name Green", 150.0, 0.0, 255.0, 1.0)
    val nameBlue = SliderSetting("Name Blue", 220.0, 0.0, 255.0, 1.0)

    val valueRed = SliderSetting("Value Red", 255.0, 0.0, 255.0, 1.0)
    val valueGreen = SliderSetting("Value Green", 255.0, 0.0, 255.0, 1.0)
    val valueBlue = SliderSetting("Value Blue", 255.0, 0.0, 255.0, 1.0)

    val direction = ModeSetting("Direction", listOf("Horizontal", "Vertical"), HORIZONTAL)
    val showFPS = BoolSetting("Show FPS", true)
    val showTPS = BoolSetting("Show TPS", true)
    val showPing = BoolSetting("Show Ping", true)

    init {
        settings += listOf(
            nameRed,
            nameGreen,
            nameBlue,
            valueRed,
            valueGreen,
            valueBlue,
            direction,
            showFPS,
            showTPS,
            showPing
        )
    }

    fun isHorizontal(): Boolean = direction.selected.equals(HORIZONTAL, ignoreCase = true)

    fun nameColor(): Int = rgb(nameRed.value, nameGreen.value, nameBlue.value)

    fun valueColor(): Int = rgb(valueRed.value, valueGreen.value, valueBlue.value)

    private fun rgb(red: Double, green: Double, blue: Double): Int {
        return (255 shl 24) or
            (red.roundToInt().coerceIn(0, 255) shl 16) or
            (green.roundToInt().coerceIn(0, 255) shl 8) or
            blue.roundToInt().coerceIn(0, 255)
    }
}
