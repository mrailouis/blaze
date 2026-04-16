package me.mrai.larpclient.features.impl.dungeons.f7.p1.maxorhphud

import me.mrai.larpclient.mixin.BossHealthOverlayAccessor
import me.mrai.larpclient.mixin.GuiAccessor
import me.mrai.larpclient.module.BoolSetting
import me.mrai.larpclient.module.Module
import me.mrai.larpclient.module.ModuleCategory
import me.mrai.larpclient.module.SliderSetting
import me.mrai.larpclient.util.TextSanitizer
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.components.LerpingBossEvent
import kotlin.math.roundToInt

object MaxorHpHudModule : Module(
    name = "Maxor HP% HUD",
    description = "Shows Maxor boss bar HP as a movable HUD.",
    category = ModuleCategory.DUNGEONS_F7_P1
) {
    val bold = BoolSetting("Bold", true)
    val red = SliderSetting("Red", 255.0, 0.0, 255.0, 1.0)
    val green = SliderSetting("Green", 255.0, 0.0, 255.0, 1.0)
    val blue = SliderSetting("Blue", 255.0, 0.0, 255.0, 1.0)

    @Volatile
    private var currentPercentText: String? = null

    init {
        settings += listOf(bold, red, green, blue)
    }

    override fun onTick() {
        currentPercentText = findMaxorPercent()
    }

    fun getDisplayText(): String? = currentPercentText

    fun getColor(): Int {
        return ((255 and 255) shl 24) or
                ((red.value.roundToInt() and 255) shl 16) or
                ((green.value.roundToInt() and 255) shl 8) or
                (blue.value.roundToInt() and 255)
    }

    private fun findMaxorPercent(): String? {
        val client = Minecraft.getInstance()
        val gui = client.gui ?: return null
        val bossOverlay = (gui as? GuiAccessor)?.`larpclient$getBossOverlay`() ?: return null
        val events = (bossOverlay as? BossHealthOverlayAccessor)?.`larpclient$getEvents`() ?: return null

        for (event in events.values) {
            val bossEvent = event as? LerpingBossEvent ?: continue
            val name = TextSanitizer.compactLower(bossEvent.name.string)
            if (!name.contains("maxor")) continue
            val percent = (bossEvent.progress * 100f)
            return String.format(java.util.Locale.US, "%.1f%%", percent)
        }
        return null
    }
}
