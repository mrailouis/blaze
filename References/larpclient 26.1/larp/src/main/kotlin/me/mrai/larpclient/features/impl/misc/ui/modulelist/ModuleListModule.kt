package me.mrai.larpclient.features.impl.misc.ui.modulelist
	
import me.mrai.larpclient.module.ComponentSetting
import me.mrai.larpclient.module.Module
import me.mrai.larpclient.module.ModuleCategory
	
class ModuleListModule : Module(
    name = "Module List",
    description = "Shows enabled modules in a sliding list on the top right.",
    category = ModuleCategory.MISC_UI
) {
    val gradientStartColor = ComponentSetting("Gradient Start Color", "#FF48E4E7")
    val gradientEndColor = ComponentSetting("Gradient End Color", "#FF8B5CF6")

    init {
        settings += gradientStartColor
        settings += gradientEndColor
    }
}
