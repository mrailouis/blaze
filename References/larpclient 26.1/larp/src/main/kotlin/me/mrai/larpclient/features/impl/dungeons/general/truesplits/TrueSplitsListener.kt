package me.mrai.larpclient.features.impl.dungeons.general.truesplits

import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents

object TrueSplitsListener {
    private var registered = false

    fun register() {
        if (registered) return
        registered = true

        ClientReceiveMessageEvents.GAME.register { message, _ ->
            handleMessage(message.string)
        }

        ClientReceiveMessageEvents.CHAT.register { message, _, _, _, _ ->
            handleMessage(message.string)
        }

        ClientPlayConnectionEvents.DISCONNECT.register { _, _ ->
            TrueSplitsState.onWorldChange()
        }

        ClientPlayConnectionEvents.JOIN.register { _, _, _ ->
            TrueSplitsState.onWorldChange()
        }
    }

    private fun handleMessage(text: String) {
        TrueSplitsState.onDungeonChat(text)

        if (text.trim() == "The Energy Laser is charging up!") {
            TrueSplitsState.onMaxorLaserCharged()
        }
    }
}
