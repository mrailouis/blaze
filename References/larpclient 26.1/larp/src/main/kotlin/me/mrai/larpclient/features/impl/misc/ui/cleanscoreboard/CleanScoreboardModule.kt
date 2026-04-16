package me.mrai.larpclient.features.impl.misc.ui.cleanscoreboard

import me.mrai.larpclient.module.BoolSetting
import me.mrai.larpclient.module.ComponentSetting
import me.mrai.larpclient.module.Module
import me.mrai.larpclient.module.ModuleCategory
import me.mrai.larpclient.module.SliderSetting

object CleanScoreboardModule : Module(
    name = "Clean Scoreboard",
    description = "Renders a custom cleaned scoreboard with a footer.",
    category = ModuleCategory.MISC_UI
) {
    val hideVanilla = BoolSetting("Hide Vanilla", true)
    val showBackground = BoolSetting("Show Background", true)
    val showFooter = BoolSetting("Show Footer", true)
    val showBorder = BoolSetting("Show Border", true)
    val animateBorder = BoolSetting("Animate Border", true)

    val backgroundAlpha = SliderSetting("Background Alpha", 0.42, 0.0, 1.0, 0.01)
    val padding = SliderSetting("Padding", 6.0, 2.0, 16.0, 1.0)
    val lineSpacing = SliderSetting("Line Spacing", 1.0, 0.0, 8.0, 1.0)
    val footerGap = SliderSetting("Footer Gap", 6.0, 0.0, 20.0, 1.0)

    val topOffset = SliderSetting("Top Offset", 6.0, 0.0, 60.0, 1.0)
    val rightOffset = SliderSetting("Right Offset", 4.0, 0.0, 60.0, 1.0)

    val hudScale = SliderSetting("HUD Scale", 1.0, 0.5, 2.5, 0.05)
    val extraWidth = SliderSetting("Extra Width", 0.0, 0.0, 220.0, 1.0)
    val extraHeight = SliderSetting("Extra Height", 0.0, 0.0, 220.0, 1.0)

    val roundness = SliderSetting("Roundness", 8.0, 0.0, 20.0, 1.0)
    val borderThickness = SliderSetting("Border Thickness", 2.0, 1.0, 6.0, 1.0)
    val borderAnimSpeed = SliderSetting("Border Speed", 0.35, 0.01, 3.0, 0.01)
    val scoreboardBackgroundColor = ComponentSetting("Scoreboard Background Color", "#FF101114")
    val scoreboardBorderStartColor = ComponentSetting("Scoreboard Border Start Color", "#FF48E4E7")
    val scoreboardBorderEndColor = ComponentSetting("Scoreboard Border End Color", "#FFB04DFF")

    init {
        settings += hideVanilla
        settings += showBackground
        settings += showFooter
        settings += showBorder
        settings += animateBorder

        settings += backgroundAlpha
        settings += padding
        settings += lineSpacing
        settings += footerGap

        settings += topOffset
        settings += rightOffset
        settings += hudScale
        settings += extraWidth
        settings += extraHeight

        settings += roundness
        settings += borderThickness
        settings += borderAnimSpeed
        settings += scoreboardBackgroundColor
        settings += scoreboardBorderStartColor
        settings += scoreboardBorderEndColor
    }
}
