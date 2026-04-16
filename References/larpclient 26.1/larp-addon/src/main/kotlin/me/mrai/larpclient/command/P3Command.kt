package me.mrai.larpclient.command

import com.mojang.brigadier.Command
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import me.mrai.larpclient.addon.AddonCommandAccess
import me.mrai.larpclient.addon.AddonDotCommandHelp
import me.mrai.larpclient.features.impl.dungeons.f7.p3.autop3.AutoP3Module
import me.mrai.larpclient.util.LarpBranding
import net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument
import net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
import net.minecraft.commands.SharedSuggestionProvider
import java.util.concurrent.CompletableFuture

object P3Command {
    fun build() = literal("p3")
        .executes(::help)
        .then(literal("help").executes(::help))
        .then(literal("types").executes(::types))
        .then(literal("list").executes(::list))
        .then(literal("clear").executes(::clear))
        .then(
            literal("remove")
                .then(literal("nearest").executes(::removeNearest))
                .then(argument("id", IntegerArgumentType.integer(1)).executes(::removeId))
        )
        .then(
            literal("add")
                .then(
                    argument("type", StringArgumentType.word())
                        .suggests { _, builder -> suggestTypes(builder) }
                        .executes(::addBare)
                        .then(
                            argument("args", StringArgumentType.greedyString())
                                .suggests(::suggestAddArgs)
                                .executes(::addWithArgs)
                        )
                )
        )
        .then(
            literal("route")
                .then(literal("list").executes(::listRoutes))
                .then(literal("record")
                    .then(literal("start").then(argument("name", StringArgumentType.word()).executes(::startRoute)))
                    .then(literal("stop").executes(::stopRoute))
                )
                .then(literal("delete")
                    .then(argument("name", StringArgumentType.word()).suggests { _, builder -> suggestRoutes(builder) }.executes(::deleteRoute))
                )
        )

    private fun help(context: CommandContext<FabricClientCommandSource>): Int {
        AddonDotCommandHelp.p3Lines().forEach(context.source::sendFeedback)
        return Command.SINGLE_SUCCESS
    }

    private fun types(context: CommandContext<FabricClientCommandSource>): Int {
        if (!requireAccess(context)) return Command.SINGLE_SUCCESS
        reply(context.source, "P3 ring types: ${AutoP3Module.supportedTypes().joinToString(", ")}")
        return Command.SINGLE_SUCCESS
    }

    private fun list(context: CommandContext<FabricClientCommandSource>): Int {
        if (!requireAccess(context)) return Command.SINGLE_SUCCESS
        val rings = AutoP3Module.listRings()
        if (rings.isEmpty()) {
            reply(context.source, "No P3 rings saved", LarpBranding.RED)
        } else {
            rings.forEach { reply(context.source, it) }
        }
        return Command.SINGLE_SUCCESS
    }

    private fun clear(context: CommandContext<FabricClientCommandSource>): Int {
        if (!requireAccess(context)) return Command.SINGLE_SUCCESS
        AutoP3Module.clearRings()
        reply(context.source, "Cleared P3 rings")
        return Command.SINGLE_SUCCESS
    }

    private fun removeNearest(context: CommandContext<FabricClientCommandSource>): Int {
        if (!requireAccess(context)) return Command.SINGLE_SUCCESS
        val removed = AutoP3Module.removeNearestRing()
        reply(
            context.source,
            if (removed) "Removed nearest P3 ring" else "No nearby P3 ring",
            if (removed) LarpBranding.GREEN else LarpBranding.RED
        )
        return Command.SINGLE_SUCCESS
    }

    private fun removeId(context: CommandContext<FabricClientCommandSource>): Int {
        if (!requireAccess(context)) return Command.SINGLE_SUCCESS
        val id = IntegerArgumentType.getInteger(context, "id")
        val removed = AutoP3Module.removeRing(id)
        reply(
            context.source,
            if (removed) "Removed P3 ring #$id" else "No P3 ring #$id",
            if (removed) LarpBranding.GREEN else LarpBranding.RED
        )
        return Command.SINGLE_SUCCESS
    }

    private fun addBare(context: CommandContext<FabricClientCommandSource>): Int {
        val type = StringArgumentType.getString(context, "type")
        return add(context, type, "")
    }

