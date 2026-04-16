package me.mrai.larpclient.command

import com.mojang.brigadier.Command
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.arguments.DoubleArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import me.mrai.larpclient.features.impl.dungeons.f7.general.positionalmessages.PositionalMessagesModule
import me.mrai.larpclient.features.impl.kuudra.p1.kuudrawaypoints.KuudraWaypointModule
import me.mrai.larpclient.features.impl.skyblock.golems.prefirewaypoints.PrefireWaypointsModule
import me.mrai.larpclient.features.impl.skyblock.general.visualfme.VisualFmeState
import me.mrai.larpclient.ui.clickgui.NewClickGuiScreen
import me.mrai.larpclient.util.LarpBranding
import net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument
import net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource

object LarpCommand {
    private val childRegistrars = mutableListOf<(LiteralArgumentBuilder<FabricClientCommandSource>) -> Unit>()

    fun registerChildren(registrar: (LiteralArgumentBuilder<FabricClientCommandSource>) -> Unit) {
        childRegistrars += registrar
    }

    fun build(): LiteralArgumentBuilder<FabricClientCommandSource> {
        return literal("larp")
            .executes(::openGui)
            .then(literal("help").executes(::help))
            .then(literal("forcekuudra").executes(::toggleForceKuudra))
            .then(literal("resetkuudra").executes(::resetKuudra))
            .then(literal("simulatepickup").executes(::simulatePickup))
            .then(
                literal("setblock")
                    .then(
                        argument("block", StringArgumentType.string())
                            .executes(::setVisualBlock)
                    )
            )
            .then(
                literal("setgolem")
                    .executes(::listGolemAreas)
                    .then(
                        argument("area", StringArgumentType.word())
                            .executes(::setGolemArea)
                    )
            )
            .then(
                literal("posmsg")
                    .then(
                        literal("add")
                            .then(
                                argument("command", StringArgumentType.string())
                                    .then(
                                        argument("radius", DoubleArgumentType.doubleArg(0.1))
                                            .executes(::addPositionalMessage)
                                    )
                            )
                    )
            ).apply {
                childRegistrars.forEach { it(this) }
            }
    }

    private fun help(context: CommandContext<FabricClientCommandSource>): Int {
        DotCommandHelp.larpLines().forEach(context.source::sendFeedback)
        return Command.SINGLE_SUCCESS
    }

    private fun openGui(context: CommandContext<FabricClientCommandSource>): Int {
        NewClickGuiScreen.open()
        return Command.SINGLE_SUCCESS
    }

    private fun toggleForceKuudra(context: CommandContext<FabricClientCommandSource>): Int {
        val enabled = KuudraWaypointModule.toggleForceKuudra()
        reply(context.source, "forcekuudra ${if (enabled) "enabled" else "disabled"}", LarpBranding.GREEN)
        return Command.SINGLE_SUCCESS
    }

    private fun resetKuudra(context: CommandContext<FabricClientCommandSource>): Int {
        KuudraWaypointModule.resetKuudraState()
        reply(context.source, "Reset Kuudra pre detection and missing-pre state")
        return Command.SINGLE_SUCCESS
    }

    private fun simulatePickup(context: CommandContext<FabricClientCommandSource>): Int {
        KuudraWaypointModule.simulatePickup()
        reply(context.source, "Simulated Kuudra crate pickup timer", LarpBranding.GREEN)
        return Command.SINGLE_SUCCESS
    }

    private fun addPositionalMessage(context: CommandContext<FabricClientCommandSource>): Int {
        val command = StringArgumentType.getString(context, "command")
        val radius = DoubleArgumentType.getDouble(context, "radius")
        val ok = PositionalMessagesModule.addAtPlayer(command, radius)
        reply(
            context.source,
            if (ok) "Added positional message ring with radius ${"%.2f".format(radius)}"
            else "Could not add positional message ring here",
            if (ok) LarpBranding.GREEN else LarpBranding.RED
        )
        return Command.SINGLE_SUCCESS
    }

    private fun setVisualBlock(context: CommandContext<FabricClientCommandSource>): Int {
        val blockId = StringArgumentType.getString(context, "block")
        val ok = VisualFmeState.setSelectedBlock(blockId)
        reply(
            context.source,
            if (ok) "Visual FME selected block set to ${VisualFmeState.selectedBlockId()}"
            else "Unknown block id: $blockId",
            if (ok) LarpBranding.GREEN else LarpBranding.RED
        )
        return Command.SINGLE_SUCCESS
    }

    private fun setGolemArea(context: CommandContext<FabricClientCommandSource>): Int {
        val area = StringArgumentType.getString(context, "area")
        val player = context.source.player ?: return Command.SINGLE_SUCCESS
        val ok = PrefireWaypointsModule.setSpot(area, player.position())
        reply(
            context.source,
            if (ok) "Saved prefire spot for $area at your current position"
            else "Unknown golem area: $area. Options: ${PrefireWaypointsModule.areaOptions()}",
            if (ok) LarpBranding.GREEN else LarpBranding.RED
        )
        return Command.SINGLE_SUCCESS
    }

    private fun listGolemAreas(context: CommandContext<FabricClientCommandSource>): Int {
        reply(context.source, "Golem areas: ${PrefireWaypointsModule.areaOptions()}", LarpBranding.AQUA)
        return Command.SINGLE_SUCCESS
    }

    private fun reply(source: FabricClientCommandSource, message: String, color: Int = LarpBranding.WHITE) {
        source.sendFeedback(LarpBranding.prefixed(message, color))
    }
}
