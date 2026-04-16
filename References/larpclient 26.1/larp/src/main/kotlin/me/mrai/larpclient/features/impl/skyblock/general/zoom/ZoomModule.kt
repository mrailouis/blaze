package me.mrai.larpclient.features.impl.skyblock.general.zoom

import com.mojang.blaze3d.platform.InputConstants
import me.mrai.larpclient.module.KeybindSetting
import me.mrai.larpclient.module.Module
import me.mrai.larpclient.module.ModuleCategory
import me.mrai.larpclient.module.SliderSetting
import net.minecraft.client.Minecraft
import org.lwjgl.glfw.GLFW

object ZoomModule : Module(
    name = "Zoom",
    description = "OptiFine-style hold zoom with configurable key and zoom factor.",
    category = ModuleCategory.SKYBLOCK_GENERAL
) {
    private const val MIN_FOV = 30
    private const val MAX_FOV = 110

    val zoomKey = KeybindSetting("Zoom Key", GLFW.GLFW_KEY_C)
    private val zoomFactor = SliderSetting("Zoom Factor", 4.0, 1.5, 12.0, 0.25)
    private var originalFov: Int? = null

    init {
        settings += listOf(zoomKey, zoomFactor)
    }

    override fun onDisable() {
        restoreFov()
    }

    override fun onTick() {
        val client = Minecraft.getInstance()
        val option = client.options.fov()
        val current = (option.get() as? Number)?.toInt() ?: return

        if (isZoomActive()) {
            if (originalFov == null) {
                originalFov = current
            }
            val base = originalFov ?: current
            val zoomed = (base / zoomFactor.value).toInt().coerceIn(MIN_FOV, MAX_FOV)
            if (current != zoomed) {
                option.set(zoomed)
            }
        } else {
            restoreFov()
        }
    }

    fun isZoomActive(): Boolean {
        if (!enabled) return false
        val client = Minecraft.getInstance()
        if (client.screen != null || client.player == null || client.level == null) return false
        val key = zoomKey.key
        if (key == InputConstants.UNKNOWN.value) return false
        val window = client.window
        return InputConstants.isKeyDown(window, key)
    }

    private fun restoreFov() {
        val value = originalFov ?: return
        val client = Minecraft.getInstance()
        val option = client.options.fov()
        val current = (option.get() as? Number)?.toInt()
        val clamped = value.coerceIn(MIN_FOV, MAX_FOV)
        if (current != clamped) {
            option.set(clamped)
        }
        originalFov = null
    }
}