    private fun addWithArgs(context: CommandContext<FabricClientCommandSource>): Int {
        val type = StringArgumentType.getString(context, "type")
        val args = StringArgumentType.getString(context, "args")
        return add(context, type, args)
    }

    private fun add(context: CommandContext<FabricClientCommandSource>, type: String, args: String): Int {
        if (!requireAccess(context)) return Command.SINGLE_SUCCESS
        val ok = AutoP3Module.addRingAtPlayer(type, parseArgs(args))
        reply(
            context.source,
            if (ok) "Added P3 $type ring" else "Failed to add P3 $type ring",
            if (ok) LarpBranding.GREEN else LarpBranding.RED
        )
        return Command.SINGLE_SUCCESS
    }

    private fun listRoutes(context: CommandContext<FabricClientCommandSource>): Int {
        if (!requireAccess(context)) return Command.SINGLE_SUCCESS
        val routes = AutoP3Module.routeNames()
        reply(
            context.source,
            if (routes.isEmpty()) "No P3 routes saved" else "P3 routes: ${routes.joinToString(", ")}",
            if (routes.isEmpty()) LarpBranding.RED else LarpBranding.WHITE
        )
        return Command.SINGLE_SUCCESS
    }

    private fun startRoute(context: CommandContext<FabricClientCommandSource>): Int {
        if (!requireAccess(context)) return Command.SINGLE_SUCCESS
        val name = StringArgumentType.getString(context, "name")
        val started = AutoP3Module.beginRouteRecording(name)
        reply(
            context.source,
            if (started) "Started route $name" else "A P3 route recording is already active",
            if (started) LarpBranding.GREEN else LarpBranding.RED
        )
        return Command.SINGLE_SUCCESS
    }

    private fun stopRoute(context: CommandContext<FabricClientCommandSource>): Int {
        if (!requireAccess(context)) return Command.SINGLE_SUCCESS
        val stopped = AutoP3Module.stopRouteRecording() != null
        reply(
            context.source,
            if (stopped) "Stopped route recording" else "No active route recording",
            if (stopped) LarpBranding.GREEN else LarpBranding.RED
        )
        return Command.SINGLE_SUCCESS
    }

    private fun deleteRoute(context: CommandContext<FabricClientCommandSource>): Int {
        if (!requireAccess(context)) return Command.SINGLE_SUCCESS
        val name = StringArgumentType.getString(context, "name")
        val deleted = AutoP3Module.deleteRoute(name)
        reply(
            context.source,
            if (deleted) "Deleted route $name" else "No route named $name",
            if (deleted) LarpBranding.GREEN else LarpBranding.RED
        )
        return Command.SINGLE_SUCCESS
    }

    private fun suggestTypes(builder: SuggestionsBuilder) = if (!AddonCommandAccess.granted()) {
        builder.buildFuture()
    } else {
        SharedSuggestionProvider.suggest(AutoP3Module.supportedTypes(), builder)
    }

    private fun suggestRoutes(builder: SuggestionsBuilder) = if (!AddonCommandAccess.granted()) {
        builder.buildFuture()
    } else {
        SharedSuggestionProvider.suggest(AutoP3Module.routeNames(), builder)
    }

