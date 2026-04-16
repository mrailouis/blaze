package me.mrai.larpclient.features.impl.dungeons.f7.p5.cowhat

import me.mrai.larpclient.module.BoolSetting
import me.mrai.larpclient.module.Module
import me.mrai.larpclient.module.ModuleCategory
import me.mrai.larpclient.util.TextSanitizer
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component

object CowHatModule : Module(
    name = "Cow Hat",
    description = "Shows a Cow Hat title reminder when Phase 5 begins.",
    category = ModuleCategory.DUNGEONS_F7_P5
) {
    private val showTitle = BoolSetting("Show Title", true)
    private val showSubtitle = BoolSetting("Show Subtitle", false)

    private const val TRIGGER_MESSAGE = "[boss]necron:allthis,fornothing..."

    init {
        settings += listOf(showTitle, showSubtitle)
    }

    fun onChatMessage(rawMessage: String) {
        if (!enabled || !showTitle.value) return
        if (TextSanitizer.compactLower(rawMessage) != TRIGGER_MESSAGE) return

        val gui = Minecraft.getInstance().gui ?: return
        gui.setTimes(5, 45, 10)
        gui.setTitle(Component.literal("Cow Hat").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD))
        if (showSubtitle.value) {
            gui.setSubtitle(Component.literal("Equip it now").withStyle(ChatFormatting.LIGHT_PURPLE))
        } else {
            gui.setSubtitle(Component.empty())
        }
    }
}
