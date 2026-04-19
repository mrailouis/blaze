package me.mrai.blaze.config

import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.LinkedHashMap
import me.mrai.blaze.Blaze
import me.mrai.blaze.feature.autoclicker.AutoclickerActivationMode
import me.mrai.blaze.feature.autoclicker.AutoclickerConfig
import me.mrai.blaze.feature.autoclicker.BlazeInputBind
import me.mrai.blaze.feature.autoclicker.BlazeInputType
import me.mrai.blaze.feature.autoclicker.SideAutoclickerConfig
import me.mrai.blaze.feature.blaze.BlazePathfinderConfig
import me.mrai.blaze.feature.module.BlazeCategory
import net.fabricmc.loader.api.FabricLoader

object BlazeDataStore {
    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val configDirectory: Path = FabricLoader.getInstance().configDir.resolve("blaze")
    private val stateFile: Path = configDirectory.resolve("client-state.json")

    @Volatile
    var state: BlazeClientState = defaultState()
        private set

    fun initialize() {
        state = runCatching(::loadState).getOrElse { throwable ->
            Blaze.logger.warn("Failed to load BLAZE client state, using defaults", throwable)
            defaultState()
        }
        save()
    }

    fun setEnabled(enabled: Boolean) {
        state = state.copy(enabled = enabled)
        save()
    }

    fun toggleEnabled(): Boolean {
        val next = !state.enabled
        setEnabled(next)
        return next
    }

    fun isModuleEnabled(moduleId: String): Boolean {
        return state.moduleStates?.get(moduleId) == true
    }

    fun toggleModule(moduleId: String): Boolean {
        val next = !isModuleEnabled(moduleId)
        setModuleEnabled(moduleId, next)
        return next
    }

    fun setModuleEnabled(moduleId: String, enabled: Boolean) {
        val updated = LinkedHashMap(state.moduleStates ?: defaultModuleStates()).apply {
            this[moduleId] = enabled
        }
        state = state.copy(moduleStates = updated)
        save()
    }

    fun updateAutoclicker(transform: (AutoclickerConfig) -> AutoclickerConfig) {
        state = state.copy(autoclicker = transform(state.autoclicker ?: defaultAutoclicker()))
        save()
    }

    fun updatePathfinder(transform: (BlazePathfinderConfig) -> BlazePathfinderConfig) {
        state = state.copy(pathfinder = transform(state.pathfinder ?: defaultPathfinder()))
        save()
    }

    fun resetSessionProfit() {
        state = state.copy(sessionProfit = 0L)
        save()
    }

    fun fullResetProfit() {
        state = state.copy(sessionProfit = 0L, trackedProfit = 0L)
        save()
    }

    fun cleanData() {
        state = defaultState()
        runCatching {
            Files.deleteIfExists(stateFile)
        }.onFailure { throwable ->
            Blaze.logger.warn("Failed to delete BLAZE client state file", throwable)
        }
    }

    private fun loadState(): BlazeClientState {
        if (!Files.exists(stateFile)) {
            return defaultState()
        }

        val loaded = Files.newBufferedReader(stateFile, StandardCharsets.UTF_8).use { reader ->
            gson.fromJson<BlazeClientState>(reader, object : TypeToken<BlazeClientState>() {}.type)
        }
        return normalizeState(loaded)
    }

    private fun save() {
        runCatching {
            Files.createDirectories(configDirectory)
            Files.newBufferedWriter(stateFile, StandardCharsets.UTF_8).use { writer ->
                gson.toJson(state, writer)
            }
        }.onFailure { throwable ->
            Blaze.logger.warn("Failed to save BLAZE client state", throwable)
        }
    }

    private fun defaultState(): BlazeClientState {
        return BlazeClientState(
            enabled = true,
            sessionProfit = 0L,
            trackedProfit = 0L,
            recentSplits = defaultSplitMap(),
            averageSplits = defaultSplitMap(),
            moduleStates = defaultModuleStates(),
            autoclicker = defaultAutoclicker(),
            pathfinder = defaultPathfinder()
        )
    }

