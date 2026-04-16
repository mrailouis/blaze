package me.mrai.larpclient.features.impl.skyblock.general.preventcursorreset

import me.mrai.larpclient.module.Module
import me.mrai.larpclient.module.ModuleCategory

object PreventCursorResetModule : Module(
    name = "Prevent Cursor Reset",
    description = "Prevents cursor position from resetting when switching GUIs",
    category = ModuleCategory.SKYBLOCK_GENERAL
)

object PreventCursorResetState {
    private var lastX: Double = 0.0
    private var lastY: Double = 0.0

    fun saveCursorPosition(x: Double, y: Double) {
        lastX = x
        lastY = y
    }

    fun getLastMouseX(): Double = lastX
    fun getLastMouseY(): Double = lastY
}
