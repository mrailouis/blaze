package me.mrai.larpclient

import me.mrai.larpclient.bootstrap.ClientBootstrap
import net.fabricmc.api.ClientModInitializer

object LarpClientClient : ClientModInitializer {
    override fun onInitializeClient() {
        ClientBootstrap.initialize()
    }
}
