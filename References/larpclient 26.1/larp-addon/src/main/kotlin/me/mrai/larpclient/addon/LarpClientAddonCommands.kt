package me.mrai.larpclient.addon

import com.mojang.brigadier.Command
import com.mojang.brigadier.arguments.DoubleArgumentType
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import me.mrai.larpclient.features.impl.dungeons.f7.p5.autocowhat.AutoCowHatModule
import me.mrai.larpclient.features.impl.dungeons.f7.p5.debuffsnap.DebuffSnapModule
import me.mrai.larpclient.features.impl.dungeons.f7.predev.soulsandaura.SoulsandAuraModule
import me.mrai.larpclient.features.impl.dungeons.general.velocitybuffer.VelocityBufferModule
import me.mrai.larpclient.features.impl.kuudra.p1.autopearls.AutoPearlsModule
import me.mrai.larpclient.features.impl.kuudra.p1.autopearls.PearlBypassController
import me.mrai.larpclient.features.impl.kuudra.p1.autoroutetopre.PreRouteManager
import me.mrai.larpclient.features.impl.kuudra.p1.autoroutetopre.PreRouteName
import me.mrai.larpclient.features.impl.misc.other.etherwarp.EtherwarpController
import me.mrai.larpclient.features.impl.kuudra.p1.summoncrates.SummonCratesSimulator
import me.mrai.larpclient.features.impl.skyblock.general.blink.BlinkModule
import me.mrai.larpclient.features.impl.skyblock.general.simpleautoroutes.SimpleAutoroutesCommand
import me.mrai.larpclient.util.LarpBranding
import net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument
import net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource

object LarpClientAddonCommands {
    fun register(root: LiteralArgumentBuilder<FabricClientCommandSource>) {
        root.then(
            literal("vb")
                .then(literal("on").executes(::enableVelocityBuffer))
                .then(literal("off").executes(::disableVelocityBuffer))
                .then(literal("pop").executes(::popVelocityBuffer))
                .then(literal("flush").executes(::flushVelocityBuffer))
        )
        root.then(literal("debuff").then(argument("radius", DoubleArgumentType.doubleArg(0.1)).executes(::addDebuffRing)))
        root.then(literal("etherwarp").executes(::toggleEtherwarp))
        root.then(literal("pearlbypass").executes(::togglePearlBypass))
        root.then(literal("autopearl").then(literal("p1").executes(::debugAutoPearlP1)).then(literal("p2").executes(::debugAutoPearlP2)))
        root.then(literal("summoncrates").executes(::summonCrates))
        root.then(literal("soulsand").executes(::toggleSoulsandEditMode))
        root.then(
            literal("blink")
                .then(argument("route", StringArgumentType.word())
                    .then(argument("packets", IntegerArgumentType.integer(1)).executes(::addBlinkRing)))
        )
        root.then(
            literal("add")
                .then(
                    literal("cow")
                        .then(argument("x", DoubleArgumentType.doubleArg(0.1))
                            .then(argument("y", DoubleArgumentType.doubleArg(0.1))
                                .then(argument("z", DoubleArgumentType.doubleArg(0.1)).executes(::addCowZone))))
                )
        )
        root.then(literal("clearcow").executes(::clearCowZones))
        root.then(
            literal("preroute")
                .then(
                    argument("name", StringArgumentType.word())
                        .then(literal("add").executes(::addSegment))
                        .then(literal("clear").executes(::clearRoute))
                        .then(literal("remove").then(argument("index", IntegerArgumentType.integer(0)).executes(::removeSegment)))
                )
        )
        root.then(SimpleAutoroutesCommand.build())
    }

    private fun enableVelocityBuffer(context: CommandContext<FabricClientCommandSource>): Int {
        if (!requireAccess(context)) return Command.SINGLE_SUCCESS
        VelocityBufferModule.enableWithToast()
        reply(context.source, "Velocity Buffer enabled", LarpBranding.GREEN)
        return Command.SINGLE_SUCCESS
    }

    private fun disableVelocityBuffer(context: CommandContext<FabricClientCommandSource>): Int {
        if (!requireAccess(context)) return Command.SINGLE_SUCCESS
        VelocityBufferModule.disableWithToast()
        reply(context.source, "Velocity Buffer disabled", LarpBranding.GREEN)
        return Command.SINGLE_SUCCESS
    }