    private fun suggestAddArgs(
        context: CommandContext<FabricClientCommandSource>,
        builder: SuggestionsBuilder
    ): CompletableFuture<Suggestions> {
        if (!AddonCommandAccess.granted()) return builder.buildFuture()
        val type = runCatching { StringArgumentType.getString(context, "type") }.getOrNull()?.lowercase()
            ?: return builder.buildFuture()
        val input = builder.remaining
        val trimmedInput = input.trimStart()
        val leadingWhitespace = input.length - trimmedInput.length
        val tokenStartInTrimmed = trimmedInput.lastIndexOf(' ').let { if (it == -1) 0 else it + 1 }
        val tokenStart = builder.start + leadingWhitespace + tokenStartInTrimmed
        val tokenBuilder = builder.createOffset(tokenStart)
        val currentToken = trimmedInput.substring(tokenStartInTrimmed)
        val currentKey = argKey(currentToken)
        val usedKeys = parseArgs(trimmedInput)
            .keys
            .filterNot { it == currentKey }
            .toSet()

        argSuggestionsFor(type)
            .filterNot { suggestion ->
                val key = argKey(suggestion) ?: suggestion.lowercase()
                key in usedKeys && (suggestion.endsWith("=") || suggestion.endsWith("["))
            }
            .forEach { suggestion ->
                if (suggestion.startsWith(currentToken, ignoreCase = true)) {
                    tokenBuilder.suggest(suggestion)
                }
            }

        if (currentToken.startsWith("route=", ignoreCase = true)) {
            val prefix = "route="
            val routeBuilder = builder.createOffset(tokenStart + prefix.length)
            val partialRoute = currentToken.removePrefix(prefix)
            AutoP3Module.routeNames()
                .filter { it.startsWith(partialRoute, ignoreCase = true) }
                .forEach(routeBuilder::suggest)
            return routeBuilder.buildFuture()
        }

        if (currentToken.startsWith("edge[", ignoreCase = true)) {
            val prefix = "edge["
            val edgeBuilder = builder.createOffset(tokenStart + prefix.length)
            val partialZone = currentToken.substring(prefix.length)
            AutoP3Module.landingZoneIds()
                .filter { it.startsWith(partialZone, ignoreCase = true) }
                .forEach { edgeBuilder.suggest("$it]") }
            return edgeBuilder.buildFuture()
        }

        if (currentToken.startsWith("edge=", ignoreCase = true)) {
            val prefix = "edge="
            val edgeBuilder = builder.createOffset(tokenStart + prefix.length)
            val partialZone = currentToken.substring(prefix.length)
            AutoP3Module.landingZoneIds()
                .filter { it.startsWith(partialZone, ignoreCase = true) }
                .forEach(edgeBuilder::suggest)
            return edgeBuilder.buildFuture()
        }

        return tokenBuilder.buildFuture()
    }

    private fun argSuggestionsFor(type: String): List<String> {
        val shared = mutableListOf("radius=", "x=", "y=", "z=", "exact")
        val lookShared = listOf("yaw=", "pitch=", "exactlook")
        when (type) {
            "walk" -> shared += lookShared + listOf("edge[", "edge=")
            "look" -> shared += lookShared
            "bonzo", "fastbonzo" -> shared += listOf("yaw=", "pitch=", "velbuffer=")
            "edge" -> shared += listOf("yaw=")
            "blink" -> shared += listOf("route=", "packets=")
            "movement" -> shared += listOf("route=")
            "command" -> shared += listOf("command=")
            "chat" -> shared += listOf("message=")
            "use" -> shared += lookShared + listOf("item=")
        }
        return shared
    }

    private fun parseArgs(input: String): Map<String, String> {
        if (input.isBlank()) return emptyMap()
        val tokens = Regex("\\S+=\"[^\"]*\"|\"[^\"]*\"|\\S+").findAll(input)
            .map { it.value }
            .toList()

        val parsed = linkedMapOf<String, String>()
        for (token in tokens) {
            val bracketMatch = BRACKET_ARG.matchEntire(token)
            if (bracketMatch != null) {
                parsed[bracketMatch.groupValues[1].lowercase()] = unquote(bracketMatch.groupValues[2])
                continue
            }
            val parts = token.split('=', limit = 2)
            if (parts.size == 2) {
                parsed[parts[0].lowercase()] = unquote(parts[1])
            } else if (parts.size == 1 && parts[0].isNotBlank()) {
                parsed[unquote(parts[0]).lowercase()] = "true"
            }
        }
        return parsed
    }

    private fun argKey(token: String): String? {
        val cleaned = token.trim().lowercase()
        if (cleaned.isBlank()) return null
        return when {
            '[' in cleaned -> cleaned.substringBefore('[')
            '=' in cleaned -> cleaned.substringBefore('=')
            else -> cleaned
        }.ifBlank { null }
    }

    private fun unquote(value: String): String {
        return if (value.length >= 2 && value.startsWith('"') && value.endsWith('"')) {
            value.substring(1, value.length - 1)
        } else {
            value
        }
    }

    private val BRACKET_ARG = Regex("([A-Za-z_][A-Za-z0-9_]*)\\[(.+)]")

    private fun reply(source: FabricClientCommandSource, message: String, color: Int = LarpBranding.WHITE) {
        source.sendFeedback(LarpBranding.prefixed(message, color))
    }

    private fun requireAccess(context: CommandContext<FabricClientCommandSource>): Boolean {
        return AddonCommandAccess.requireCommandAccess(context.source)
    }
}
