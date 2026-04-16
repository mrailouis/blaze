package me.mrai.larpclient.ui.font

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import kotlin.math.roundToInt

object HudTextRenderer {
    private data class Segment(val text: String, val bold: Boolean)

    fun width(text: String, scale: Float = 1f): Int {
        val font = Minecraft.getInstance().font
        val rawWidth = parseSegments(text).sumOf { font.width(componentFor(it)) }
        return (rawWidth * scale).roundToInt()
    }

    fun lineHeight(scale: Float = 1f, extraSpacing: Float = 0f): Int {
        val font = Minecraft.getInstance().font
        return ((font.lineHeight + extraSpacing) * scale).roundToInt()
    }

    fun drawScaledText(
        graphics: GuiGraphicsExtractor,
        text: String,
        x: Float,
        y: Float,
        color: Int,
        scale: Float = 1f,
        shadow: Boolean = false
    ) {
        if (text.isEmpty()) return

        if (shadow) {
            drawScaledText(graphics, text, x + scale, y + scale, (color and 0x00FFFFFF) or 0x55000000, scale, false)
        }

        val font = Minecraft.getInstance().font
        val pose = graphics.pose()
        pose.pushMatrix()
        pose.translate(x, y)
        pose.scale(scale, scale)

        var drawX = 0
        parseSegments(text).forEach { segment ->
            if (segment.text.isEmpty()) return@forEach
            val component = componentFor(segment)
            graphics.text(font, component, drawX, 0, color, false)
            drawX += font.width(component)
        }

        pose.popMatrix()
    }

    private fun componentFor(segment: Segment): Component {
        return Component.literal(segment.text).setStyle(
            if (segment.bold) Style.EMPTY.withBold(true) else Style.EMPTY
        )
    }

    private fun parseSegments(text: String): List<Segment> {
        if ('§' !in text) return listOf(Segment(text, false))

        val segments = mutableListOf<Segment>()
        val current = StringBuilder()
        var bold = false
        var index = 0

        while (index < text.length) {
            val char = text[index]
            if (char == '§' && index + 1 < text.length) {
                if (current.isNotEmpty()) {
                    segments += Segment(current.toString(), bold)
                    current.clear()
                }
                when (text[index + 1].lowercaseChar()) {
                    'l' -> bold = true
                    'r', '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
                    'a', 'b', 'c', 'd', 'e', 'f' -> bold = false
                }
                index += 2
                continue
            }
            current.append(char)
            index++
        }

        if (current.isNotEmpty()) {
            segments += Segment(current.toString(), bold)
        }

        return if (segments.isEmpty()) listOf(Segment("", false)) else segments
    }
}
