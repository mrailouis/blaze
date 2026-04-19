package me.mrai.blaze.ui.font

import me.mrai.blaze.Blaze
import me.mrai.blaze.platform.BlazeIdentifiers
import net.minecraft.network.chat.FontDescription

object BlazeFonts {
    val DEFAULT = BlazeIdentifiers.of(Blaze.MOD_ID, "default")
    val DEFAULT_DESCRIPTION: FontDescription = FontDescription.Resource(DEFAULT)
}
