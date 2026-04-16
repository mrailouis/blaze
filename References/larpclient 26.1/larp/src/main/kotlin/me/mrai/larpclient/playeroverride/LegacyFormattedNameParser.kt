package me.mrai.larpclient.playeroverride

import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import net.minecraft.network.chat.Style

object LegacyFormattedNameParser {
    fun parse(raw: String): Component {
        val root = Component.empty()
        val buffer = StringBuilder()
        var style = Style.EMPTY
        var index = 0

        fun flushBuffer() {
            if (buffer.isEmpty()) return

            root.append(Component.literal(buffer.toString()).setStyle(style))
            buffer.setLength(0)
        }

        while (index < raw.length) {
            val current = raw[index]
            if ((current == '&' || current == ChatFormatting.PREFIX_CODE) && index + 1 < raw.length) {
                val format = ChatFormatting.getByCode(raw[index + 1].lowercaseChar())
                if (format != null) {
                    flushBuffer()
                    style = when {
                        format == ChatFormatting.RESET -> Style.EMPTY
                        format.isColor -> Style.EMPTY.applyLegacyFormat(format)
                        else -> style.applyLegacyFormat(format)
                    }
                    index += 2
                    continue
                }
            }

            buffer.append(current)
            index++
        }

        flushBuffer()
        return if (root.siblings.isEmpty()) Component.literal(raw) else root
    }

    fun copyWithFallbackStyle(component: Component, fallback: Style): MutableComponent {
        val base = when (val contents = component.contents) {
            is net.minecraft.network.chat.contents.PlainTextContents.LiteralContents -> Component.literal(contents.text())
            else -> component.plainCopy()
        }

        base.setStyle(component.style.applyTo(fallback))
        component.siblings.forEach { sibling ->
            base.append(copyWithFallbackStyle(sibling, fallback))
        }
        return base
    }
}
