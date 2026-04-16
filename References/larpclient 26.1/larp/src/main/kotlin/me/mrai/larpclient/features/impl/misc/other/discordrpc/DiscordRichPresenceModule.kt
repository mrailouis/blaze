package me.mrai.larpclient.features.impl.misc.other.discordrpc

import me.mrai.larpclient.module.BoolSetting
import me.mrai.larpclient.module.Module
import me.mrai.larpclient.module.ModuleCategory
import me.mrai.larpclient.module.SliderSetting
import me.mrai.larpclient.util.LarpLog
import net.minecraft.client.Minecraft

class DiscordRichPresenceModule : Module(
    name = "Discord Rich Presence",
    description = "Shows your LarpClient activity on Discord.",
    category = ModuleCategory.MISC_OTHER
) {
    companion object {
        private const val APPLICATION_ID = 1486704714916691978L
    }

    private val showUsername = BoolSetting("Show Username", false)
    private val showServer = BoolSetting("Show Server", false)
    private val showWorldType = BoolSetting("Show World Type", false)
    private val showHeldItem = BoolSetting("Show Held Item", true)
    private val showSessionTime = BoolSetting("Show Session Time", true)
    private val useLargeImage = BoolSetting("Use Large Image", true)
    private val updateInterval = SliderSetting("Update Interval", 5.0, 2.0, 30.0, 1.0)

    private val service = DiscordRichPresenceService(
        applicationId = APPLICATION_ID,
        detailsProvider = { buildDetails() },
        stateProvider = { buildState() },
        largeImageKeyProvider = { if (useLargeImage.value) "icon" else null },
        largeImageComponentProvider = { if (useLargeImage.value) "icon" else null },
        showSessionTimeProvider = { showSessionTime.value }
    )

    private var ticksUntilUpdate = 0

    init {
        settings += showUsername
        settings += showServer
        settings += showWorldType
        settings += showHeldItem
        settings += showSessionTime
        settings += useLargeImage
        settings += updateInterval
    }

    override fun onEnable() {
        LarpLog.info("Discord Rich Presence enabled")
        ticksUntilUpdate = 0
        service.start()
    }

    override fun onDisable() {
        LarpLog.info("Discord Rich Presence disabled")
        service.stop()
    }

    override fun onTick() {
        ticksUntilUpdate--

        if (ticksUntilUpdate > 0) return

        val intervalTicks = (updateInterval.value * 20.0).toInt().coerceAtLeast(20)
        LarpLog.debug("Updating Discord presence (intervalTicks=$intervalTicks)")

        ticksUntilUpdate = intervalTicks
        service.tick()
    }

    private fun findHeldItemName(): String? {
        val client = Minecraft.getInstance()
        val player = client.player ?: return null

        val stack = player.getMainHandItem()
        if (stack.isEmpty) return null

        val rawName = stack.hoverName.string
        val cleanName = cleanDisplayComponent(rawName)

        return cleanName.takeIf { it.isNotBlank() }
    }

    private fun cleanDisplayComponent(line: String): String {
        return line
            .replace(Regex("§."), "")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun buildDetails(): String {
        val client = Minecraft.getInstance()

        if (client.player == null) {
            return "Larping in the menu"
        }

        if (showHeldItem.value) {
            findHeldItemName()?.let { return it }
        }

        val parts = mutableListOf<String>()

        if (showServer.value) {
            currentServerLabel(client)?.let(parts::add)
        }

        if (showWorldType.value) {
            parts += currentWorldTypeLabel(client)
        }

        return if (parts.isEmpty()) "Using LarpClient" else parts.joinToString(" • ")
    }

    private fun buildState(): String {
        val client = Minecraft.getInstance()

        if (client.player == null) {
            return "Somewhere and Nowhere"
        }

        val parts = mutableListOf<String>()

        if (showUsername.value) {
            parts += client.user.name
        }

        if (!showHeldItem.value) {
            findHeldItemName()?.let(parts::add)
        }

        if (showServer.value) {
            currentServerLabel(client)?.let(parts::add)
        }

        if (showWorldType.value) {
            parts += currentWorldTypeLabel(client)
        }

        return if (parts.isEmpty()) "Using LarpClient" else parts.joinToString(" • ")
    }

    private fun currentServerLabel(client: Minecraft): String? {
        return when {
            client.hasSingleplayerServer() -> "Solo Larping"
            client.currentServer != null -> client.currentServer!!.ip.substringBefore(":")
            else -> null
        }
    }

    private fun currentWorldTypeLabel(client: Minecraft): String {
        return when {
            client.player == null -> "Where am I?"
            client.hasSingleplayerServer() -> "Solo Larping"
            client.currentServer != null -> "Larping with friends!"
            else -> "Somewhere?"
        }
    }
}
