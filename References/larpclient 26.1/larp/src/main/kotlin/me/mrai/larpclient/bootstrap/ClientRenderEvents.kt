package me.mrai.larpclient.bootstrap

import me.mrai.larpclient.features.impl.dungeons.f7.general.positionalmessages.PositionalMessagesModule
import me.mrai.larpclient.features.impl.dungeons.f7.general.witheresp.WitherEspModule
import me.mrai.larpclient.features.impl.dungeons.f7.p2.gyrowaypoints.GyroWaypointsModule
import me.mrai.larpclient.features.impl.dungeons.f7.p4.threebythreehighlight.ThreeByThreeHighlightModule
import me.mrai.larpclient.features.impl.dungeons.f7.p5.arrowstackwaypoints.ArrowStackWaypointsModule
import me.mrai.larpclient.features.impl.dungeons.f7.predev.arrowalign.ArrowAlignModule
import me.mrai.larpclient.features.impl.dungeons.f7.predev.lightsdevice.LightsDeviceModule
import me.mrai.larpclient.features.impl.dungeons.f7.predev.soulsandaura.SoulsandAuraModule
import me.mrai.larpclient.features.impl.kuudra.p1.kuudrawaypoints.KuudraWaypointModule
import me.mrai.larpclient.features.impl.kuudra.p1.pearlprediction.PearlPredictionModule
import me.mrai.larpclient.features.impl.kuudra.p3.skipwaypoint.SkipWaypointModule
import me.mrai.larpclient.features.impl.kuudra.p3.stunwaypoint.StunWaypointModule
import me.mrai.larpclient.features.impl.misc.other.playerparticles.PlayerParticlesModule
import me.mrai.larpclient.features.impl.misc.other.trail.TrailRenderer
import me.mrai.larpclient.features.impl.skyblock.golems.locationscanner.LocationScannerModule
import me.mrai.larpclient.features.impl.skyblock.golems.prefirewaypoints.PrefireWaypointsModule
import me.mrai.larpclient.features.impl.skyblock.golems.spawntimer.SpawnTimerModule
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents

object ClientRenderEvents {
    private val renderers: List<(LevelRenderContext) -> Unit> = buildList {
        add(KuudraWaypointModule::render)
        add(PearlPredictionModule::render)
        add(WitherEspModule::render)
        add(PositionalMessagesModule::render)
        add(ArrowAlignModule::render)
        add(SoulsandAuraModule::render)
        add(LightsDeviceModule::render)
        add(ThreeByThreeHighlightModule::render)
        add(GyroWaypointsModule::render)
        add(ArrowStackWaypointsModule::render)
        add(SkipWaypointModule::render)
        add(StunWaypointModule::render)
        add(LocationScannerModule::render)
        add(SpawnTimerModule::render)
        add(PrefireWaypointsModule::render)
        add(PlayerParticlesModule::render)
        add(TrailRenderer::render)
    }

    fun register() {
        LevelRenderEvents.END_MAIN.register { context ->
            renderers.forEach { renderer -> renderer(context) }
        }
    }
}
