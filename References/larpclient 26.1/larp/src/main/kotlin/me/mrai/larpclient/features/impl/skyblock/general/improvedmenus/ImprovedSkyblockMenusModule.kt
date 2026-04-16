package me.mrai.larpclient.features.impl.skyblock.general.improvedmenus

import me.mrai.larpclient.module.BoolSetting
import me.mrai.larpclient.module.InfoSetting
import me.mrai.larpclient.module.ModeSetting
import me.mrai.larpclient.module.Module
import me.mrai.larpclient.module.ModuleCategory

object ImprovedSkyblockMenusModule : Module(
    name = "Improved Skyblock Menus",
    description = "Ports NEU-style dynamic skyblock menu skins for chest menus.",
    category = ModuleCategory.SKYBLOCK_GENERAL
) {
    val hideEmptyPanes = BoolSetting("Hide Empty Panes", true)
    val style = ModeSetting("Style", "1", "2", "3", "4", "5", "6", "7")
    private val status = InfoSetting("Status") { ImprovedSkyblockMenusState.status() }

    init {
        settings += hideEmptyPanes
        settings += style
        settings += status
    }
}
