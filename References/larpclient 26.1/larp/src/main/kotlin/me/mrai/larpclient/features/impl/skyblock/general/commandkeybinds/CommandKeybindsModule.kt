package me.mrai.larpclient.features.impl.skyblock.general.commandkeybinds

import com.google.gson.GsonBuilder
import com.mojang.blaze3d.platform.InputConstants
import me.mrai.larpclient.module.KeybindSetting
import me.mrai.larpclient.module.Module
import me.mrai.larpclient.module.ModuleCategory
import me.mrai.larpclient.util.LarpLog
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.Minecraft
import org.lwjgl.glfw.GLFW
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.UUID

object CommandKeybindsModule : Module(
    name = "Command Keybinds",
    description = "Runs common SkyBlock commands from keybinds and supports custom command binds.",
    category = ModuleCategory.SKYBLOCK_GENERAL
) {
    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val configPath: Path =
        FabricLoader.getInstance().configDir.resolve("larpclient/command_keybinds.json")

    private val petsKeybind = KeybindSetting("Pets", GLFW.GLFW_KEY_UNKNOWN)
    private val storageKeybind = KeybindSetting("Storage", GLFW.GLFW_KEY_UNKNOWN)
    private val wardrobeKeybind = KeybindSetting("Wardrobe", GLFW.GLFW_KEY_UNKNOWN)
    private val equipmentKeybind = KeybindSetting("Equipment", GLFW.GLFW_KEY_UNKNOWN)
    private val dungeonHubKeybind = KeybindSetting("Dungeon Hub", GLFW.GLFW_KEY_UNKNOWN)
    private val potionBagKeybind = KeybindSetting("Potion Bag", GLFW.GLFW_KEY_UNKNOWN)

    data class CustomCommandKeybind(
        var id: String = UUID.randomUUID().toString(),
        var command: String = "/",
        var key: Int = GLFW.GLFW_KEY_UNKNOWN
    )

    private data class Config(
        var entries: MutableList<CustomCommandKeybind> = mutableListOf()
    )

    private val pressedKeys = hashSetOf<Int>()
    private var config = Config()

    init {
        settings += petsKeybind
        settings += storageKeybind
        settings += wardrobeKeybind
        settings += equipmentKeybind
        settings += dungeonHubKeybind
        settings += potionBagKeybind
        load()
    }

    override fun onEnable() {
        pressedKeys.clear()
    }

    override fun onDisable() {
        pressedKeys.clear()
    }

    override fun onTick() {
        if (!enabled) {
            pressedKeys.clear()
            return
        }

        val client = Minecraft.getInstance()
        val player = client.player ?: run {
            pressedKeys.clear()
            return
        }
        val connection = player.connection
        if (client.screen != null) {
            pressedKeys.clear()
            return
        }

        val window = client.window
        val builtins = listOf(
            StaticBind(petsKeybind.key, "pets"),
            StaticBind(storageKeybind.key, "storage"),
            StaticBind(wardrobeKeybind.key, "wardrobe"),
            StaticBind(equipmentKeybind.key, "equipment"),
            StaticBind(dungeonHubKeybind.key, "warp dungeon_hub"),
            StaticBind(potionBagKeybind.key, "potionbag")
        )

        for (bind in builtins) {
            if (bind.key == GLFW.GLFW_KEY_UNKNOWN) continue
            if (!isNewPress(window, bind.key)) continue
            connection.sendCommand(bind.command)
            return
        }

        for (entry in config.entries) {
            if (entry.key == GLFW.GLFW_KEY_UNKNOWN) continue
            if (!isNewPress(window, entry.key)) continue
            val command = normalizeCommand(entry.command)
            if (command.isBlank()) return
            connection.sendCommand(command)
            return
        }
    }

    fun getEntries(): List<CustomCommandKeybind> = config.entries

    fun addEntry() {
        config.entries += CustomCommandKeybind()
        save()
    }

    fun updateCommand(index: Int, command: String) {
        val entry = config.entries.getOrNull(index) ?: return
        entry.command = command
        save()
    }

    fun updateKey(index: Int, key: Int) {
        val entry = config.entries.getOrNull(index) ?: return
        entry.key = key
        save()
    }

    fun remove(index: Int) {
        config.entries.removeAt(index)
        save()
    }

    private fun normalizeCommand(raw: String): String {
        return raw.trim().removePrefix("/").trim()
    }

    private fun isNewPress(window: com.mojang.blaze3d.platform.Window, key: Int): Boolean {
        val isPressed = InputConstants.isKeyDown(window, key)
        val wasPressed = pressedKeys.contains(key)
        if (isPressed) pressedKeys += key else pressedKeys -= key
        return isPressed && !wasPressed
    }

    @Synchronized
    private fun load() {
        try {
            Files.createDirectories(configPath.parent)
            config = if (Files.exists(configPath)) {
                gson.fromJson(Files.readString(configPath), Config::class.java) ?: Config()
            } else {
                Config()
            }
            save()
        } catch (t: Throwable) {
            LarpLog.warn("Failed to load command keybind config from $configPath: ${t.message ?: t.javaClass.simpleName}")
            config = Config()
        }
    }

    @Synchronized
    private fun save() {
        try {
            Files.createDirectories(configPath.parent)
            Files.writeString(
                configPath,
                gson.toJson(config),
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE
            )
        } catch (t: Throwable) {
            LarpLog.warn("Failed to save command keybind config to $configPath: ${t.message ?: t.javaClass.simpleName}")
        }
    }

    private data class StaticBind(
        val key: Int,
        val command: String
    )
}
