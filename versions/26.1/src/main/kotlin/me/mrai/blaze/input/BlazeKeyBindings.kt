package me.mrai.blaze.input

import com.mojang.blaze3d.platform.InputConstants
import me.mrai.blaze.Blaze
import me.mrai.blaze.config.BlazeDataStore
import me.mrai.blaze.platform.BlazeIdentifiers
import me.mrai.blaze.ui.clickgui.BlazeClickGuiScreen
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper
import net.minecraft.client.KeyMapping
import org.lwjgl.glfw.GLFW

object BlazeKeyBindings {
    private val generalCategory = KeyMapping.Category(BlazeIdentifiers.of(Blaze.MOD_ID, "general"))

    private lateinit var clickGuiKey: KeyMapping

    fun register() {
        clickGuiKey = KeyMappingHelper.registerKeyMapping(
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
