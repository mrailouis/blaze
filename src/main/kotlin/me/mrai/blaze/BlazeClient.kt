package me.mrai.blaze

import me.mrai.blaze.bootstrap.BlazeClientBootstrap
import net.fabricmc.api.ClientModInitializer

object BlazeClient : ClientModInitializer {
    override fun onInitializeClient() {
        BlazeClientBootstrap.initialize()
    }
}
