package me.mrai.larpclient.features.impl.misc.other.headsscale

import me.mrai.larpclient.module.Module
import me.mrai.larpclient.module.ModuleCategory
import me.mrai.larpclient.module.SliderSetting

object HeadsScaleModule : Module(
    name = "Heads Scale",
    description = "Scales head items in GUI renders.",
    category = ModuleCategory.MISC_OTHER
) {
    val scalePercent = SliderSetting("Scale Percent", 75.0, 10.0, 300.0, 5.0)

    init {
        settings += scalePercent
    }

    @JvmStatic
    fun scaleMultiplier(): Float = (scalePercent.value / 100.0).toFloat()
}
