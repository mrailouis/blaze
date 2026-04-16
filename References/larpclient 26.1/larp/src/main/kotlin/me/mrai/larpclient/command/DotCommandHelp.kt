package me.mrai.larpclient.command

import me.mrai.larpclient.util.LarpBranding
import net.minecraft.network.chat.Component

object DotCommandHelp {
    private val rootProviders = mutableListOf<() -> List<Component>>()
    private val larpProviders = mutableListOf<() -> List<Component>>()

    fun registerRootLines(provider: () -> List<Component>) {
        rootProviders += provider
    }

    fun registerLarpLines(provider: () -> List<Component>) {
        larpProviders += provider
    }

    fun rootLines(): List<Component> {
        return buildList {
            addAll(
                listOf(
                    header("Dot Commands", "Everything runs through dot commands now."),
                    entry(".help", "Show this overview."),
                    entry(".larp help", "General client utilities and legit helpers."),
                    entry(".truesplits help", "True Splits chat commands."),
                    footer("Use tab while typing .larp or .truesplits for live suggestions.")
                )
            )
            rootProviders.forEach { addAll(it()) }
        }
    }

    fun larpLines(): List<Component> {
        return buildList {
            add(header(".larp", "General client utilities."))
            add(entry(".larp forcekuudra", "Toggle Kuudra pre-phase debug forcing."))
            add(entry(".larp resetkuudra", "Reset Kuudra pre detection and missing-pre state."))
            add(entry(".larp setgolem <area>", "Save your current spot for that golem prefire area."))
            add(entry(".larp setblock <block>", "Set the Visual FME edit block id."))
            add(entry(".larp posmsg add \"command\" <radius>", "Add a positional message trigger at your feet."))
            larpProviders.forEach { addAll(it()) }
            add(footer("Try .larp help for the current command list."))
        }
    }

    fun trueSplitsLines(): List<Component> {
        return listOf(
            header(".truesplits", "True Splits chat utilities."),
            entry(".truesplits breakdown <1-5>", "Dump one breakdown section to chat."),
            footer("The section number matches the clickable breakdown summary headings.")
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
