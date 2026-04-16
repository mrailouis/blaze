package me.mrai.larpclient.features.impl.shared

import me.mrai.larpclient.module.ComponentSetting
import me.mrai.larpclient.module.Module
import me.mrai.larpclient.module.ModuleCategory
import me.mrai.larpclient.module.SliderSetting

open class PlaceholderFeatureModule(
    name: String,
    description: String,
    category: ModuleCategory,
    placeholderText: String
) : Module(name, description, category) {
    protected val previewText = ComponentSetting("Placeholder Text", placeholderText)
    protected val previewScale = SliderSetting("Preview Scale", 1.0, 0.5, 4.0, 0.05)
    protected val previewAlpha = SliderSetting("Preview Alpha", 0.85, 0.0, 1.0, 0.05)

    init {
        settings += previewText
        settings += previewScale
        settings += previewAlpha
    }
}
