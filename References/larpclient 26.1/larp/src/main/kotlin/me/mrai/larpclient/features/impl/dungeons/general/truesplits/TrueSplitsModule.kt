package me.mrai.larpclient.features.impl.dungeons.general.truesplits

import me.mrai.larpclient.module.BoolSetting
import me.mrai.larpclient.module.ComponentSetting
import me.mrai.larpclient.module.Module
import me.mrai.larpclient.module.ModuleCategory
import me.mrai.larpclient.module.SliderSetting

object TrueSplitsModule : Module(
    name = "True Splits",
    description = "Over-engineered M7 dungeon splits with real time, tick time and breakdown HUDs.",
    category = ModuleCategory.DUNGEONS_GENERAL
) {
    val showMainHud = BoolSetting("Show Main Splits", true)
    val showBreakdownHud = BoolSetting("Show Breakdown", true)
    val underlineActive = BoolSetting("Underline Active Split", true)
    val sendBreakdownsInChat = BoolSetting("Clickable Breakdown Chat", true)

    val breakdownScrollLength = SliderSetting("Breakdown Scroll Length", 6.0, 6.0, 12.0, 1.0)
    val breakdownScrollDelay = SliderSetting("Breakdown Scroll Delay", 5.0, 0.0, 15.0, 1.0)

    val pbArrowColor = ComponentSetting("PB Arrow Color", "#FFAA00")
    val normalArrowColor = ComponentSetting("Normal Arrow Color", "#FFAA00")

    val bloodColor = ComponentSetting("Blood Split Color", "#55FF55")
    val watcherColor = ComponentSetting("Watcher Split Color", "#55FFFF")
    val portalColor = ComponentSetting("Portal Split Color", "#FF55FF")
    val maxorColor = ComponentSetting("Maxor Split Color", "#AA00AA")
    val stormColor = ComponentSetting("Storm Split Color", "#00AAAA")
    val terminalsColor = ComponentSetting("Terminals Split Color", "#FFAA00")
    val goldorColor = ComponentSetting("Goldor Split Color", "#FFAA00")
    val necronColor = ComponentSetting("Necron Split Color", "#FF5555")
    val dragonsColor = ComponentSetting("Dragons Split Color", "#AA0000")

    init {
        settings += showMainHud
        settings += showBreakdownHud
        settings += underlineActive
        settings += sendBreakdownsInChat
        settings += breakdownScrollLength
        settings += breakdownScrollDelay
        settings += pbArrowColor
        settings += normalArrowColor
        settings += bloodColor
        settings += watcherColor
        settings += portalColor
        settings += maxorColor
        settings += stormColor
        settings += terminalsColor
        settings += goldorColor
        settings += necronColor
        settings += dragonsColor
    }

    override fun onEnable() {
        TrueSplitsState.resetAll()
    }

    override fun onDisable() {
        TrueSplitsState.resetAll()
    }

    internal fun parseColor(setting: ComponentSetting, fallback: Int): Int {
        val raw = setting.text.trim()
        if (raw.isEmpty()) return fallback
        return try {
            val hex = raw.removePrefix("#")
            when (hex.length) {
                6 -> (0xFF shl 24) or hex.toLong(16).toInt()
                8 -> hex.toLong(16).toInt()
                else -> fallback
            }
        } catch (_: Throwable) {
            fallback
        }
    }

    fun mainSplitColor(index: Int): Int {
        return when (index) {
            0 -> parseColor(bloodColor, 0xFF55FF55.toInt())
            1 -> parseColor(watcherColor, 0xFF55FFFF.toInt())
            2 -> parseColor(portalColor, 0xFFFF55FF.toInt())
            3 -> parseColor(maxorColor, 0xFFAA00AA.toInt())
            4 -> parseColor(stormColor, 0xFF00AAAA.toInt())
            5 -> parseColor(terminalsColor, 0xFFFFAA00.toInt())
            6 -> parseColor(goldorColor, 0xFFFFAA00.toInt())
            7 -> parseColor(necronColor, 0xFFFF5555.toInt())
            8 -> parseColor(dragonsColor, 0xFFAA0000.toInt())
            else -> 0xFFFFFFFF.toInt()
        }
    }

    fun normalArrow(): Int = parseColor(normalArrowColor, 0xFFFFAA00.toInt())
    fun pbArrow(): Int = parseColor(pbArrowColor, 0xFFFFAA00.toInt())
}