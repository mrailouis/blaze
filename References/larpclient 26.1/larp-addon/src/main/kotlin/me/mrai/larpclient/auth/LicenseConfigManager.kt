package me.mrai.larpclient.auth

import com.google.gson.GsonBuilder
import me.mrai.larpclient.util.LarpLog
import net.fabricmc.loader.api.FabricLoader
import java.nio.file.Files
import java.nio.file.Path

object LicenseConfigManager {
    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val configPath: Path = FabricLoader.getInstance().configDir.resolve("larpclient-license.json")

    fun load(): LicenseConfig {
        ensureExists()

        return runCatching {
            Files.newBufferedReader(configPath).use { reader ->
                gson.fromJson(reader, LicenseConfig::class.java) ?: LicenseConfig()
            }
        }.getOrElse {
            LarpLog.warn("Failed to read license config from $configPath: ${it.message ?: it.javaClass.simpleName}")
            LicenseConfig()
        }
    }

    private fun ensureExists() {
        val parent = configPath.parent
        if (parent != null && Files.notExists(parent)) {
            Files.createDirectories(parent)
        }

        if (Files.notExists(configPath)) {
            Files.newBufferedWriter(configPath).use { writer ->
                gson.toJson(LicenseConfig(), writer)
            }
        }
    }
}
