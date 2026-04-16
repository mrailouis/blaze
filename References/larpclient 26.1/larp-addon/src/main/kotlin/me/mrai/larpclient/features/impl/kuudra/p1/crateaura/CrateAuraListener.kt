package me.mrai.larpclient.features.impl.kuudra.p1.crateaura

import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents

object CrateAuraListener {
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

        ClientPlayConnectionEvents.JOIN.register { _, _, _ ->
            CrateAuraModule.onWorldChange()
        }

        ClientPlayConnectionEvents.DISCONNECT.register { _, _ ->
            CrateAuraModule.onWorldChange()
        }
    }

    private fun handleMessage(raw: String) {
        val text = cleanMessage(raw)
        CrateAuraModule.onRawMessage(text)
    }

    private fun cleanMessage(input: String): String {
        return input
            .replace(Regex("§."), "")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}