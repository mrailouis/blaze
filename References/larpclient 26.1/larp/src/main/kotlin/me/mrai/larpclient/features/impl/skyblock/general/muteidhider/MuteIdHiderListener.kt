package me.mrai.larpclient.features.impl.skyblock.general.muteidhider

import me.mrai.larpclient.util.LarpBranding
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents
import net.minecraft.client.Minecraft

object MuteIdHiderListener {

    private var inCandidateBlock = false
    private var confirmedMuteBlock = false
    private var sentReplacement = false
    private var muteTime: String? = null

    fun register() {
        ClientReceiveMessageEvents.ALLOW_GAME.register { message, _ ->
            if (!MuteIdHiderModule.enabled) {
                reset()
                true
            } else {
                val text = message.string.trim()

                val isDivider = text.all { it == '-' } && text.length >= 10
                val isBlank = text.isBlank()

                if (!inCandidateBlock && isDivider) {
                    inCandidateBlock = true
                    confirmedMuteBlock = false
                    sentReplacement = false
                    muteTime = null
                    false
                } else if (!inCandidateBlock) {
                    true
                } else if (text.contains("You are currently muted")) {
                    confirmedMuteBlock = true
                    false
                } else if (text.startsWith("Your mute will expire in ")) {
                    confirmedMuteBlock = true
                    muteTime = text.removePrefix("Your mute will expire in ").trim()

                    if (!sentReplacement) {
                        sendReplacementMessage(muteTime ?: "Unknown")
                        sentReplacement = true
                    }

                    false
                } else if (text.startsWith("Mute ID:")) {
                    reset()
                    false
                } else {
                    false
                }
            }
        }
    }

    private fun sendReplacementMessage(timeComponent: String) {
        val client = Minecraft.getInstance()
        val player = client.player ?: return

        player.sendSystemMessage(
            LarpBranding.prefixed(
                LarpBranding.text("You are muted for ", LarpBranding.WHITE)
                    .append(LarpBranding.text(timeComponent, LarpBranding.RED))
            )
        )
    }

    private fun reset() {
        inCandidateBlock = false
        confirmedMuteBlock = false
        sentReplacement = false
        muteTime = null
    }
}
