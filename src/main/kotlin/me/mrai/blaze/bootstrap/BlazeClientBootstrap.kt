package me.mrai.blaze.bootstrap

import me.mrai.blaze.Blaze
import me.mrai.blaze.command.BlazeClientCommands
import me.mrai.blaze.data.BlazeDataStore
import me.mrai.blaze.feature.autoclicker.AutoclickerController
import me.mrai.blaze.input.BlazeKeyBindings
import me.mrai.blaze.ui.font.BlazeText
import net.minecraft.client.Minecraft

object BlazeClientBootstrap {
    fun initialize() {
        Blaze.logger.info("Initializing BLAZE client bootstrap")
        BlazeDataStore.initialize()
        BlazeClientCommands.register()
        AutoclickerController.register()
        BlazeKeyBindings.register()
        Minecraft.getInstance().execute {
            BlazeText.prewarmClickGui()
        }
    }
}
