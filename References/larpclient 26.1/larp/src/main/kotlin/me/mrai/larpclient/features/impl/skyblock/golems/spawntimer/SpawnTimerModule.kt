package me.mrai.larpclient.features.impl.skyblock.golems.spawntimer

import me.mrai.larpclient.features.impl.skyblock.golems.GolemTrackerState
import me.mrai.larpclient.module.BoolSetting
import me.mrai.larpclient.module.Module
import me.mrai.larpclient.module.ModuleCategory
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext

object SpawnTimerModule : Module(
    name = "Spawn Timer",
    description = "Shows the End Stone Protector spawn timer as a movable HUD.",
    category = ModuleCategory.SKYBLOCK_GOLEMS
) {
    private val showTimerText = BoolSetting("Show Timer Text", true)

    init {
        settings += showTimerText
    }

    fun render(@Suppress("UNUSED_PARAMETER") context: LevelRenderContext) {
    }

    fun getDisplayText(): String? {
        if (!enabled || !showTimerText.value) return null
        return GolemTrackerState.getCountdownText()
    }

    fun getDisplayColor(): Int {
        val text = getDisplayText() ?: return 0xFFFFFFFF.toInt()
        return if (text.startsWith("-")) 0xFFFF5555.toInt() else 0xFF55FFFF.toInt()
    }
}
