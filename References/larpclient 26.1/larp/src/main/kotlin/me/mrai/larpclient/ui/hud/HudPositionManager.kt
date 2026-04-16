package me.mrai.larpclient.ui.hud

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import me.mrai.larpclient.util.LarpLog
import net.fabricmc.loader.api.FabricLoader
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

object HudPositionManager {

    private val gson = GsonBuilder().setPrettyPrinting().create()

    private val configPath: Path =
        FabricLoader.getInstance().configDir.resolve("larpclient/hud_positions.json")

    data class HudPoint(
        var x: Float,
        var y: Float
    )

    data class HudConfig(
        var scoreboard: HudPoint = HudPoint(8f, 8f),
        var moduleList: HudPoint = HudPoint(8f, 30f),
        var moduleListScale: Float = 1.0f,
        var autoClickerCps: HudPoint = HudPoint(8f, 52f),
        var autoClickerCpsScale: Float = 1.0f,
        var trueSplits: HudPoint = HudPoint(8f, 74f),
        var trueSplitsScale: Float = 1.0f,
        var trueSplitsBreakdown: HudPoint = HudPoint(220f, 74f),
        var trueSplitsBreakdownScale: Float = 1.0f,
        var archerUtilsTitle: HudPoint = HudPoint(140f, 120f),
        var archerUtilsTitleScale: Float = 1.0f,
        var lastBreathUtilsTitle: HudPoint = HudPoint(140f, 180f),
        var lastBreathUtilsTitleScale: Float = 1.0f,
        var maxorHpHud: HudPoint = HudPoint(140f, 150f),
        var maxorHpHudScale: Float = 1.0f,
        var kuudraPriority: HudPoint = HudPoint(8f, 96f),
        var kuudraPriorityScale: Float = 1.0f,
        var kuudraSpawnedCrates: HudPoint = HudPoint(170f, 96f),
        var kuudraSpawnedCratesScale: Float = 1.0f,
        var kuudraGoTo: HudPoint = HudPoint(8f, 140f),
        var kuudraGoToScale: Float = 1.0f,
        var buildProgressHud: HudPoint = HudPoint(8f, 190f),
        var buildProgressHudScale: Float = 1.0f,
        var deployableDisplay: HudPoint = HudPoint(8f, 162f),
        var deployableDisplayScale: Float = 1.0f,
        var golemTimerHud: HudPoint = HudPoint(8f, 214f),
        var golemTimerHudScale: Float = 1.0f,
        var performanceHud: HudPoint = HudPoint(8f, 238f),
        var performanceHudScale: Float = 1.0f,
        var kuudraDirectionHud: HudPoint = HudPoint(180f, 190f),
        var kuudraDirectionHudScale: Float = 1.3f,
        var blinkHud: HudPoint = HudPoint(8f, 170f),
        var blinkHudScale: Float = 1.0f
    )

    private var loaded = false

    private var backingConfig: HudConfig = HudConfig()

    var config: HudConfig
        get() {
            ensureLoaded()
            return backingConfig
        }
        private set(value) {
            backingConfig = value
        }

    init {
        load()
    }

    @Synchronized
    fun load() {
        try {
            Files.createDirectories(configPath.parent)

            config = if (Files.exists(configPath)) {
                val loaded = gson.fromJson(Files.readString(configPath), JsonObject::class.java)
                gson.fromJson(mergeWithDefaults(loaded), HudConfig::class.java) ?: HudConfig()
            } else {
                HudConfig()
            }

            loaded = true
            save()
        } catch (throwable: Throwable) {
            LarpLog.warn("Failed to load HUD positions from $configPath: ${throwable.message ?: throwable.javaClass.simpleName}")
            config = HudConfig()
            loaded = true
        }
    }

    @Synchronized
    fun save() {
        ensureLoaded()

        try {
            Files.createDirectories(configPath.parent)
            Files.writeString(
                configPath,
                gson.toJson(config),
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE
            )
        } catch (throwable: Throwable) {
            LarpLog.warn("Failed to save HUD positions to $configPath: ${throwable.message ?: throwable.javaClass.simpleName}")
        }
    }

    @Synchronized
    private fun ensureLoaded() {
        if (!loaded) {
            load()
        }
    }

    private fun mergeWithDefaults(loaded: JsonObject?): JsonObject {
        val merged = gson.toJsonTree(HudConfig()).asJsonObject
        if (loaded != null) {
            mergeInto(merged, loaded)
        }
        return merged
    }

    private fun mergeInto(base: JsonObject, overrides: JsonObject) {
        for ((key, value) in overrides.entrySet()) {
            if (value.isJsonNull) continue

            val baseValue = base.get(key)
            if (baseValue != null && baseValue.isJsonObject && value.isJsonObject) {
                mergeInto(baseValue.asJsonObject, value.asJsonObject)
            } else {
                base.add(key, value)
            }
        }
    }
}
