package me.mrai.blaze.input

import com.mojang.blaze3d.platform.InputConstants
import me.mrai.blaze.Blaze
import me.mrai.blaze.data.BlazeDataStore
import me.mrai.blaze.ui.clickgui.BlazeClickGuiScreen
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper
import net.minecraft.client.KeyMapping
import net.minecraft.resources.Identifier
import org.lwjgl.glfw.GLFW

object BlazeKeyBindings {
    private val generalCategory = KeyMapping.Category.register(
        Identifier.fromNamespaceAndPath(Blaze.MOD_ID, "general")
    )

    private lateinit var clickGuiKey: KeyMapping

    fun register() {
        clickGuiKey = KeyBindingHelper.registerKeyBinding(
            KeyMapping(
                "key.blaze.click_gui",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_RIGHT_SHIFT,
                generalCategory
            )
        )

        ClientTickEvents.END_CLIENT_TICK.register { client ->
            while (clickGuiKey.consumeClick()) {
                if (!BlazeDataStore.state.enabled) {
                    continue
                }
                if (client.screen is BlazeClickGuiScreen) {
                    client.setScreen(null)
                } else {
                    BlazeClickGuiScreen.open()
                }
            }
        }
    }
}
