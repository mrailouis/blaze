package me.mrai.larpclient.bootstrap

import me.mrai.larpclient.command.DotCommandRouter
import me.mrai.larpclient.command.LarpCommand
import me.mrai.larpclient.features.impl.dungeons.general.truesplits.TrueSplitsCommand
import me.mrai.larpclient.features.impl.dungeons.general.truesplits.TrueSplitsListener
import me.mrai.larpclient.features.impl.kuudra.p1.kuudrawaypoints.KuudraWaypointListener
import me.mrai.larpclient.features.impl.misc.ui.itemrarity.ItemRarityRenderer
import me.mrai.larpclient.features.impl.skyblock.general.muteidhider.MuteIdHiderListener
import me.mrai.larpclient.module.ModuleConfigManager
import me.mrai.larpclient.module.ModuleManager
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents

object ClientRegistrations {
    private var commandHooksRegistered = false

    fun registerCommandHooks() {
        if (commandHooksRegistered) return

        ClientCommandRegistrationCallback.EVENT.register { dispatcher, _ ->
            dispatcher.register(LarpCommand.build())
            dispatcher.register(TrueSplitsCommand.build())
        }

        ClientSendMessageEvents.ALLOW_CHAT.register { message ->
            !DotCommandRouter.handleChat(message)
        }

        ClientSendMessageEvents.ALLOW_COMMAND.register { command ->
            DotCommandRouter.handleSlashCommand(command)
        }

        commandHooksRegistered = true
    }

    fun initializeServices() {
        ModuleManager.bootstrap()
        ModuleConfigManager.load()
        ItemRarityRenderer.INSTANCE.init()
    }

    fun registerListenersAndCommands() {
        TrueSplitsListener.register()
        MuteIdHiderListener.register()
        KuudraWaypointListener.register()
    }
}
