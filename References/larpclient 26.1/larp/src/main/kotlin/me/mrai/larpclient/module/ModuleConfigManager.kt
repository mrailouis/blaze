package me.mrai.larpclient.module

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import me.mrai.larpclient.util.LarpLog
import net.fabricmc.loader.api.FabricLoader
import java.nio.file.Files

object ModuleConfigManager {
    private const val SCHEMA_VERSION = 1

    private val gson = GsonBuilder().setPrettyPrinting().create()
    private fun dir() = FabricLoader.getInstance().configDir.resolve("larpclient")
    private fun file() = dir().resolve("modules.json")

    fun load(modules: List<Module> = ModuleManager.modules) {
        val dir = dir()
        val file = file()
        try {
            if (!Files.exists(dir)) Files.createDirectories(dir)
            if (!Files.exists(file)) {
                save()
                return
            }

            val root = Files.newBufferedReader(file).use { reader ->
                gson.fromJson(reader, JsonObject::class.java)
            } ?: run {
                LarpLog.warn("Module config at $file was empty. Keeping defaults.")
                return
            }

            applyJson(root, modules)
        } catch (throwable: Throwable) {
            LarpLog.error("Failed to load module config from $file.", throwable)
        }
    }

    fun save() {
        val dir = dir()
        val file = file()
        try {
            if (!Files.exists(dir)) Files.createDirectories(dir)
            Files.newBufferedWriter(file).use { writer ->
                gson.toJson(toJson(ModuleManager.modules), writer)
            }
        } catch (throwable: Throwable) {
            LarpLog.error("Failed to save module config to $file.", throwable)
        }
    }

    internal fun toJson(modules: List<Module>): JsonObject {
        val root = JsonObject()
        root.addProperty("schemaVersion", SCHEMA_VERSION)

        val modulesArray = JsonArray()
        for (module in modules) {
            val obj = JsonObject()
            obj.addProperty("name", module.name)
            obj.addProperty("enabled", module.enabled)
            obj.addProperty("bindKey", module.bindKey)

            val settingsObj = JsonObject()
            for (setting in module.settings) {
                when (setting) {
                    is BoolSetting -> settingsObj.addProperty(setting.name, setting.value)
                    is SliderSetting -> settingsObj.addProperty(setting.name, setting.value)
                    is ModeSetting -> settingsObj.addProperty(setting.name, setting.selected)
                    is ComponentSetting -> settingsObj.addProperty(setting.name, setting.text)
                    is KeybindSetting -> settingsObj.addProperty(setting.name, setting.key)
                }
            }

            obj.add("settings", settingsObj)
            modulesArray.add(obj)
        }

        root.add("modules", modulesArray)
        return root
    }

    internal fun applyJson(root: JsonObject, modules: List<Module>) {
        val schemaVersion = root.get("schemaVersion")?.asInt ?: 0
        if (schemaVersion > SCHEMA_VERSION) {
            LarpLog.warn("Module config schema $schemaVersion is newer than supported schema $SCHEMA_VERSION.")
        }

        val modulesByName = modules.associateBy { it.name }
        val modulesArray = root.getAsJsonArray("modules") ?: return
        val desiredEnabledState = linkedMapOf<Module, Boolean>()

        for (element in modulesArray) {
            val obj = element.asJsonObject
            val moduleName = obj.get("name")?.asString ?: continue
            val module = modulesByName[moduleName] ?: run {
                LarpLog.debug("Skipping unknown module in config: $moduleName")
                continue
            }

            if (obj.has("enabled")) {
                desiredEnabledState[module] = obj.get("enabled").asBoolean
            }
            if (obj.has("bindKey")) module.bindKey = obj.get("bindKey").asInt

            val settingsObj = obj.getAsJsonObject("settings") ?: continue
            applySettings(module, settingsObj)
        }

        desiredEnabledState.forEach { (module, enabled) ->
            module.enabled = enabled
        }
    }

    private fun applySettings(module: Module, settingsObj: JsonObject) {
        for (setting in module.settings) {
            val value = settingsObj.get(setting.name) ?: continue
            try {
                when (setting) {
                    is BoolSetting -> setting.value = value.asBoolean
                    is SliderSetting -> setting.value = value.asDouble
                    is ModeSetting -> applyModeSetting(setting, value)
                    is ComponentSetting -> setting.text = value.asString
                    is KeybindSetting -> setting.key = value.asInt
                }
            } catch (throwable: Throwable) {
                LarpLog.warn("Failed to load setting '${setting.name}' for module '${module.name}': ${throwable.message ?: throwable.javaClass.simpleName}")
            }
        }
    }

    private fun applyModeSetting(setting: ModeSetting, value: JsonElement) {
        val saved = value.asString
        val index = setting.modes.indexOf(saved)
        if (index >= 0) {
            setting.selectedIndex = index
            return
        }

        LarpLog.warn("Ignoring unknown mode '$saved' for setting '${setting.name}'.")
    }
}
