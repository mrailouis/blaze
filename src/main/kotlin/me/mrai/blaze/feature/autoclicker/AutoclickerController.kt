package me.mrai.blaze.feature.autoclicker

import com.mojang.blaze3d.platform.InputConstants
import me.mrai.blaze.data.BlazeDataStore
import me.mrai.blaze.mixin.MinecraftInvoker
import me.mrai.blaze.ui.clickgui.model.BlazeModuleIds
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Minecraft
import org.lwjgl.glfw.GLFW

object AutoclickerController {
    private val toggled = mutableMapOf(
        AutoclickerSide.LEFT to false,
        AutoclickerSide.RIGHT to false
    )
    private val lastBindDown = mutableMapOf(
        AutoclickerSide.LEFT to false,
        AutoclickerSide.RIGHT to false
    )
    private val lastClickNanos = mutableMapOf(
        AutoclickerSide.LEFT to 0L,
        AutoclickerSide.RIGHT to 0L
    )

    fun register() {
        ClientTickEvents.END_CLIENT_TICK.register(::tick)
    }

    private fun tick(client: Minecraft) {
        val state = BlazeDataStore.state
        val moduleEnabled = BlazeDataStore.isModuleEnabled(BlazeModuleIds.AUTOCLICKER)
        val config = state.autoclicker ?: return

        if (!state.enabled || !moduleEnabled || client.player == null || client.level == null || client.gameMode == null) {
            syncPhysicalStates(client, config)
            return
        }

        if (client.screen != null || !client.isWindowActive) {
            syncPhysicalStates(client, config)
            return
        }

        processSide(client, AutoclickerSide.LEFT, config.left)
        processSide(client, AutoclickerSide.RIGHT, config.right)
    }

    private fun processSide(client: Minecraft, side: AutoclickerSide, config: SideAutoclickerConfig) {
        if (!config.enabled) {
            return
        }
        val currentDown = isBindDown(client, config.bind)
        val wasDown = lastBindDown.getValue(side)

        if (config.activationMode == AutoclickerActivationMode.TOGGLE && currentDown && !wasDown) {
            toggled[side] = !(toggled[side] ?: false)
        }

        lastBindDown[side] = currentDown
        if (config.cps <= 0 || !isActive(side, config, currentDown)) {
            return
        }

        val intervalNanos = 1_000_000_000L / config.cps.coerceAtLeast(1)
        val now = System.nanoTime()
        val previous = lastClickNanos.getValue(side)
        if (now - previous < intervalNanos) {
            return
        }

        val invoker = client as MinecraftInvoker
        when (side) {
            AutoclickerSide.LEFT -> invoker.`blaze$startAttack`()
            AutoclickerSide.RIGHT -> invoker.`blaze$startUseItem`()
        }
        lastClickNanos[side] = now
    }

    private fun isActive(side: AutoclickerSide, config: SideAutoclickerConfig, currentDown: Boolean): Boolean {
        return when (config.activationMode) {
            AutoclickerActivationMode.HOLD -> currentDown
            AutoclickerActivationMode.TOGGLE -> toggled.getValue(side)
        }
    }

    private fun syncPhysicalStates(client: Minecraft, config: AutoclickerConfig) {
        lastBindDown[AutoclickerSide.LEFT] = isBindDown(client, config.left.bind)
        lastBindDown[AutoclickerSide.RIGHT] = isBindDown(client, config.right.bind)
    }

    private fun isBindDown(client: Minecraft, bind: BlazeInputBind): Boolean {
        if (!bind.isBound()) {
            return false
        }

        return when (bind.type) {
            BlazeInputType.KEYSYM -> InputConstants.isKeyDown(client.window, bind.value)
            BlazeInputType.MOUSE -> GLFW.glfwGetMouseButton(client.window.handle(), bind.value) == GLFW.GLFW_PRESS
        }
    }
}
