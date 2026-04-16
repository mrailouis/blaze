package me.mrai.blaze.ui.clickgui.model

import kotlin.math.roundToInt
import me.mrai.blaze.render.gui.GuiRect

data class ClickGuiLayout(
    val panel: GuiRect,
    val headerHeight: Int,
    val leftPane: GuiRect,
    val rightPane: GuiRect,
    val separator: GuiRect,
    val panelRadius: Int,
    val elementRadius: Int,
    val uiScale: Float
) {
    companion object {
        private const val UI_SCALE = 0.75f

        fun compute(screenWidth: Int, screenHeight: Int, panelXOverride: Int? = null, panelYOverride: Int? = null): ClickGuiLayout {
            fun s(value: Int): Int = (value * UI_SCALE).roundToInt()

            val minPanelWidth = s(520)
            val minPanelHeight = s(360)
            val maxPanelWidth = (screenWidth - s(40)).coerceAtLeast(minPanelWidth)
            val maxPanelHeight = (screenHeight - s(40)).coerceAtLeast(minPanelHeight)

            val panelWidth = (screenWidth * 0.72f * UI_SCALE).roundToInt().coerceIn(minPanelWidth, maxPanelWidth)
            val panelHeight = (screenHeight * 0.70f * UI_SCALE).roundToInt().coerceIn(minPanelHeight, maxPanelHeight)
            val centeredX = (screenWidth - panelWidth) / 2
            val centeredY = (screenHeight - panelHeight) / 2
            val panelX = (panelXOverride ?: centeredX).coerceIn(0, (screenWidth - panelWidth).coerceAtLeast(0))
            val panelY = (panelYOverride ?: centeredY).coerceIn(0, (screenHeight - panelHeight).coerceAtLeast(0))

            val panel = GuiRect(panelX, panelY, panelWidth, panelHeight)
            val headerHeight = s(76)
            val leftWidth = (panelWidth * 0.20f).roundToInt()
            val contentTop = panelY + headerHeight + s(14)
            val contentHeight = panel.bottom - contentTop - s(16)
            val separatorWidth = 1
            val separatorX = panelX + leftWidth

            return ClickGuiLayout(
                panel = panel,
                headerHeight = headerHeight,
                leftPane = GuiRect(panelX + s(18), contentTop, leftWidth - s(30), contentHeight),
                rightPane = GuiRect(separatorX + s(18), contentTop, panel.right - separatorX - s(36), contentHeight),
                separator = GuiRect(separatorX, panelY + headerHeight, separatorWidth, panel.bottom - (panelY + headerHeight) - s(16)),
                panelRadius = s(10).coerceAtLeast(1),
                elementRadius = s(8).coerceAtLeast(1),
                uiScale = UI_SCALE
            )
        }
    }
}
