package me.mrai.larpclient.features.impl.dungeons.general.truesplits

import com.mojang.brigadier.Command
import com.mojang.brigadier.arguments.IntegerArgumentType
import me.mrai.larpclient.command.DotCommandHelp
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
import net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument
import net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal
import net.minecraft.commands.SharedSuggestionProvider

object TrueSplitsCommand {
    fun build() = literal("truesplits")
        .executes(::help)
        .then(literal("help").executes(::help))
        .then(
            literal("breakdown")
                .then(
                    argument("index", IntegerArgumentType.integer(1, 5))
                        .suggests { _, builder ->
                            SharedSuggestionProvider.suggest(listOf("1", "2", "3", "4", "5"), builder)
                        }
                        .executes {
                            val index = IntegerArgumentType.getInteger(it, "index") - 1
                            TrueSplitsState.sendBreakdownDumpToChat(index)
                            Command.SINGLE_SUCCESS
                        }
                )
        )

    private fun help(context: com.mojang.brigadier.context.CommandContext<FabricClientCommandSource>): Int {
        DotCommandHelp.trueSplitsLines().forEach(context.source::sendFeedback)
        return Command.SINGLE_SUCCESS
    }
}
