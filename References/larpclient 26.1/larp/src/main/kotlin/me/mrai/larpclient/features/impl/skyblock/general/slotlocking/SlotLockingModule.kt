package me.mrai.larpclient.features.impl.skyblock.general.slotlocking

import me.mrai.larpclient.module.BoolSetting
import me.mrai.larpclient.module.KeybindSetting
import me.mrai.larpclient.module.Module
import me.mrai.larpclient.module.ModuleCategory
import me.mrai.larpclient.module.SliderSetting
import org.lwjgl.glfw.GLFW

object SlotLockingModule : Module(
    name = "Slot Locking",
    description = "Ports NEU-style slot locking and slot binding into inventories.",
    category = ModuleCategory.SKYBLOCK_GENERAL
) {
    val lockKey = KeybindSetting("Lock Key", GLFW.GLFW_KEY_L)
    val enableBinding = BoolSetting("Slot Binding", true)
    val bindingAlsoLocks = BoolSetting("Binding Also Locks", true)
    val lockSound = BoolSetting("Lock Sound", true)
    val soundVolume = SliderSetting("Sound Volume", 65.0, 0.0, 100.0, 1.0)

    init {
        settings += lockKey
        settings += enableBinding
        settings += bindingAlsoLocks
        settings += lockSound
        settings += soundVolume
    }
}
