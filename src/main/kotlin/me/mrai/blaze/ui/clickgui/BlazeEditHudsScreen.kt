package me.mrai.blaze.ui.clickgui

import me.mrai.blaze.render.gui.BlazeColorPalette
import me.mrai.blaze.render.gui.GuiPrimitives
import me.mrai.blaze.render.gui.GuiRect
import me.mrai.blaze.ui.font.BlazeText
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component

class BlazeEditHudsScreen : Screen(Component.literal("Edit HUDs")) {
    override fun render(graphics: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
        graphics.fill(0, 0, width, height, BlazeColorPalette.SCREEN_SCRIM)

        val panelWidth = (width * 0.34f).toInt().coerceAtLeast(240)
        val panelHeight = (height * 0.18f).toInt().coerceAtLeast(96)
        val panel = GuiRect(
            x = (width - panelWidth) / 2,
            y = (height - panelHeight) / 2,
            width = panelWidth,
            height = panelHeight
        )

        GuiPrimitives.drawGradientShadow(graphics, panel, 8, BlazeColorPalette.GLOW_START, BlazeColorPalette.GLOW_END, layers = 6)
        GuiPrimitives.fillRoundedRect(graphics, panel, 8, BlazeColorPalette.PANEL_BASE)
        BlazeText.draw(graphics, font, "Edit HUDs", panel.x + 18, panel.y + 18, BlazeColorPalette.TEXT_PRIMARY, bold = true, shadow = true)
        BlazeText.draw(graphics, font, "HUD editing foundation goes here.", panel.x + 18, panel.y + 40, BlazeColorPalette.TEXT_SECONDARY)
    }

    override fun isPauseScreen(): Boolean = false
}
