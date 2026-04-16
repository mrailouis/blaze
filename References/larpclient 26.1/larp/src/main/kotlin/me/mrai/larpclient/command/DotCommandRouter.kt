package me.mrai.larpclient.command

import com.mojang.brigadier.exceptions.CommandSyntaxException
import me.mrai.larpclient.ui.clickgui.NewClickGuiScreen
import kotlin.jvm.JvmStatic
import me.mrai.larpclient.util.LarpBranding
import me.mrai.larpclient.util.LarpChat
import me.mrai.larpclient.util.LarpLog
import net.fabricmc.fabric.api.client.command.v2.ClientCommands
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
import net.minecraft.client.Minecraft

object DotCommandRouter {
    private val managedRoots = linkedSetOf("larp", "truesplits")
    private val rootGuards = linkedMapOf<String, (String) -> Boolean>()

    fun registerManagedRoot(root: String) {
        val normalized = root.trim().lowercase()
        if (normalized.isNotBlank()) {
            managedRoots += normalized
        }
    }

    fun registerGuard(root: String, guard: (String) -> Boolean) {
        val normalized = root.trim().lowercase()
        if (normalized.isNotBlank()) {
            rootGuards[normalized] = guard
        }
    }

    /**
     * Maps chat input starting with `.` to the slash command string that Brigadier should parse
     * for syntax highlighting and tab completion. Top-level managed roots (e.g. addon `p3`) must
     * use `/root ...`, not `/larp root ...`, or parsing fails and suggestions break.
     */
    @JvmStatic
    fun mapDotInputForSuggestions(value: String): String {
        if (!value.startsWith(".")) return value
        val raw = value.substring(1).trim()
        if (raw.isEmpty()) {
            return "/larp "
        }
        val lower = raw.lowercase()
        if (lower == "larp") {
            return "/larp "
        }
        if (lower == "truesplits") {
            return "/truesplits "
        }
        if (lower.startsWith("larp ")) {
            return "/$raw"
        }
        if (lower.startsWith("truesplits ")) {
            return "/$raw"
        }
        val root = raw.substringBefore(' ').lowercase()
        if (root in managedRoots) {
            return if (raw.equals(root, ignoreCase = true)) "/$root " else "/$raw"
        }
        return "/larp $raw"
    }

    fun handleChat(rawMessage: String): Boolean {
        val message = rawMessage.trim()
        if (!message.startsWith(".")) return false

        val command = message.removePrefix(".").trim()
        if (command.isBlank()) return false

        val root = command.substringBefore(' ').lowercase()
        if (root == "larp" && command.equals("larp", ignoreCase = true)) {
            NewClickGuiScreen.open()
            return true
        }
        if (root == "help") {
            DotCommandHelp.rootLines().forEach(LarpChat::send)
            return true
        }
        if (root == "commands") {
            DotCommandHelp.rootLines().forEach(LarpChat::send)
            return true
        }

        if (root !in managedRoots) {
            invalid(command)
            return true
        }
        if (rootGuards[root]?.invoke(command) == true) return true

        execute(command)
        return true
    }

    fun handleSlashCommand(command: String): Boolean {
        val trimmed = command.trim()
        if (trimmed.isEmpty()) return true

        val root = trimmed.substringBefore(' ').lowercase()
        if (root == "commands" || root == "help") {
            return false
        }
        if (root !in managedRoots) {
            return true
        }
        if (root == "larp" && trimmed.equals("larp", ignoreCase = true)) {
            NewClickGuiScreen.open()
            return false
        }
        if (rootGuards[root]?.invoke(trimmed) == true) {
            return false
        }
        execute(trimmed)
        return false
    }

    private fun execute(command: String) {
        val dispatcher = ClientCommands.getActiveDispatcher()
        if (dispatcher == null) {
            LarpChat.send(
                LarpBranding.prefixed(
                    LarpBranding.error("Command dispatcher is not ready yet. Reconnect if this keeps happening.")
                )
            )
            LarpLog.warn("Dot command '$command' could not run because the client dispatcher was null.")
            return
        }

        val source = resolveCommandSource()
        if (source == null) {
            LarpChat.send(
                LarpBranding.prefixed(
                    LarpBranding.error("Command source is unavailable right now.")
                )
            )
            LarpLog.warn("Dot command '$command' could not run because the Fabric command source was unavailable.")
            return
        }

        LarpLog.info("Executing dot command '.$command'.")

        try {
            dispatcher.execute(command, source)
        } catch (syntax: CommandSyntaxException) {
            invalid(command, source)
        } catch (throwable: Throwable) {
            LarpLog.error("Dot command '.$command' crashed.", throwable)
            source.sendFeedback(
                LarpBranding.prefixed(
                    LarpBranding.error(
                        "Command failed: ${throwable.message ?: throwable.javaClass.simpleName}"
                    )
                )
            )
        }
    }

    private fun resolveCommandSource(): FabricClientCommandSource? {
        val connection = Minecraft.getInstance().connection ?: return null

        val reflected = runCatching {
            val getter = connection.javaClass.methods.firstOrNull {
                it.name == "getSuggestionsProvider" && it.parameterCount == 0
            }
            getter?.invoke(connection)
        }.getOrNull() ?: runCatching {
            val field = connection.javaClass.declaredFields.firstOrNull {
                it.name == "suggestionsProvider" || FabricClientCommandSource::class.java.isAssignableFrom(it.type)
            } ?: return null
            field.isAccessible = true
            field.get(connection)
        }.getOrNull()

        return reflected as? FabricClientCommandSource
    }

    private fun invalid(command: String, source: FabricClientCommandSource? = resolveCommandSource()) {
        source?.sendFeedback(
            LarpBranding.prefixed(
                LarpBranding.error("Invalid command.")
            )
        )
        LarpLog.info("Rejected invalid dot command '.$command'.")
    }
}
