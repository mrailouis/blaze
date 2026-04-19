package me.mrai.blaze.command

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.IntegerArgumentType
import me.mrai.blaze.chat.BlazeChat
import me.mrai.blaze.config.BlazeDataStore
import me.mrai.blaze.feature.blaze.BlazePathfindController
import me.mrai.blaze.ui.clickgui.BlazeClickGuiScreen
import me.mrai.blaze.ui.clickgui.BlazeEditHudsScreen
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import net.fabricmc.fabric.api.client.command.v2.ClientCommands
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
import net.minecraft.core.BlockPos

object BlazeClientCommands {
    fun register() {
        ClientCommandRegistrationCallback.EVENT.register(::registerCommands)
    }

    private fun registerCommands(
        dispatcher: CommandDispatcher<FabricClientCommandSource>,
        @Suppress("UNUSED_PARAMETER") buildContext: net.minecraft.commands.CommandBuildContext
    ) {
        dispatcher.register(
            ClientCommands.literal("blaze")
                .executes { runIfEnabled(it.source) { openClickGui(it.source) } }
                .then(ClientCommands.literal("help").executes { runIfEnabled(it.source) { sendHelp(it.source) } })
                .then(ClientCommands.literal("toggle").executes { toggleBlaze(it.source) })
                .then(
                    ClientCommands.literal("splits")
                        .then(ClientCommands.literal("recent").executes { runIfEnabled(it.source) { sendSplits(it.source, recent = true) } })
                        .then(ClientCommands.literal("average").executes { runIfEnabled(it.source) { sendSplits(it.source, recent = false) } })
                )
                .then(
                    ClientCommands.literal("profit")
                        .executes { runIfEnabled(it.source) { sendProfit(it.source) } }
                        .then(ClientCommands.literal("reset").executes { runIfEnabled(it.source) { resetProfit(it.source) } })
                        .then(ClientCommands.literal("fullreset").executes { runIfEnabled(it.source) { fullResetProfit(it.source) } })
                )
                .then(ClientCommands.literal("cleandata").executes { runIfEnabled(it.source) { cleanData(it.source) } })
                .then(
                    ClientCommands.literal("pathfind")
                        .executes { runIfEnabled(it.source) { pathfindToNearestBlaze(it.source) } }
                        .then(
                            ClientCommands.argument("x", IntegerArgumentType.integer())
                                .then(
                                    ClientCommands.argument("y", IntegerArgumentType.integer())
                                        .then(
                                            ClientCommands.argument("z", IntegerArgumentType.integer())
                                                .executes {
                                                    runIfEnabled(it.source) {
                                                        pathfindToCoordinates(
                                                            it.source,
                                                            IntegerArgumentType.getInteger(it, "x"),
                                                            IntegerArgumentType.getInteger(it, "y"),
                                                            IntegerArgumentType.getInteger(it, "z")
                                                        )
                                                    }
                                                }
                                        )
                                )
                        )
                )
                .then(
                    ClientCommands.literal("pathdebug")
                        .executes { runIfEnabled(it.source) { setPathDebug(it.source, true) } }
                        .then(ClientCommands.literal("on").executes { runIfEnabled(it.source) { setPathDebug(it.source, true) } })
                        .then(ClientCommands.literal("off").executes { runIfEnabled(it.source) { setPathDebug(it.source, false) } })
                )
                .then(ClientCommands.literal("edge").executes { runIfEnabled(it.source) { renderPlatformEdges(it.source) } })
                .then(hudEditorAlias("edithuds"))
                .then(hudEditorAlias("hud"))
                .then(hudEditorAlias("gui"))
                .then(hudEditorAlias("edit"))
        )
    }

    private fun hudEditorAlias(name: String) = ClientCommands.literal(name)
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
        BlazeChat.info(source, "/blaze pathfind - pathfinds to the nearest Blaze and walks you there")
        BlazeChat.info(source, "/blaze pathfind <x> <y> <z> - pathfinds to the given coordinates")
        BlazeChat.info(source, "/blaze pathdebug <on|off> - enables coded pathfinding diagnostics in chat")
        BlazeChat.info(source, "/blaze edge - renders overlays for the open edge blocks on your current platform")
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

    private fun pathfindToNearestBlaze(source: FabricClientCommandSource): Int {
        return BlazePathfindController.start(source)
    }

    private fun pathfindToCoordinates(source: FabricClientCommandSource, x: Int, y: Int, z: Int): Int {
        return BlazePathfindController.start(source, BlockPos(x, y, z))
    }

    private fun renderPlatformEdges(source: FabricClientCommandSource): Int {
        return BlazePathfindController.renderCurrentPlatformEdges(source)
    }

    private fun setPathDebug(source: FabricClientCommandSource, enabled: Boolean): Int {
        return BlazePathfindController.setPathDebug(source, enabled)
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
