package me.mrai.larpclient.bootstrap

import me.mrai.larpclient.features.impl.misc.ui.playercustomisations.PlayerCustomisationsModule
import me.mrai.larpclient.presence.HeartbeatClientType
import me.mrai.larpclient.presence.HeartbeatManager

object ClientBootstrap {
    fun initialize() {
        HeartbeatManager.configure(
            clientType = HeartbeatClientType.MOD,
            modId = "larpclient",
            userAgentProduct = "Larp",
            playerCustomizationProvider = PlayerCustomisationsModule::heartbeatPayload
        )
        HeartbeatManager.start()
        ClientRenderEvents.register()
        ClientLifecycleEvents.register()
        ClientKeyBindings.register()
        ClientRegistrations.registerCommandHooks()
        ClientRegistrations.initializeServices()
        ClientRegistrations.registerListenersAndCommands()
    }
}
