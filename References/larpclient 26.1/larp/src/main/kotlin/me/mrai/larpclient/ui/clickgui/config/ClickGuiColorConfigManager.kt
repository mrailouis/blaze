package me.mrai.larpclient.ui.clickgui.config

import com.google.gson.GsonBuilder
import me.mrai.larpclient.util.LarpLog
import net.fabricmc.loader.api.FabricLoader
import java.nio.file.Files

object ClickGuiColorConfigManager {
    data class ColorConfig(
        var backgroundColor: Int = 0xFF101114.toInt(),
        var topBarColor: Int = 0xFF121419.toInt(),
        var cardColor: Int = 0xFF1C1F24.toInt(),
        var cardHoverColor: Int = 0xFF23272D.toInt(),
        var dividerColor: Int = 0x22FFFFFF,
        var textColor: Int = 0xFFF4F4F4.toInt(),
        var softComponentColor: Int = 0xFF9B9EA5.toInt(),
        var moduleEnabledColor: Int = 0xFF48E4E7.toInt(),
        var accentColor: Int = 0xFF48E4E7.toInt(),
        var accentOutlineColor: Int = 0x882BD9DE.toInt(),
        var accentOutlineStrongColor: Int = 0xCC48E4E7.toInt(),
        var toggleOnColor: Int = 0xFF183D41.toInt(),
        var toggleOffColor: Int = 0xFF26282D.toInt(),
        var sliderInactiveColor: Int = 0xFF384049.toInt(),
        var moduleListGradientStartColor: Int = 0xFF48E4E7.toInt(),
        var moduleListGradientEndColor: Int = 0xFF8B5CF6.toInt(),
        var scoreboardBackgroundColor: Int = 0xFF101114.toInt(),
        var scoreboardBorderGradientStartColor: Int = 0xFF48E4E7.toInt(),
        var scoreboardBorderGradientEndColor: Int = 0xFFB04DFF.toInt(),
        var backgroundAlpha: Float = 1.0f,
        var moduleAlpha: Float = 1.0f
    )

    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val dir = FabricLoader.getInstance().configDir.resolve("larpclient")
    private val file = dir.resolve("clickgui-colors.json")

    var config: ColorConfig = load()

    private fun load(): ColorConfig {
        return try {
            if (!Files.exists(dir)) Files.createDirectories(dir)
            if (!Files.exists(file)) {
                val created = ColorConfig()
                save(created)
                created
            } else {
                Files.newBufferedReader(file).use { reader ->
                    gson.fromJson(reader, ColorConfig::class.java) ?: ColorConfig()
                }
            }
        } catch (throwable: Throwable) {
            LarpLog.warn("Failed to load Click GUI color config from $file: ${throwable.message ?: throwable.javaClass.simpleName}")
            ColorConfig()
        }
    }

    fun save() {
        save(config)
    }

    private fun save(value: ColorConfig) {
        try {
            if (!Files.exists(dir)) Files.createDirectories(dir)
            Files.newBufferedWriter(file).use { writer ->
                gson.toJson(value, writer)
            }
        } catch (throwable: Throwable) {
            LarpLog.warn("Failed to save Click GUI color config to $file: ${throwable.message ?: throwable.javaClass.simpleName}")
        }
    }
}
