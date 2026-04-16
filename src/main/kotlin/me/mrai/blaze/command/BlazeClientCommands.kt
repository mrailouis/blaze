package me.mrai.blaze.command

import com.mojang.brigadier.CommandDispatcher
import me.mrai.blaze.chat.BlazeChat
import me.mrai.blaze.data.BlazeDataStore
import me.mrai.blaze.ui.clickgui.BlazeClickGuiScreen
import me.mrai.blaze.ui.clickgui.BlazeEditHudsScreen
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource

object BlazeClientCommands {
    fun register() {
        ClientCommandRegistrationCallback.EVENT.register(::registerCommands)
    }

    private fun registerCommands(
        dispatcher: CommandDispatcher<FabricClientCommandSource>,
        @Suppress("UNUSED_PARAMETER") buildContext: net.minecraft.commands.CommandBuildContext
    ) {
        dispatcher.register(
            ClientCommandManager.literal("blaze")
                .executes { runIfEnabled(it.source) { openClickGui(it.source) } }
                .then(ClientCommandManager.literal("help").executes { runIfEnabled(it.source) { sendHelp(it.source) } })
                .then(ClientCommandManager.literal("toggle").executes { toggleBlaze(it.source) })
                .then(
                    ClientCommandManager.literal("splits")
                        .then(ClientCommandManager.literal("recent").executes { runIfEnabled(it.source) { sendSplits(it.source, recent = true) } })
                        .then(ClientCommandManager.literal("average").executes { runIfEnabled(it.source) { sendSplits(it.source, recent = false) } })
                )
                .then(
                    ClientCommandManager.literal("profit")
                        .executes { runIfEnabled(it.source) { sendProfit(it.source) } }
                        .then(ClientCommandManager.literal("reset").executes { runIfEnabled(it.source) { resetProfit(it.source) } })
                        .then(ClientCommandManager.literal("fullreset").executes { runIfEnabled(it.source) { fullResetProfit(it.source) } })
                )
                .then(ClientCommandManager.literal("cleandata").executes { runIfEnabled(it.source) { cleanData(it.source) } })
                .then(hudEditorAlias("edithuds"))
                .then(hudEditorAlias("hud"))
                .then(hudEditorAlias("gui"))
                .then(hudEditorAlias("edit"))
        )
    }

    private fun hudEditorAlias(name: String) = ClientCommandManager.literal(name)
        .executes { runIfEnabled(it.source) { openHudEditor(it.source) } }

    private fun runIfEnabled(source: FabricClientCommandSource, action: () -> Int): Int {
        if (!BlazeDataStore.state.enabled) {
            BlazeChat.error(source, "BLAZE is disabled. Use /blaze toggle to turn it back on.")
            return 0
        }
        return action()
    }

    private fun openClickGui(source: FabricClientCommandSource): Int {
        source.client.execute {
            BlazeClickGuiScreen.open()
        }
        return 1
    }

    private fun openHudEditor(source: FabricClientCommandSource): Int {
        source.client.execute {
            source.client.setScreen(BlazeEditHudsScreen())
        }
        BlazeChat.info(source, "Opened the HUD editor.")
        return 1
    }

    private fun sendHelp(source: FabricClientCommandSource): Int {
        BlazeChat.info(source, "/blaze - opens the main GUI")
        BlazeChat.info(source, "/blaze help - shows this command list")
        BlazeChat.info(source, "/blaze splits recent - sends the most recent boss splits")
        BlazeChat.info(source, "/blaze splits average - sends your average for every split")
        BlazeChat.info(source, "/blaze profit - sends your tracked profit")
        BlazeChat.info(source, "/blaze profit reset - resets the current profit session")
        BlazeChat.info(source, "/blaze profit fullreset - clears all stored profit data")
        BlazeChat.info(source, "/blaze cleandata - clears config, profit, and split data")
        BlazeChat.info(source, "/blaze edithuds|hud|gui|edit - opens the HUD editor")
        BlazeChat.info(source, "/blaze toggle - toggles Blaze systems on or off")
        return 1
    }

    private fun sendSplits(source: FabricClientCommandSource, recent: Boolean): Int {
        val header = if (recent) "Recent splits" else "Average splits"
        BlazeChat.info(source, header)
        val splits = if (recent) {
            BlazeDataStore.state.recentSplits.orEmpty()
        } else {
            BlazeDataStore.state.averageSplits.orEmpty()
        }
        splits.forEach { (name, value) ->
            source.sendFeedback(BlazeChat.prefixed(BlazeChat.text("$name: $value")))
        }
        return 1
    }

    private fun sendProfit(source: FabricClientCommandSource): Int {
        val state = BlazeDataStore.state
        BlazeChat.info(
            source,
            "Session profit: ${formatCoins(state.sessionProfit)} | Stored total: ${formatCoins(state.trackedProfit)}"
        )
        return 1
    }

    private fun resetProfit(source: FabricClientCommandSource): Int {
        BlazeDataStore.resetSessionProfit()
        BlazeChat.info(source, "Profit session reset.")
        return 1
    }

    private fun fullResetProfit(source: FabricClientCommandSource): Int {
        BlazeDataStore.fullResetProfit()
        BlazeChat.info(source, "Stored profit data fully reset.")
        return 1
    }

    private fun cleanData(source: FabricClientCommandSource): Int {
        BlazeDataStore.cleanData()
        closeBlazeScreens(source)
        BlazeChat.info(source, "All Blaze config, profit, and split data were cleared.")
        return 1
    }

    private fun toggleBlaze(source: FabricClientCommandSource): Int {
        val enabled = BlazeDataStore.toggleEnabled()
        if (!enabled) {
            closeBlazeScreens(source)
        }
        BlazeChat.info(source, if (enabled) "BLAZE enabled." else "BLAZE disabled. Only /blaze toggle remains active.")
        return 1
    }

    private fun closeBlazeScreens(source: FabricClientCommandSource) {
        source.client.execute {
            val screen = source.client.screen
            if (screen is BlazeClickGuiScreen || screen is BlazeEditHudsScreen) {
                source.client.setScreen(null)
            }
        }
    }

    private fun formatCoins(value: Long): String = "%,d".format(value) + " coins"
}
