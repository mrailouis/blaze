package me.mrai.larpclient.bootstrap

import com.mojang.blaze3d.platform.InputConstants
import me.mrai.larpclient.ui.clickgui.NewClickGuiScreen
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper
import net.minecraft.client.KeyMapping
import net.minecraft.client.gui.screens.ChatScreen
import net.minecraft.resources.Identifier
import org.lwjgl.glfw.GLFW

object ClientKeyBindings {
    private lateinit var clickGuiKey: KeyMapping
    private lateinit var dotCommandKey: KeyMapping

    fun register() {
        val generalCategory = KeyMapping.Category(
            Identifier.fromNamespaceAndPath("larpclient", "general")
        )

        clickGuiKey = KeyMappingHelper.registerKeyMapping(
            KeyMapping(
                "key.larpclient.clickgui",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_RIGHT_SHIFT,
                generalCategory
            )
        )

        dotCommandKey = KeyMappingHelper.registerKeyMapping(
            KeyMapping(
                "key.larpclient.dot_command",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_PERIOD,
                generalCategory
            )
        )

        ClientTickEvents.END_CLIENT_TICK.register { client ->
            while (clickGuiKey.consumeClick()) {
                if (client.screen is NewClickGuiScreen) {
                    (client.screen as NewClickGuiScreen).requestClose()
                } else {
                    NewClickGuiScreen.open()
                }
            }

            while (dotCommandKey.consumeClick()) {
                if (client.screen == null) {
                    client.setScreen(ChatScreen(".", false))
                }
            }
        }
    }
}
