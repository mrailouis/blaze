package me.mrai.larpclient.auth

import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import org.lwjgl.glfw.GLFW

class AuthFailureScreen : Screen(Component.literal("LarpClient Authentication")) {
    private data class ButtonBox(
        val x1: Int,
        val y1: Int,
        val x2: Int,
        val y2: Int,
        val label: String,
        val action: () -> Unit
    ) {
        fun contains(mouseX: Double, mouseY: Double): Boolean {
            return mouseX >= x1 && mouseX <= x2 && mouseY >= y1 && mouseY <= y2
        }
    }

    private val buttons = mutableListOf<ButtonBox>()

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        super.extractRenderState(graphics, mouseX, mouseY, delta)

        buttons.clear()
        graphics.fill(0, 0, width, height, 0xD0101010.toInt())

        val panelWidth = 340
        val panelHeight = 170
        val panelX = (width - panelWidth) / 2
        val panelY = (height - panelHeight) / 2

        graphics.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, 0xF0222222.toInt())
        graphics.outline(panelX, panelY, panelWidth, panelHeight, 0xFFFF5555.toInt())

        val title = "Authentication failed"
        val titleX = panelX + (panelWidth - minecraft.font.width(title)) / 2
        graphics.text(minecraft.font, Component.literal(title), titleX, panelY + 16, 0xFFFFFFFF.toInt(), false)

        val reason = AuthManager.denialReason ?: "Unknown error"
        val wrapped = minecraft.font.split(Component.literal(reason), panelWidth - 32)
        wrapped.take(4).forEachIndexed { index, line ->
            graphics.text(minecraft.font, line, panelX + 16, panelY + 44 + index * 12, 0xFFE0E0E0.toInt(), false)
        }

        val buttonTop = panelY + panelHeight - 40
        addButton(panelX + 20, buttonTop, 130, 20, "Retry") { AuthManager.retryAuthentication() }
        addButton(panelX + panelWidth - 150, buttonTop, 130, 20, "Quit Game") { minecraft.stop() }

        buttons.forEach { button ->
            val hovered = button.contains(mouseX.toDouble(), mouseY.toDouble())
            val fillColor = if (hovered) 0xFF4A4A4A.toInt() else 0xFF323232.toInt()
            graphics.fill(button.x1, button.y1, button.x2, button.y2, fillColor)
            graphics.outline(button.x1, button.y1, button.x2 - button.x1, button.y2 - button.y1, 0xFFFFFFFF.toInt())
            val textX = button.x1 + ((button.x2 - button.x1) - minecraft.font.width(button.label)) / 2
            val textY = button.y1 + 6
            graphics.text(minecraft.font, Component.literal(button.label), textX, textY, 0xFFFFFFFF.toInt(), false)
        }
    }

    override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        if (event.button() != 0) return true
        buttons.firstOrNull { it.contains(event.x(), event.y()) }?.action?.invoke()
        return true
    }

    override fun keyPressed(event: KeyEvent): Boolean {
        when (event.key()) {
            GLFW.GLFW_KEY_R, GLFW.GLFW_KEY_ENTER -> AuthManager.retryAuthentication()
            GLFW.GLFW_KEY_ESCAPE -> minecraft.stop()
        }
        return true
    }

    override fun shouldCloseOnEsc(): Boolean = false

    override fun isPauseScreen(): Boolean = true

    private fun addButton(x: Int, y: Int, width: Int, height: Int, label: String, action: () -> Unit) {
        buttons += ButtonBox(x, y, x + width, y + height, label, action)
    }
}