    private fun popVelocityBuffer(context: CommandContext<FabricClientCommandSource>): Int {
        if (!requireAccess(context)) return Command.SINGLE_SUCCESS
        VelocityBufferModule.popWithToast()
        reply(context.source, "Velocity Buffer pop", LarpBranding.GREEN)
        return Command.SINGLE_SUCCESS
    }

    private fun flushVelocityBuffer(context: CommandContext<FabricClientCommandSource>): Int {
        if (!requireAccess(context)) return Command.SINGLE_SUCCESS
        VelocityBufferModule.flushWithToast()
        reply(context.source, "Velocity Buffer flushed", LarpBranding.GREEN)
        return Command.SINGLE_SUCCESS
    }

    private fun toggleEtherwarp(context: CommandContext<FabricClientCommandSource>): Int {
        if (!requireAccess(context)) return Command.SINGLE_SUCCESS
        val enabled = EtherwarpController.toggle()
        reply(context.source, "Etherwarp ${if (enabled) "enabled" else "disabled"}", LarpBranding.GREEN)
        return Command.SINGLE_SUCCESS
    }

    private fun addDebuffRing(context: CommandContext<FabricClientCommandSource>): Int {
        if (!requireAccess(context)) return Command.SINGLE_SUCCESS
        val radius = DoubleArgumentType.getDouble(context, "radius")
        val ok = DebuffSnapModule.addAtPlayer(radius)
        reply(
            context.source,
            if (ok) "Added Debuff Snap ring with radius ${"%.2f".format(radius)}" else "Could not add Debuff Snap ring here",
            if (ok) LarpBranding.GREEN else LarpBranding.RED
        )
        return Command.SINGLE_SUCCESS
    }

    private fun debugAutoPearlP1(context: CommandContext<FabricClientCommandSource>): Int {
        if (!requireAccess(context)) return Command.SINGLE_SUCCESS
        val ok = AutoPearlsModule.debugTriggerP1()
        reply(
            context.source,
            if (ok) "Queued Auto Pearl P1 test" else "Could not queue Auto Pearl P1 test",
            if (ok) LarpBranding.GREEN else LarpBranding.RED
        )
        return Command.SINGLE_SUCCESS
    }

    private fun debugAutoPearlP2(context: CommandContext<FabricClientCommandSource>): Int {
        if (!requireAccess(context)) return Command.SINGLE_SUCCESS
        val ok = AutoPearlsModule.debugTriggerP2()
        reply(
            context.source,
            if (ok) "Queued Auto Pearl P2 test" else "Could not queue Auto Pearl P2 test",
            if (ok) LarpBranding.GREEN else LarpBranding.RED
        )
        return Command.SINGLE_SUCCESS
    }

    private fun togglePearlBypass(context: CommandContext<FabricClientCommandSource>): Int {
        if (!requireAccess(context)) return Command.SINGLE_SUCCESS
        val enabled = PearlBypassController.toggle()
        reply(context.source, "Pearl bypass ${if (enabled) "enabled" else "disabled"}", LarpBranding.GREEN)
        return Command.SINGLE_SUCCESS
    }

    private fun toggleSoulsandEditMode(context: CommandContext<FabricClientCommandSource>): Int {
        if (!requireAccess(context)) return Command.SINGLE_SUCCESS
        SoulsandAuraModule.toggleEditMode()
        reply(context.source, "Toggled Soulsand Aura edit mode", LarpBranding.GREEN)
        return Command.SINGLE_SUCCESS
    }

    private fun summonCrates(context: CommandContext<FabricClientCommandSource>): Int {
        if (!requireAccess(context)) return Command.SINGLE_SUCCESS
        val result = SummonCratesSimulator.summonRandomScenario()
        reply(
            context.source,
            if (result != null) {
                "Summoned crates for ${result.pre}. Missing: ${result.missing}. Spawned: ${result.spawned.joinToString(", ")}"
            } else {
                "Stand in a Kuudra pre spot first."
            },
            if (result != null) LarpBranding.GREEN else LarpBranding.RED
        )
        return Command.SINGLE_SUCCESS
    }

