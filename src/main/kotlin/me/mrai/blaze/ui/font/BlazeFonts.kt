package me.mrai.blaze.ui.font

import me.mrai.blaze.Blaze
import net.minecraft.network.chat.FontDescription
import net.minecraft.resources.Identifier

object BlazeFonts {
    val DEFAULT: Identifier = Identifier.fromNamespaceAndPath(Blaze.MOD_ID, "default")
    val DEFAULT_DESCRIPTION: FontDescription = FontDescription.Resource(DEFAULT)
}