    private fun normalizeState(loaded: BlazeClientState?): BlazeClientState {
        val defaults = defaultState()
        if (loaded == null) {
            return defaults
        }

        return BlazeClientState(
            enabled = loaded.enabled,
            sessionProfit = loaded.sessionProfit,
            trackedProfit = loaded.trackedProfit,
            recentSplits = mergeSplitMap(loaded.recentSplits),
            averageSplits = mergeSplitMap(loaded.averageSplits),
            moduleStates = mergeModuleStates(loaded.moduleStates),
            autoclicker = normalizeAutoclicker(loaded.autoclicker ?: defaults.autoclicker ?: defaultAutoclicker()),
            pathfinder = normalizePathfinder(loaded.pathfinder ?: defaults.pathfinder ?: defaultPathfinder())
        )
    }

    private fun mergeSplitMap(existing: LinkedHashMap<String, String>?): LinkedHashMap<String, String> {
        return defaultSplitMap().apply {
            existing?.forEach { (key, value) ->
                if (containsKey(key)) {
                    this[key] = value
                }
            }
        }
    }

    private fun mergeModuleStates(existing: LinkedHashMap<String, Boolean>?): LinkedHashMap<String, Boolean> {
        return defaultModuleStates().apply {
            existing?.forEach { (key, value) ->
                if (containsKey(key)) {
                    this[key] = value == true
                }
            }
        }
    }

    private fun defaultModuleStates(): LinkedHashMap<String, Boolean> {
        return linkedMapOf<String, Boolean>().apply {
            BlazeCategory.entries
                .flatMap { it.modules }
                .forEach { module -> put(module.id, false) }
        }
    }

    private fun defaultAutoclicker(): AutoclickerConfig {
        return AutoclickerConfig(
            left = SideAutoclickerConfig(
                enabled = true,
                cps = 10,
                activationMode = AutoclickerActivationMode.HOLD,
                bind = BlazeInputBind(BlazeInputType.KEYSYM, com.mojang.blaze3d.platform.InputConstants.KEY_F6)
            ),
            right = SideAutoclickerConfig(
                enabled = true,
                cps = 10,
                activationMode = AutoclickerActivationMode.HOLD,
                bind = BlazeInputBind(BlazeInputType.KEYSYM, com.mojang.blaze3d.platform.InputConstants.KEY_F7)
            )
        )
    }

    private fun defaultPathfinder(): BlazePathfinderConfig {
        return BlazePathfinderConfig()
    }

    private fun normalizeAutoclicker(config: AutoclickerConfig): AutoclickerConfig {
        return config.copy(
            left = normalizeSide(config.left),
            right = normalizeSide(config.right)
        )
    }

    private fun normalizePathfinder(config: BlazePathfinderConfig): BlazePathfinderConfig {
        return config.copy(
            rotationSpeedMultiplier = config.rotationSpeedMultiplier.coerceIn(0.4, 5.0),
            rotationSmoothness = config.rotationSmoothness.coerceIn(0.5, 2.5),
            movementLookBias = config.movementLookBias.coerceIn(0.1, 1.0),
            headFreedomDegrees = config.headFreedomDegrees.coerceIn(10.0, 45.0)
        )
    }

    private fun normalizeSide(side: SideAutoclickerConfig): SideAutoclickerConfig {
        return side.copy(
            enabled = side.enabled,
            cps = side.cps.coerceIn(0, 30),
            bind = side.bind.copy(
                type = side.bind.type,
                value = side.bind.value
            )
        )
    }

    private fun defaultSplitMap(): LinkedHashMap<String, String> {
        return linkedMapOf(
            "Time to spawn" to "Not recorded",
            "1/3 boss" to "Not recorded",
            "Demon 1 (1/3)" to "Not recorded",
            "Demon 2 (1/3)" to "Not recorded",
            "2/3 boss" to "Not recorded",
            "Demon 1 (2/3)" to "Not recorded",
            "Demon 2 (2/3)" to "Not recorded",
            "3/3 boss" to "Not recorded",
            "Total kill time" to "Not recorded",
            "Overall time" to "Not recorded"
        )
    }
}

data class BlazeClientState(
    val enabled: Boolean = true,
    val sessionProfit: Long = 0L,
    val trackedProfit: Long = 0L,
    val recentSplits: LinkedHashMap<String, String>? = null,
    val averageSplits: LinkedHashMap<String, String>? = null,
    val moduleStates: LinkedHashMap<String, Boolean>? = null,
    val autoclicker: AutoclickerConfig? = null,
    val pathfinder: BlazePathfinderConfig? = null
)
