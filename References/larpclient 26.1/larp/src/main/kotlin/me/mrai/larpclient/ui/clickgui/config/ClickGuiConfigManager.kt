package me.mrai.larpclient.ui.clickgui.config

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import me.mrai.larpclient.util.LarpLog
import net.fabricmc.loader.api.FabricLoader
import java.nio.file.Files

object ClickGuiConfigManager {
    data class LayoutConfig(
        var panelX: Float = Float.NaN,
        var panelY: Float = Float.NaN,
        var panelManuallyMoved: Boolean = false,
        var uiScale: Float = 0.65f,
        var selectedCategory: String = "SKYBLOCK_GENERAL",
        var elementSpacing: Float = 12f,
        var showToasts: Boolean = true,
        var worldGlow: Boolean = false
    )

    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val dir = FabricLoader.getInstance().configDir.resolve("larpclient")
    private val file = dir.resolve("clickgui.json")

    var config: LayoutConfig = load()

    private fun load(): LayoutConfig {
        return try {
            if (!Files.exists(dir)) Files.createDirectories(dir)
            if (!Files.exists(file)) {
                val created = LayoutConfig()
                save(created)
                created
            } else {
                val raw = Files.readString(file)
                val parsed = gson.fromJson(raw, LayoutConfig::class.java) ?: LayoutConfig()
                val json = gson.fromJson(raw, JsonObject::class.java)
                if (json == null || !json.has("showToasts")) {
                    parsed.showToasts = true
                }
                if (json == null || !json.has("worldGlow")) {
                    parsed.worldGlow = false
                }
                if (json == null || !json.has("panelManuallyMoved")) {
                    parsed.panelManuallyMoved = false
                }
                parsed
            }
        } catch (throwable: Throwable) {
            LarpLog.warn("Failed to load Click GUI layout config from $file: ${throwable.message ?: throwable.javaClass.simpleName}")
            LayoutConfig()
        }
    }

    fun save() {
        save(config)
    }

    private fun save(value: LayoutConfig) {
        try {
            if (!Files.exists(dir)) Files.createDirectories(dir)
            Files.newBufferedWriter(file).use { writer ->
                gson.toJson(value, writer)
            }
        } catch (throwable: Throwable) {
            LarpLog.warn("Failed to save Click GUI layout config to $file: ${throwable.message ?: throwable.javaClass.simpleName}")
        }
    }
}
