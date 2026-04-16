package me.mrai.larpclient.features.impl.misc.ui.itemrarity

import me.mrai.larpclient.module.BoolSetting
import me.mrai.larpclient.module.Module
import me.mrai.larpclient.module.ModuleCategory
import me.mrai.larpclient.module.SliderSetting

object ItemRarityModule : Module(
    name = "Item Rarity",
    description = "Draws rarity-colored backgrounds behind item icons in inventories and the hotbar.",
    category = ModuleCategory.MISC_UI
) {
    val alphaPercent = SliderSetting("Alpha Percent", 45.0, 0.0, 100.0, 1.0)
    val renderHotbar = BoolSetting("Hotbar", true)

    init {
        settings += listOf(alphaPercent, renderHotbar)
    }
}