    private fun addCowZone(context: CommandContext<FabricClientCommandSource>): Int {
        if (!requireAccess(context)) return Command.SINGLE_SUCCESS
        val x = DoubleArgumentType.getDouble(context, "x")
        val y = DoubleArgumentType.getDouble(context, "y")
        val z = DoubleArgumentType.getDouble(context, "z")
        val ok = AutoCowHatModule.addZoneAtPlayer(x, y, z)
        reply(
            context.source,
            if (ok) "Added Cow Hat zone at your position with size ($x, $y, $z)" else "Could not add Cow Hat zone here",
            if (ok) LarpBranding.GREEN else LarpBranding.RED
        )
        return Command.SINGLE_SUCCESS
    }

    private fun clearCowZones(context: CommandContext<FabricClientCommandSource>): Int {
        if (!requireAccess(context)) return Command.SINGLE_SUCCESS
        AutoCowHatModule.clearZones()
        reply(context.source, "Cleared all Cow Hat zones", LarpBranding.GREEN)
        return Command.SINGLE_SUCCESS
    }

    private fun addSegment(context: CommandContext<FabricClientCommandSource>): Int {
        if (!requireAccess(context)) return Command.SINGLE_SUCCESS
        val route = parseName(context) ?: return fail(context.source)

        val ok = PreRouteManager.addLookedSegment(route)
        reply(
            context.source,
            if (ok) "Added a segment to '${route.id}'." else "Stand on a block and look at a block first.",
            if (ok) LarpBranding.GREEN else LarpBranding.RED
        )
        return Command.SINGLE_SUCCESS
    }

    private fun clearRoute(context: CommandContext<FabricClientCommandSource>): Int {
        if (!requireAccess(context)) return Command.SINGLE_SUCCESS
        val route = parseName(context) ?: return fail(context.source)

        PreRouteManager.clear(route)
        reply(context.source, "Cleared preroute '${route.id}'", LarpBranding.GREEN)
        return Command.SINGLE_SUCCESS
    }

    private fun removeSegment(context: CommandContext<FabricClientCommandSource>): Int {
        if (!requireAccess(context)) return Command.SINGLE_SUCCESS
        val route = parseName(context) ?: return fail(context.source)
        val index = IntegerArgumentType.getInteger(context, "index")
        val ok = PreRouteManager.remove(route, index)
        reply(
            context.source,
            if (ok) "Removed index $index from '${route.id}'" else "Invalid index for route '${route.id}'",
            if (ok) LarpBranding.GREEN else LarpBranding.RED
        )
        return Command.SINGLE_SUCCESS
    }

    private fun addBlinkRing(context: CommandContext<FabricClientCommandSource>): Int {
        if (!requireAccess(context)) return Command.SINGLE_SUCCESS
        val route = StringArgumentType.getString(context, "route")
        val packets = IntegerArgumentType.getInteger(context, "packets")
        val ok = BlinkModule.addRingAtPlayer(route, packets)
        if (ok) {
            reply(context.source, "Added blink ring for '$route' with $packets packets", LarpBranding.GREEN)
        } else {
            val routes = BlinkModule.availableRoutes().joinToString(", ").ifBlank { "none" }
            reply(context.source, "Unknown or invalid blink route '$route'. Available: $routes", LarpBranding.RED)
        }
        return Command.SINGLE_SUCCESS
    }

    private fun parseName(context: CommandContext<FabricClientCommandSource>): PreRouteName? {
        return PreRouteName.from(StringArgumentType.getString(context, "name"))
    }

    private fun fail(source: FabricClientCommandSource): Int {
        reply(source, "Unknown preroute name. Use tri, x, slash, or equals.", LarpBranding.RED)
        return Command.SINGLE_SUCCESS
    }

    private fun requireAccess(context: CommandContext<FabricClientCommandSource>): Boolean {
        return AddonCommandAccess.requireCommandAccess(context.source)
    }

    private fun reply(source: FabricClientCommandSource, message: String, color: Int = LarpBranding.WHITE) {
        source.sendFeedback(LarpBranding.prefixed(message, color))
    }
}
