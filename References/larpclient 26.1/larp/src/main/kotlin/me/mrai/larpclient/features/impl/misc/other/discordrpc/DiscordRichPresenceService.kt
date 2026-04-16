package me.mrai.larpclient.features.impl.misc.other.discordrpc

import com.jagrosh.discordipc.IPCClient
import com.jagrosh.discordipc.entities.ActivityType
import com.jagrosh.discordipc.entities.RichPresence
import me.mrai.larpclient.util.LarpLog

class DiscordRichPresenceService(
    private val applicationId: Long,
    private val detailsProvider: () -> String,
    private val stateProvider: () -> String,
    private val largeImageKeyProvider: () -> String?,
    private val largeImageComponentProvider: () -> String?,
    private val showSessionTimeProvider: () -> Boolean
) {
    private var client: IPCClient? = null
    private var connected = false
    private var startedAtEpochSeconds: Long? = null

    private var lastDetails: String? = null
    private var lastState: String? = null
    private var lastLargeImageKey: String? = null
    private var lastLargeImageComponent: String? = null
    private var lastShowSessionTime: Boolean? = null

    fun start() {
        if (applicationId <= 0L) {
            LarpLog.warn("Discord Rich Presence not started because the app ID is invalid.")
            return
        }

        if (client != null) {
            LarpLog.debug("Discord Rich Presence client already exists.")
            return
        }

        startedAtEpochSeconds = System.currentTimeMillis() / 1000L

        val ipc = IPCClient(applicationId)
        client = ipc

        try {
            LarpLog.info("Connecting to Discord IPC.")
            ipc.connect()

            connected = true
            LarpLog.info("Connected to Discord IPC.")

            pushPresence(force = true)
        } catch (t: Throwable) {
            LarpLog.error("Failed to connect to Discord IPC.", t)
            stop()
        }
    }

    fun tick() {
        if (!connected) return

        pushPresence(force = false)
    }

    fun stop() {
        connected = false
        lastDetails = null
        lastState = null
        lastLargeImageKey = null
        lastLargeImageComponent = null
        lastShowSessionTime = null

        try {
            client?.close()
        } catch (t: Throwable) {
            LarpLog.warn("Error while closing Discord IPC client: ${t.message}")
        }

        client = null
        startedAtEpochSeconds = null
    }

    private fun pushPresence(force: Boolean) {
        val details = detailsProvider().take(128)
        val state = stateProvider().take(128)
        val largeImageKey = largeImageKeyProvider()?.takeIf { it.isNotBlank() }
        val largeImageComponent = largeImageComponentProvider()?.takeIf { it.isNotBlank() }
        val showSessionTime = showSessionTimeProvider()

        if (!force &&
            details == lastDetails &&
            state == lastState &&
            largeImageKey == lastLargeImageKey &&
            largeImageComponent == lastLargeImageComponent &&
            showSessionTime == lastShowSessionTime
        ) {
            LarpLog.debug("Skipping Discord presence update because nothing changed.")
            return
        }

        val builder = RichPresence.Builder()
            .setActivityType(ActivityType.Playing)
            .setDetails(details)
            .setState(state)

        if (showSessionTime) {
            startedAtEpochSeconds?.let { builder.setStartTimestamp(it) }
        }

        if (largeImageKey != null) {
            builder.setLargeImage(largeImageKey, largeImageComponent ?: "LarpClient")
        }

        try {
            client?.sendRichPresence(builder.build())
            LarpLog.debug(
                "Discord presence updated (details=$details, state=$state, image=$largeImageKey, session=$showSessionTime)."
            )

            lastDetails = details
            lastState = state
            lastLargeImageKey = largeImageKey
            lastLargeImageComponent = largeImageComponent
            lastShowSessionTime = showSessionTime
        } catch (t: Throwable) {
            LarpLog.error("Failed to send Discord Rich Presence update.", t)
            connected = false
        }
    }
}
