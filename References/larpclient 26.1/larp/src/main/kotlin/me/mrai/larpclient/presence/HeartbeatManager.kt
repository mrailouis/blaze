package me.mrai.larpclient.presence

import com.google.gson.JsonObject
import me.mrai.larpclient.playeroverride.PlayerOverrideManager
import me.mrai.larpclient.util.LarpLog
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.Minecraft
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import javax.net.ssl.SSLParameters

object HeartbeatManager {
    private const val HEARTBEAT_INTERVAL_TICKS = 20 * 10

    private val sslParameters = SSLParameters().apply {
        protocols = arrayOf("TLSv1.2", "TLSv1.3")
    }

    private val httpClient: HttpClient = HttpClient.newBuilder()
        .version(HttpClient.Version.HTTP_1_1)
        .sslParameters(sslParameters)
        .connectTimeout(Duration.ofSeconds(10))
        .build()

    private var running = false
    private var ticksUntilNextHeartbeat = 0
    private var clientType = HeartbeatClientType.MOD
    private var modId = "larp"
    private var userAgentProduct = "Larp"
    private var licenseKeyProvider: (() -> String?) = { null }
    private var playerCustomizationProvider: (() -> PlayerCustomizationPayload?) = { null }
    private var lastCustomizationFingerprint: String? = null

    data class PlayerCustomizationPayload(
        val customName: String?,
        val scaleX: Float,
        val scaleY: Float,
        val scaleZ: Float,
        val showToOthers: Boolean,
        val applyChanges: Boolean
    ) {
        fun fingerprint(): String {
            return listOf(
                customName?.trim().orEmpty(),
                scaleX.toString(),
                scaleY.toString(),
                scaleZ.toString(),
                showToOthers.toString(),
                applyChanges.toString()
            ).joinToString("|")
        }
    }

    fun configure(
        clientType: HeartbeatClientType,
        modId: String,
        userAgentProduct: String,
        licenseKeyProvider: () -> String? = { null },
        playerCustomizationProvider: () -> PlayerCustomizationPayload? = { null }
    ) {
        this.clientType = clientType
        this.modId = modId
        this.userAgentProduct = userAgentProduct
        this.licenseKeyProvider = licenseKeyProvider
        this.playerCustomizationProvider = playerCustomizationProvider
    }

    fun start() {
        running = true
        ticksUntilNextHeartbeat = 1
    }

    fun stop() {
        running = false
        ticksUntilNextHeartbeat = 0
    }

    fun tick() {
        if (!running) return

        val currentFingerprint = playerCustomizationProvider()?.fingerprint()
        if (currentFingerprint != lastCustomizationFingerprint) {
            ticksUntilNextHeartbeat = 0
        }

        ticksUntilNextHeartbeat--
        if (ticksUntilNextHeartbeat > 0) return

        ticksUntilNextHeartbeat = HEARTBEAT_INTERVAL_TICKS
        sendHeartbeat()
    }

    private fun sendHeartbeat() {
        val client = Minecraft.getInstance()
        val user = client.user
        val uuid = user.profileId.toString().trim()
        val username = user.name.trim()
        val heartbeatUrl = PresenceEndpoints.HEARTBEAT_URL

        if (uuid.isEmpty() || heartbeatUrl.isBlank()) return

        val modVersion = FabricLoader.getInstance()
            .getModContainer(modId)
            .map { it.metadata.version.friendlyString }
            .orElse("unknown")

        val body = buildHeartbeatRequestBody(
            uuid = uuid,
            licenseKey = licenseKeyProvider()?.trim()?.ifBlank { null },
            minecraftName = username,
            modVersion = modVersion,
            clientType = clientType,
            playerCustomization = playerCustomizationProvider()
        )
        lastCustomizationFingerprint = playerCustomizationProvider()?.fingerprint()

        val request = runCatching {
            HttpRequest.newBuilder()
                .uri(URI.create(heartbeatUrl))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("User-Agent", "$userAgentProduct/$modVersion")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build()
        }.getOrElse { throwable ->
            LarpLog.error("Failed to build heartbeat request.", throwable)
            return
        }

        httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
            .whenComplete { response, throwable ->
                when {
                    throwable != null -> LarpLog.warn("Heartbeat request failed: ${throwable.message ?: throwable.javaClass.simpleName}")
                    response.statusCode() !in 200..299 -> LarpLog.warn("Heartbeat returned HTTP ${response.statusCode()}.")
                    else -> PlayerOverrideManager.updateFromHeartbeatResponse(response.body())
                }
            }
    }

    fun buildHeartbeatRequestBody(
        uuid: String,
        licenseKey: String?,
        minecraftName: String,
        modVersion: String,
        clientType: HeartbeatClientType,
        playerCustomization: PlayerCustomizationPayload? = null
    ): String {
        return JsonObject().apply {
            addProperty("uuid", uuid)
            addProperty("clientType", clientType.wireValue)
            addProperty("minecraftName", minecraftName)
            addProperty("modVersion", modVersion)
            if (!licenseKey.isNullOrBlank()) {
                addProperty("key", licenseKey)
            }
            if (playerCustomization != null) {
                add("selfCustomization", JsonObject().apply {
                    addProperty("customName", playerCustomization.customName?.trim()?.ifBlank { null })
                    addProperty("scaleX", playerCustomization.scaleX)
                    addProperty("scaleY", playerCustomization.scaleY)
                    addProperty("scaleZ", playerCustomization.scaleZ)
                    addProperty("showToOthers", playerCustomization.showToOthers)
                    addProperty("applyChanges", playerCustomization.applyChanges)
                })
            }
        }.toString()
    }

    fun requestImmediateHeartbeat() {
        if (!running) return
        ticksUntilNextHeartbeat = 0
    }
}
