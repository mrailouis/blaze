package me.mrai.larpclient.util

import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import net.minecraft.network.chat.Style
import kotlin.math.roundToInt

object LarpBranding {
    const val BRAND_NAME: String = "LarpClient"

    const val PINK: Int = 0xFFFF5CD1.toInt()
    const val AQUA: Int = 0xFF1EE3E7.toInt()
    const val WHITE: Int = 0xFFFFFFFF.toInt()
    const val GRAY: Int = 0xFF9A9A9A.toInt()
    const val GREEN: Int = 0xFF6CF3A7.toInt()
    const val RED: Int = 0xFFFF7C87.toInt()
    const val GOLD: Int = 0xFFFFD166.toInt()

    fun text(text: String, color: Int, bold: Boolean = false): MutableComponent {
        return Component.literal(text).setStyle(
            Style.EMPTY.withColor(color).withBold(bold)
        )
    }

    fun muted(text: String): MutableComponent = this.text(text, GRAY)

    fun command(text: String): MutableComponent = this.text(text, GOLD, bold = true)

    fun success(text: String): MutableComponent = this.text(text, GREEN)

    fun error(text: String): MutableComponent = this.text(text, RED)

    fun brandWord(): MutableComponent {
        val component = Component.literal("")
        val lastIndex = (BRAND_NAME.length - 1).coerceAtLeast(1)

        BRAND_NAME.forEachIndexed { index, char ->
            val t = index.toFloat() / lastIndex.toFloat()
            component.append(text(char.toString(), mixColor(PINK, AQUA, t)))
        }

        return component
    }

    fun prefix(): MutableComponent {
        return brandWord().append(muted(" » "))
    }

    fun prefixed(message: String, color: Int = WHITE): Component {
        return prefixed(text(message, color))
    }

    fun prefixed(message: Component): Component {
        return prefix().append(message.copy())
    }

    private fun mixColor(from: Int, to: Int, t: Float): Int {
        val clamped = t.coerceIn(0f, 1f)

        val a1 = (from ushr 24) and 0xFF
        val r1 = (from ushr 16) and 0xFF
        val g1 = (from ushr 8) and 0xFF
        val b1 = from and 0xFF

        val a2 = (to ushr 24) and 0xFF
        val r2 = (to ushr 16) and 0xFF
        val g2 = (to ushr 8) and 0xFF
        val b2 = to and 0xFF

        val a = (a1 + ((a2 - a1) * clamped)).roundToInt()
        val r = (r1 + ((r2 - r1) * clamped)).roundToInt()
        val g = (g1 + ((g2 - g1) * clamped)).roundToInt()
        val b = (b1 + ((b2 - b1) * clamped)).roundToInt()

        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }
}
