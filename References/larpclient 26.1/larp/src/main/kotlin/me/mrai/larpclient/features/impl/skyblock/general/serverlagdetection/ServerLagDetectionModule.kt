package me.mrai.larpclient.features.impl.skyblock.general.serverlagdetection

import me.mrai.larpclient.module.Module
import me.mrai.larpclient.module.ModuleCategory
import me.mrai.larpclient.util.LarpBranding
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import java.util.Locale
import kotlin.math.max

object ServerLagDetectionModule : Module(
    name = "Server Lag Detection",
    description = "Tracks time lost to lag in Kuudra and Dungeons.",
    category = ModuleCategory.SKYBLOCK_GENERAL
) {
    private enum class RunType {
        NONE,
        KUUDRA,
        DUNGEON
    }

    private var runType = RunType.NONE
    private var running = false

    private var startNano = 0L
    private var trackedTicks = 0

    override fun onEnable() {
        resetTracking()
    }

    override fun onDisable() {
        resetTracking()
    }

    override fun onTick() {
        if (!running) return

        val mc = Minecraft.getInstance()
        if (mc.player == null || mc.level == null) {
            resetTracking()
            return
        }

        trackedTicks++
    }

    fun onSystemChat(message: String) {
        if (!enabled) return
        val clean = stripFormatting(message)

        if (!running) {
            if (isKuudraStart(clean)) {
                begin(RunType.KUUDRA)
                return
            }

            if (isDungeonStart(clean)) {
                begin(RunType.DUNGEON)
                return
            }

            return
        }

        when (runType) {
            RunType.KUUDRA -> {
                if (isKuudraEnd(clean)) {
                    finishAndReport()
                }
            }

            RunType.DUNGEON -> {
                if (isDungeonEnd(clean)) {
                    finishAndReport()
                }
            }

            RunType.NONE -> {}
        }
    }

    private fun begin(type: RunType) {
        runType = type
        running = true
        trackedTicks = 0
        startNano = System.nanoTime()
    }

    private fun finishAndReport() {
        if (!running || startNano == 0L) {
            resetTracking()
            return
        }

        val elapsedNano = System.nanoTime() - startNano
        val realSeconds = elapsedNano / 1_000_000_000.0
        val tickSeconds = trackedTicks / 20.0
        val lostSeconds = max(0.0, realSeconds - tickSeconds)
        val formatted = formatSeconds(lostSeconds)

        sendClientMessage("Time lost to lag ${formatted}s.")
        sendPartyChat(formatted)

        resetTracking()
    }

    private fun sendClientMessage(message: String) {
        val mc = Minecraft.getInstance()
        val player = mc.player ?: return

        player.sendSystemMessage(LarpBranding.prefixed(message))
    }

    private fun sendPartyChat(formattedSeconds: String) {
        val connection = Minecraft.getInstance().connection ?: return
        connection.sendCommand("pc [${LarpBranding.BRAND_NAME}] Time lost to lag ${formattedSeconds}s.")
    }

    private fun formatSeconds(value: Double): String {
        return String.format(Locale.US, "%.2f", value)
    }

    private fun resetTracking() {
        runType = RunType.NONE
        running = false
        startNano = 0L
        trackedTicks = 0
    }

    private fun stripFormatting(text: String): String {
        return text.replace(Regex("§[0-9A-FK-ORa-fk-or]"), "")
    }

    private fun isKuudraStart(text: String): Boolean {
        return text.contains("[NPC] Elle: Okay adventurers, I will go and fish up Kuudra!")
    }

    private fun isKuudraEnd(text: String): Boolean {
        return text.contains("KUUDRA DOWN!")
    }

    private fun isDungeonStart(text: String): Boolean {
        return text.contains("[NPC] Mort: Here, I found this map when I first entered the dungeon.")
    }

    private fun isDungeonEnd(text: String): Boolean {
        return text.contains("> EXTRA STATS <")
    }
}
