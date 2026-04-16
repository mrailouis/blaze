package me.mrai.larpclient.ui.font

import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style

object ModText {
    fun literal(text: String): Component {
        return Component.literal(text)
    }

    fun literal(text: String, bold: Boolean): Component {
        return if (bold) bold(text) else literal(text)
    }

    fun bold(text: String): Component {
        return Component.literal(text).copy().setStyle(
            Style.EMPTY.withBold(true)
        )
    }

    fun applyFont(component: Component): Component {
        return component.copy()
    }
}
