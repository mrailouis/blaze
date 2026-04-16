package me.mrai.larpclient.features.impl.skyblock.general.storagegui

import me.mrai.larpclient.module.BoolSetting
import me.mrai.larpclient.module.InfoSetting
import me.mrai.larpclient.module.ModeSetting
import me.mrai.larpclient.module.Module
import me.mrai.larpclient.module.ModuleCategory
import me.mrai.larpclient.module.SliderSetting

object StorageGuiModule : Module(
    name = "Storage Gui",
    description = "Ports the full NEU-style storage GUI with cached previews and page navigation.",
    category = ModuleCategory.SKYBLOCK_GENERAL
) {
    val viewHeight = SliderSetting("View Height", 188.0, 104.0, 320.0, 54.0)
    val style = ModeSetting("Style", "0", "1", "2", "3")
    val hoverPreview = BoolSetting("Hover Preview", true)
    val backpackPreview = BoolSetting("Backpack Preview", true)
    val enderChestPreview = BoolSetting("Ender Chest Preview", true)
    val masonryMode = BoolSetting("Compact Vertically", false)
    val searchAutofocus = BoolSetting("Search Autofocus", false)
    private val status = InfoSetting("Status") { StorageGuiManager.status() }

    init {
        settings += viewHeight
        settings += style
        settings += hoverPreview
        settings += backpackPreview
        settings += enderChestPreview
        settings += masonryMode
        settings += searchAutofocus
        settings += status
    }

    override fun onDisable() {
        StorageGuiManager.reset()
    }
}
