package me.mrai.larpclient.module

import com.mojang.blaze3d.platform.InputConstants
import me.mrai.larpclient.features.impl.dungeons.f7.general.archerutils.ArcherUtilsModule
import me.mrai.larpclient.features.impl.dungeons.f7.general.positionalmessages.PositionalMessagesModule
import me.mrai.larpclient.features.impl.skyblock.general.commandkeybinds.CommandKeybindsModule
import me.mrai.larpclient.features.impl.dungeons.f7.general.termgui.TermGuiModule
import me.mrai.larpclient.features.impl.dungeons.f7.general.witheresp.WitherEspModule
import me.mrai.larpclient.features.impl.dungeons.f7.p1.maxorhphud.MaxorHpHudModule
import me.mrai.larpclient.features.impl.dungeons.f7.p1.skeletonhorse.SkeletonHorseModule
import me.mrai.larpclient.features.impl.dungeons.f7.p4.threebythreehighlight.ThreeByThreeHighlightModule
import me.mrai.larpclient.features.impl.dungeons.f7.p5.arrowstackwaypoints.ArrowStackWaypointsModule
import me.mrai.larpclient.features.impl.dungeons.f7.p5.cowhat.CowHatModule
import me.mrai.larpclient.features.impl.dungeons.f7.p2.gyrowaypoints.GyroWaypointsModule
import me.mrai.larpclient.features.impl.dungeons.f7.predev.arrowalign.ArrowAlignModule
import me.mrai.larpclient.features.impl.dungeons.f7.predev.lightsdevice.LightsDeviceModule
import me.mrai.larpclient.features.impl.dungeons.f7.predev.soulsandaura.SoulsandAuraModule
import me.mrai.larpclient.features.impl.dungeons.general.lastbreathutils.LastBreathUtilsModule
import me.mrai.larpclient.features.impl.dungeons.general.truesplits.TrueSplitsModule
import me.mrai.larpclient.features.impl.kuudra.general.blockpickobulus.BlockPickobulusModule
import me.mrai.larpclient.features.impl.kuudra.p1.kuudrawaypoints.KuudraWaypointModule
import me.mrai.larpclient.features.impl.kuudra.p1.pearlprediction.PearlPredictionModule
import me.mrai.larpclient.features.impl.kuudra.p2.buildprogress.BuildProgressDisplayModule
import me.mrai.larpclient.features.impl.kuudra.p3.skipwaypoint.SkipWaypointModule
import me.mrai.larpclient.features.impl.kuudra.p3.stunwaypoint.StunWaypointModule
import me.mrai.larpclient.features.impl.kuudra.p4.kuudradirection.KuudraDirectionModule
import me.mrai.larpclient.features.impl.misc.other.discordrpc.DiscordRichPresenceModule
import me.mrai.larpclient.features.impl.misc.other.headsscale.HeadsScaleModule
import me.mrai.larpclient.features.impl.misc.other.playerparticles.PlayerParticlesModule
import me.mrai.larpclient.features.impl.misc.other.trail.TrailModule
import me.mrai.larpclient.features.impl.misc.ui.cleanscoreboard.CleanScoreboardModule
import me.mrai.larpclient.features.impl.misc.ui.itemrarity.ItemRarityModule
import me.mrai.larpclient.features.impl.misc.ui.modulelist.ModuleListModule
import me.mrai.larpclient.features.impl.misc.ui.playercustomisations.PlayerCustomisationsModule
import me.mrai.larpclient.features.impl.skyblock.golems.locationscanner.LocationScannerModule
import me.mrai.larpclient.features.impl.skyblock.golems.prefirewaypoints.PrefireWaypointsModule
import me.mrai.larpclient.features.impl.skyblock.golems.spawntimer.SpawnTimerModule
import me.mrai.larpclient.features.impl.skyblock.general.femboyarrows.FemboyArrowsModule
import me.mrai.larpclient.features.impl.skyblock.general.abilitycooldowns.AbilityCooldownsModule
import me.mrai.larpclient.features.impl.skyblock.general.deployabledisplay.DeployableDisplayModule
import me.mrai.larpclient.features.impl.skyblock.general.improvedmenus.ImprovedSkyblockMenusModule
import me.mrai.larpclient.features.impl.skyblock.general.muteidhider.MuteIdHiderModule
import me.mrai.larpclient.features.impl.skyblock.general.performancehud.PerformanceHudModule
import me.mrai.larpclient.features.impl.skyblock.general.serverlagdetection.ServerLagDetectionModule
import me.mrai.larpclient.features.impl.skyblock.general.slotlocking.SlotLockingModule
import me.mrai.larpclient.features.impl.skyblock.general.storagegui.StorageGuiModule
import me.mrai.larpclient.features.impl.skyblock.general.visualfme.VisualFmeModule
import me.mrai.larpclient.features.impl.skyblock.general.wardrobekeybinds.WardrobeKeybindsModule
import me.mrai.larpclient.features.impl.skyblock.general.zoom.ZoomModule
import me.mrai.larpclient.features.impl.skyblock.general.preventcursorreset.PreventCursorResetModule
import net.minecraft.client.Minecraft
import org.lwjgl.glfw.GLFW

