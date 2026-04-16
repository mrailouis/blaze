package me.mrai.larpclient.features.impl.skyblock.general.simpleautoroutes

import com.mojang.brigadier.Command
import com.mojang.brigadier.arguments.BoolArgumentType
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import me.mrai.larpclient.addon.AddonCommandAccess
import me.mrai.larpclient.addon.AddonDotCommandHelp
import me.mrai.larpclient.util.LarpBranding
import net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument
import net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
import net.minecraft.commands.SharedSuggestionProvider

object SimpleAutoroutesCommand {
    fun build() = literal("autoroute")
        .executes(::help)
        .then(literal("help").executes(::help))
        .then(
            literal("list")
                .executes(::listRoutes)
                .then(
                    argument("route", StringArgumentType.word())
                        .suggests(::suggestRoutes)
                        .executes(::showRoute)
                )
        )
        .then(
            literal("add")
                .then(
                    argument("route", StringArgumentType.word())
                        .suggests(::suggestRoutes)
                        .executes { addNode(it, start = false, awaitClick = false, awaitTrace = false) }
                        .then(
                            argument("start", BoolArgumentType.bool())
                                .executes {
                                    addNode(
                                        it,
                                        start = BoolArgumentType.getBool(it, "start"),
                                        awaitClick = false,
                                        awaitTrace = false
                                    )
                                }
                                .then(
                                    argument("awaitClick", BoolArgumentType.bool())
                                        .executes {
                                            addNode(
                                                it,
                                                start = BoolArgumentType.getBool(it, "start"),
                                                awaitClick = BoolArgumentType.getBool(it, "awaitClick"),
                                                awaitTrace = false
                                            )
                                        }
                                        .then(
                                            argument("awaitTrace", BoolArgumentType.bool())
                                                .executes {
                                                    addNode(
                                                        it,
                                                        start = BoolArgumentType.getBool(it, "start"),
                                                        awaitClick = BoolArgumentType.getBool(it, "awaitClick"),
                                                        awaitTrace = BoolArgumentType.getBool(it, "awaitTrace")
                                                    )
                                                }
                                        )
                                )
                        )
                )
        )
        .then(
            literal("remove")
                .then(
                    argument("route", StringArgumentType.word())
                        .suggests(::suggestRoutes)
                        .executes(::removeNearest)
                        .then(
                            argument("index", IntegerArgumentType.integer(0))
                                .suggests(::suggestIndices)
                                .executes(::removeIndex)
                        )
                )
        )
        .then(
            literal("clear")
                .then(
                    argument("route", StringArgumentType.word())
                        .suggests(::suggestRoutes)
                        .executes(::clearRoute)
                )
        )
        .then(
            literal("delete")
                .then(
                    argument("route", StringArgumentType.word())
                        .suggests(::suggestRoutes)
                        .executes(::deleteRoute)
                )
        )

    private fun help(context: CommandContext<FabricClientCommandSource>): Int {
        AddonDotCommandHelp.simpleAutoroutesLines().forEach(context.source::sendFeedback)
        return Command.SINGLE_SUCCESS
    }

    private fun listRoutes(context: CommandContext<FabricClientCommandSource>): Int {
        if (!requireAccess(context)) return Command.SINGLE_SUCCESS
        val lines = SimpleAutoroutesModule.describeRoutes()
        if (lines.isEmpty()) {
            reply(context.source, "No simple autoroutes saved yet.", LarpBranding.GRAY)
            return Command.SINGLE_SUCCESS
        }

        lines.forEach { line -> reply(context.source, line) }
        return Command.SINGLE_SUCCESS
    }

    private fun showRoute(context: CommandContext<FabricClientCommandSource>): Int {
        if (!requireAccess(context)) return Command.SINGLE_SUCCESS
        val route = StringArgumentType.getString(context, "route")
        val lines = SimpleAutoroutesModule.describeRoute(route)
        if (lines.isEmpty()) {
            reply(context.source, "Unknown route '$route'.", LarpBranding.RED)
            return Command.SINGLE_SUCCESS
        }

        lines.forEach { line -> reply(context.source, line) }
        return Command.SINGLE_SUCCESS
    }

    private fun addNode(
        context: CommandContext<FabricClientCommandSource>,
        start: Boolean,
        awaitClick: Boolean,
        awaitTrace: Boolean
    ): Int {
        if (!requireAccess(context)) return Command.SINGLE_SUCCESS
        val route = StringArgumentType.getString(context, "route")
        val result = SimpleAutoroutesModule.addEtherwarpNode(route, start, awaitClick, awaitTrace)
        reply(context.source, result.message, if (result.ok) LarpBranding.GREEN else LarpBranding.RED)
        return Command.SINGLE_SUCCESS
    }

    private fun removeNearest(context: CommandContext<FabricClientCommandSource>): Int {
        if (!requireAccess(context)) return Command.SINGLE_SUCCESS
        val route = StringArgumentType.getString(context, "route")
        val result = SimpleAutoroutesModule.removeNearestNode(route)
        reply(context.source, result.message, if (result.ok) LarpBranding.GREEN else LarpBranding.RED)
        return Command.SINGLE_SUCCESS
    }

    private fun removeIndex(context: CommandContext<FabricClientCommandSource>): Int {
        if (!requireAccess(context)) return Command.SINGLE_SUCCESS
        val route = StringArgumentType.getString(context, "route")
        val index = IntegerArgumentType.getInteger(context, "index")
        val result = SimpleAutoroutesModule.removeNode(route, index)
        reply(context.source, result.message, if (result.ok) LarpBranding.GREEN else LarpBranding.RED)
        return Command.SINGLE_SUCCESS
    }

    private fun clearRoute(context: CommandContext<FabricClientCommandSource>): Int {
        if (!requireAccess(context)) return Command.SINGLE_SUCCESS
        val route = StringArgumentType.getString(context, "route")
        val result = SimpleAutoroutesModule.clearRoute(route)
        reply(context.source, result.message, if (result.ok) LarpBranding.GREEN else LarpBranding.RED)
        return Command.SINGLE_SUCCESS
    }

    private fun deleteRoute(context: CommandContext<FabricClientCommandSource>): Int {
        if (!requireAccess(context)) return Command.SINGLE_SUCCESS
        val route = StringArgumentType.getString(context, "route")
        val result = SimpleAutoroutesModule.deleteRoute(route)
        reply(context.source, result.message, if (result.ok) LarpBranding.GREEN else LarpBranding.RED)
        return Command.SINGLE_SUCCESS
    }

    private fun suggestRoutes(
        context: CommandContext<FabricClientCommandSource>,
        builder: com.mojang.brigadier.suggestion.SuggestionsBuilder
    ) = if (!AddonCommandAccess.granted()) {
        builder.buildFuture()
    } else {
        SharedSuggestionProvider.suggest(SimpleAutoroutesModule.routeNames(), builder)
    }

    private fun suggestIndices(
        context: CommandContext<FabricClientCommandSource>,
        builder: com.mojang.brigadier.suggestion.SuggestionsBuilder
    ) = if (!AddonCommandAccess.granted()) {
        builder.buildFuture()
    } else {
        SharedSuggestionProvider.suggest(
            SimpleAutoroutesModule.nodeIndexSuggestions(StringArgumentType.getString(context, "route")),
            builder
        )
    }

    private fun requireAccess(context: CommandContext<FabricClientCommandSource>): Boolean {
        return AddonCommandAccess.requireCommandAccess(context.source)
    }

    private fun reply(source: FabricClientCommandSource, message: String, color: Int = LarpBranding.WHITE) {
        source.sendFeedback(LarpBranding.prefixed(message, color))
    }
}
