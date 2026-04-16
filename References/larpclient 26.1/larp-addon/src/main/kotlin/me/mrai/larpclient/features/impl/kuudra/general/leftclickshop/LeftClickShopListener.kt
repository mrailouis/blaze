package me.mrai.larpclient.features.impl.kuudra.general.leftclickshop

import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents

object LeftClickShopListener {

    fun register() {
        ClientReceiveMessageEvents.ALLOW_GAME.register { message, _ ->
            val text = message.string

            if (text.contains("You purchased Human Cannonball!", ignoreCase = false)) {
                LeftClickShopModule.suppressForFiveSeconds()
            }

            true
        }
    }
}