object ModuleManager {
    val modules: MutableList<Module> = mutableListOf()
    private val pressedKeys = hashSetOf<Int>()
    private val moduleRegistry = ModuleRegistry().apply {
        register({ ServerLagDetectionModule })
        register({ MuteIdHiderModule })
        register({ AbilityCooldownsModule })
        register({ CommandKeybindsModule })
        register({ DeployableDisplayModule })
        register({ FemboyArrowsModule })
        register({ ImprovedSkyblockMenusModule })
        register({ WardrobeKeybindsModule })
        register({ StorageGuiModule })
        register({ SlotLockingModule })
        register({ ZoomModule })
        register({ PerformanceHudModule })
        register({ PreventCursorResetModule })
        register({ VisualFmeModule })
        register({ LocationScannerModule })
        register({ SpawnTimerModule })
        register({ PrefireWaypointsModule })
        register({ TrueSplitsModule })
        register({ LastBreathUtilsModule })
        register({ WitherEspModule })
        register({ PositionalMessagesModule })
        register({ ArrowAlignModule })
        register({ SoulsandAuraModule }, ModuleDistribution.FULL)
        register({ LightsDeviceModule })
        register({ MaxorHpHudModule })
        register({ SkeletonHorseModule })
        register({ ArcherUtilsModule })
        register({ GyroWaypointsModule })
        register({ TermGuiModule })
        register({ ThreeByThreeHighlightModule })
        register({ CowHatModule })
        register({ ArrowStackWaypointsModule })
        register({ BlockPickobulusModule })
        register({ KuudraWaypointModule })
        register({ PearlPredictionModule })
        register({ BuildProgressDisplayModule })
        register({ StunWaypointModule })
        register({ SkipWaypointModule })
        register({ KuudraDirectionModule })
        register({ CleanScoreboardModule })
        register({ ModuleListModule() })
        register({ ItemRarityModule })
        register({ PlayerCustomisationsModule })
        register({ DiscordRichPresenceModule() })
        register({ HeadsScaleModule })
        register({ PlayerParticlesModule })
        register({ TrailModule })
    }

    fun bootstrap() {
        if (modules.isNotEmpty()) return
        modules += moduleRegistry.createModulesFor(ModuleDistribution.LEGIT)
    }

    fun registerExternalModules(newModules: Collection<Module>) {
        for (module in newModules) {
            modules.removeIf { it.name.equals(module.name, ignoreCase = true) }
            modules += module
        }
    }

    fun modulesIn(category: ModuleCategory, search: String): List<Module> {
        val q = search.trim().lowercase()
        return modules.filter {
            it.category == category && (q.isBlank() || it.name.lowercase().contains(q))
        }
    }

    fun groupedCategories(): Map<String, List<ModuleCategory>> {
        val grouped = linkedMapOf<String, MutableList<ModuleCategory>>()
        for (category in ModuleCategory.entries) {
            grouped.getOrPut(category.group) { mutableListOf() } += category
        }
        return grouped.mapValues { (_, categories) -> categories.toList() }
    }

    fun onClientTick(client: Minecraft) {
        val window = client.window

        for (module in modules) {
            val key = module.bindKey
            if (key != GLFW.GLFW_KEY_UNKNOWN) {
                val isPressed = InputConstants.isKeyDown(window, key)
                val wasPressed = pressedKeys.contains(key)

                if (isPressed && !wasPressed && client.screen == null) {
                    module.toggle()
                }

                if (isPressed) {
                    pressedKeys += key
                } else {
                    pressedKeys -= key
                }
            }

            if (module.enabled) {
                module.onTick()
            }
        }
    }

}
