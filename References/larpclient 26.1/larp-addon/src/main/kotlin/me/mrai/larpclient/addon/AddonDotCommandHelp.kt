package me.mrai.larpclient.addon

import me.mrai.larpclient.util.LarpBranding
import net.minecraft.network.chat.Component

object AddonDotCommandHelp {
    fun p3Lines(): List<Component> {
        return listOf(
            header(".p3", "P3 ring and route tools."),
            entry(".p3 types", "List supported ring types."),
            entry(".p3 add <type> key=value ...", "Add a ring using the current player position."),
            entry(".p3 route record start <name>", "Begin recording a route."),
            entry(".p3 route record stop", "Stop the active recording."),
            entry(".p3 route delete <name>", "Delete a saved route.")
        )
    }

    fun simpleAutoroutesLines(): List<Component> {
        return listOf(
            header(".larp autoroute", "Simple etherwarp route editing."),
            entry(".larp autoroute add <route> [start] [awaitClick] [awaitTrace]", "Add a node from your feet to the looked etherwarp block."),
            entry(".larp autoroute list [route]", "List saved routes or inspect one route in detail."),
            entry(".larp autoroute remove <route> [index]", "Remove the nearest node or a specific node."),
            entry(".larp autoroute clear <route>", "Clear all nodes from one route."),
            entry(".larp autoroute delete <route>", "Delete one route entirely."),
            footer("Await click listens for attack, use, or the Trigger Key while you stand in the ring.")
        )
    }

    private fun header(title: String, subtitle: String): Component {
        return LarpBranding.prefixed(
            LarpBranding.command(title)
                .append(LarpBranding.muted("  "))
                .append(LarpBranding.text(subtitle, LarpBranding.WHITE))
        )
    }

    private fun entry(command: String, description: String): Component {
        return LarpBranding.prefixed(
            LarpBranding.command(command)
                .append(LarpBranding.muted(" - "))
                .append(LarpBranding.text(description, LarpBranding.WHITE))
        )
    }

    private fun footer(text: String): Component {
        return LarpBranding.prefixed(LarpBranding.text(text, LarpBranding.GRAY))
    }
}
