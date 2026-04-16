package me.mrai.larpclient.features.impl.kuudra.p2.buildprogress

import me.mrai.larpclient.module.BoolSetting
import me.mrai.larpclient.module.Module
import me.mrai.larpclient.module.ModuleCategory
import me.mrai.larpclient.module.SliderSetting
import me.mrai.larpclient.util.TextSanitizer
import net.minecraft.client.Minecraft
import net.minecraft.world.entity.decoration.ArmorStand

object BuildProgressDisplayModule : Module(
    name = "Build Progress Display",
    description = "Displays Kuudra build progress as a movable HUD.",
    category = ModuleCategory.KUUDRA_P2
) {
    private val bold = BoolSetting("Bold", true)
    private val red = SliderSetting("Red", 85.0, 0.0, 255.0, 1.0)
    private val green = SliderSetting("Green", 255.0, 0.0, 255.0, 1.0)
    private val blue = SliderSetting("Blue", 255.0, 0.0, 255.0, 1.0)

    private val progressRegex = Regex("""Building Progress:?\s*(\d+)%""", RegexOption.IGNORE_CASE)

    @Volatile
    private var currentText: String? = null

    @Volatile
    private var lastSeenAtMs: Long = 0L

    init {
        settings += listOf(bold, red, green, blue)
    }

    override fun onDisable() {
        currentText = null
        lastSeenAtMs = 0L
    }

    override fun onTick() {
        val level = Minecraft.getInstance().level ?: run {
            currentText = null
            return
        }

        var progress: Int? = null
        for (entity in level.entitiesForRendering()) {
            val stand = entity as? ArmorStand ?: continue
            val name = stand.customName?.string ?: continue
            val stripped = TextSanitizer.stripFormatting(name)
            val match = progressRegex.find(stripped) ?: continue
            progress = match.groupValues.getOrNull(1)?.toIntOrNull()
            if (progress != null) break
        }

        if (progress != null) {
            currentText = "Build: ${progress}%"
            lastSeenAtMs = System.currentTimeMillis()
        } else if (System.currentTimeMillis() - lastSeenAtMs > 1500L) {
            currentText = null
        }
    }

    fun getDisplayText(): String? = currentText

    fun getColor(): Int {
        return ((255 and 255) shl 24) or
            ((red.value.toInt() and 255) shl 16) or
            ((green.value.toInt() and 255) shl 8) or
            (blue.value.toInt() and 255)
    }

    fun isBold(): Boolean = bold.value
}
