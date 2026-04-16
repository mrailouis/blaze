package me.mrai.larpclient.module

import me.mrai.larpclient.ui.toast.ToastManager
import net.minecraft.client.Minecraft
import org.lwjgl.glfw.GLFW

open class Module(
    val name: String,
    val description: String,
    val category: ModuleCategory
) {
    val settings = mutableListOf<Setting>()

    var enabled: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            if (value) onEnable() else onDisable()
        }

    var bindKey: Int = GLFW.GLFW_KEY_UNKNOWN

    open fun onEnable() {}
    open fun onDisable() {}
    open fun onTick() {}
    open fun onRender() {}

    fun toggle() {
        enabled = !enabled
        ModuleConfigManager.save()
        ToastManager.show(name, if (enabled) "Enabled" else "Disabled")
    }

    fun handleKeyPressed(key: Int) {
        if (bindKey != GLFW.GLFW_KEY_UNKNOWN && key == bindKey) {
            toggle()
        }
    }

    protected fun client(): Minecraft = Minecraft.getInstance()
}