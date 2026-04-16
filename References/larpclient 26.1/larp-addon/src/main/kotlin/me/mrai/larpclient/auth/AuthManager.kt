package me.mrai.larpclient.auth

import com.google.gson.Gson
import com.google.gson.JsonObject
import me.mrai.larpclient.util.LarpLog
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.Minecraft
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import javax.net.ssl.SSLParameters

object AuthManager {
    private val gson = Gson()

    private val sslParameters = SSLParameters().apply {
        protocols = arrayOf("TLSv1.2", "TLSv1.3")
    }

    private val httpClient: HttpClient = HttpClient.newBuilder()
        .version(HttpClient.Version.HTTP_1_1)
        .sslParameters(sslParameters)
        .connectTimeout(Duration.ofSeconds(10))
        .build()
    private val authExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "larpclient-auth").apply { isDaemon = true }
    }
    private var authFuture: CompletableFuture<AuthResult>? = null
    private var shownFailureScreen = false

    var state: AuthState = AuthState.UNCHECKED
        private set

    var denialReason: String? = null
        private set

    private data class AuthResult(
        val state: AuthState,
        val denialReason: String? = null
    )

    fun beginAuthentication() {
        if (FabricLoader.getInstance().isDevelopmentEnvironment) {
            state = AuthState.AUTHENTICATED
            denialReason = null
            shownFailureScreen = false
            LarpLog.info("Development environment detected, skipping authentication.")
            return
        }

        val currentFuture = authFuture
        if (state == AuthState.IN_PROGRESS && currentFuture != null && !currentFuture.isDone) {
            return
        }

        state = AuthState.IN_PROGRESS
        denialReason = null
        shownFailureScreen = false
        authFuture = CompletableFuture.supplyAsync(::performAuthentication, authExecutor)
    }

    fun retryAuthentication() {
        beginAuthentication()
    }

    fun poll(client: Minecraft): Boolean {
        val future = authFuture
        if (future != null && future.isDone) {
            val result = runCatching { future.join() }
                .getOrElse { throwable ->
                    LarpLog.error("Authentication attempt crashed.", throwable)
                    AuthResult(AuthState.DENIED, "Authentication request crashed: ${throwable.message ?: throwable.javaClass.simpleName}")
                }
            finish(result)
            authFuture = null
        }

        when (state) {
            AuthState.DENIED -> {
                if (!shownFailureScreen && client.screen !is AuthFailureScreen) {
                    client.setScreen(AuthFailureScreen())
                    shownFailureScreen = true
                }
            }

            AuthState.AUTHENTICATED -> {
                if (client.screen is AuthFailureScreen) {
                    client.setScreen(null)
                }
            }

            else -> Unit
        }

        return state == AuthState.AUTHENTICATED
    }

    private fun finish(result: AuthResult) {
        state = result.state
        denialReason = result.denialReason
        when (result.state) {
            AuthState.AUTHENTICATED -> {
                shownFailureScreen = false
                LarpLog.info("Authenticated successfully.")
            }

            AuthState.DENIED -> {
                LarpLog.warn("Authentication denied: ${result.denialReason}")
            }

            else -> Unit
        }
    }

    private fun performAuthentication(): AuthResult {
        val config = LicenseConfigManager.load()
        val client = Minecraft.getInstance()
        val user = client.user

        val licenseKey = config.licenseKey.trim()
        if (licenseKey.isEmpty()) {
            return denied("No license key found in config/larpclient-license.json")
        }

        val uuid = user.profileId.toString().trim()
        if (uuid.isEmpty()) {
            return denied("Unable to read your Minecraft UUID from the current session.")
        }

        val username = user.name.trim()
        val modVersion = FabricLoader.getInstance()
            .getModContainer("larpclient")
            .map { it.metadata.version.friendlyString }
            .orElse("unknown")

        val response = runCatching {
            authenticate(
                authUrl = AuthEndpoints.AUTH_URL,
                uuid = uuid,
                licenseKey = licenseKey,
                minecraftName = username,
                modVersion = modVersion
            )
        }.getOrElse { throwable ->
            val detail = throwable.message ?: throwable.javaClass.simpleName
            return denied("Authentication request failed.\n\n$detail")
        }

        if (!response.allowed) {
            return denied(response.reason)
        }

        return AuthResult(AuthState.AUTHENTICATED)
    }

    private fun denied(reason: String): AuthResult {
        return AuthResult(
            state = AuthState.DENIED,
            denialReason = "Not authenticated.\n\nReason: $reason"
        )
    }

    internal fun buildAuthRequestBody(
        uuid: String,
        licenseKey: String,
        minecraftName: String,
        modVersion: String
    ): String {
        return JsonObject().apply {
            addProperty("uuid", uuid)
            addProperty("key", licenseKey)
            addProperty("minecraftName", minecraftName)
            addProperty("modVersion", modVersion)
        }.toString()
    }

    private fun authenticate(
        authUrl: String,
        uuid: String,
        licenseKey: String,
        minecraftName: String,
        modVersion: String
    ): AuthResponse {
        if (authUrl.isBlank()) {
            throw IllegalStateException("authUrl is blank in larpclient-license.json")
        }

        val body = buildAuthRequestBody(
            uuid = uuid,
            licenseKey = licenseKey,
            minecraftName = minecraftName,
            modVersion = modVersion
        )

        val request = HttpRequest.newBuilder()
            .uri(URI.create(authUrl))
            .timeout(Duration.ofSeconds(12))
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .header("User-Agent", "LarpClient/$modVersion")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build()

        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())

        if (response.statusCode() !in 200..299) {
            throw IllegalStateException("Auth server returned HTTP ${response.statusCode()}: ${response.body()}")
        }

        return gson.fromJson(response.body(), AuthResponse::class.java)
            ?: throw IllegalStateException("Auth server returned an empty response")
    }

    @Suppress("unused")
    fun requireAuthentication() {
        beginAuthentication()
    }
}
