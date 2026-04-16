package me.mrai.larpclient.util

import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component

object LarpChat {
    fun send(message: String) {
        send(LarpBranding.prefixed(message))
    }

    fun send(message: Component) {
        val player = Minecraft.getInstance().player ?: return
        player.sendSystemMessage(message)
    }
}
