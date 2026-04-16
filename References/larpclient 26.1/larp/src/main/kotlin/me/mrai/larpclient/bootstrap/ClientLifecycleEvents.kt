package me.mrai.larpclient.bootstrap

import me.mrai.larpclient.features.impl.dungeons.f7.general.positionalmessages.PositionalMessagesModule
import me.mrai.larpclient.features.impl.dungeons.f7.general.termgui.TermGuiModule
import me.mrai.larpclient.features.impl.dungeons.f7.p2.gyrowaypoints.GyroWaypointsModule
import me.mrai.larpclient.features.impl.dungeons.f7.p5.arrowstackwaypoints.ArrowStackWaypointsModule
import me.mrai.larpclient.features.impl.dungeons.f7.predev.arrowalign.ArrowAlignModule
import me.mrai.larpclient.features.impl.dungeons.f7.predev.soulsandaura.SoulsandAuraModule
import me.mrai.larpclient.features.impl.dungeons.general.truesplits.TrueSplitsState
import me.mrai.larpclient.features.impl.kuudra.p1.kuudrawaypoints.KuudraWaypointModule
import me.mrai.larpclient.features.impl.kuudra.general.blockpickobulus.BlockPickobulusModule
import me.mrai.larpclient.features.impl.kuudra.p3.stunwaypoint.StunWaypointModule
import me.mrai.larpclient.features.impl.kuudra.p4.kuudradirection.KuudraDirectionModule
import me.mrai.larpclient.features.impl.misc.other.trail.TrailModule
import me.mrai.larpclient.features.impl.skyblock.golems.GolemTrackerState
import me.mrai.larpclient.features.impl.skyblock.general.performancehud.PerformanceStatsTracker
import me.mrai.larpclient.module.ModuleManager
import me.mrai.larpclient.presence.HeartbeatManager
import me.mrai.larpclient.render.gui.RoundRectPIPRenderer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback
import net.fabricmc.fabric.api.client.rendering.v1.SpecialGuiElementRegistry

object ClientLifecycleEvents {
    private val worldChangeHandlers = buildList {
        add(PositionalMessagesModule::onWorldChange)
        add(TermGuiModule::onWorldChange)
        add(KuudraWaypointModule::onWorldChange)
        add(BlockPickobulusModule::onWorldChange)
        add(KuudraDirectionModule::onWorldChange)
        add(StunWaypointModule::onWorldChange)
        add(ArrowAlignModule::onWorldChange)
        add(ArrowStackWaypointsModule::onWorldChange)
        add(GolemTrackerState::onWorldChange)
        add(PerformanceStatsTracker::reset)
        add(TrailModule::clear)
    }

    fun register() {
        ClientPlayConnectionEvents.DISCONNECT.register { _, _ ->
            worldChangeHandlers.forEach { it() }
        }

        ClientTickEvents.END_CLIENT_TICK.register { client ->
            HeartbeatManager.tick()
            ModuleManager.onClientTick(client)
            GolemTrackerState.onTick()
            SoulsandAuraModule.onClientTick()
            TrueSplitsState.onClientTick()
        }

        // Register HUD end callback to flush round rect UBO
        HudRenderCallback.EVENT.register { _, _ ->
            RoundRectPIPRenderer.endFrame()
        }

        // Register round rect PIP renderer
        SpecialGuiElementRegistry.register { ctx ->
            RoundRectPIPRenderer(ctx.vertexConsumers())
        }
    }
}

