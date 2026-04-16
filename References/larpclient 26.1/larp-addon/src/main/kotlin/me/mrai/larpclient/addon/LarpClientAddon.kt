package me.mrai.larpclient.addon

import me.mrai.larpclient.auth.AuthManager
import me.mrai.larpclient.auth.AuthState
import me.mrai.larpclient.command.DotCommandRouter
import me.mrai.larpclient.command.DotCommandHelp
import me.mrai.larpclient.command.LarpCommand
import me.mrai.larpclient.command.P3Command
import me.mrai.larpclient.features.impl.dungeons.f7.p3.autop3.AutoP3Module
import me.mrai.larpclient.features.impl.dungeons.f7.p3.autoss.AutoSSModule
import me.mrai.larpclient.features.impl.dungeons.f7.p5.arrowstackwaypoints.ArrowStackWaypointsModule
import me.mrai.larpclient.features.impl.dungeons.f7.p5.autocowhat.AutoCowHatModule
import me.mrai.larpclient.features.impl.dungeons.f7.p5.debuffsnap.DebuffSnapModule
import me.mrai.larpclient.features.impl.dungeons.f7.predev.lightsdevice.LeverAuraModule
import me.mrai.larpclient.features.impl.dungeons.f7.predev.soulsandaura.SoulsandAuraModule
import me.mrai.larpclient.features.impl.dungeons.general.dungeonbreakernuker.DungeonbreakerNukerModule
import me.mrai.larpclient.features.impl.dungeons.general.velocitybuffer.VelocityBufferModule
import me.mrai.larpclient.features.impl.kuudra.general.leftclickshop.LeftClickShopListener
import me.mrai.larpclient.features.impl.kuudra.general.leftclickshop.LeftClickShopModule
import me.mrai.larpclient.features.impl.kuudra.p1.autopearls.AutoPearlsModule
import me.mrai.larpclient.features.impl.kuudra.p1.autopearls.PearlBypassController
import me.mrai.larpclient.features.impl.kuudra.p1.autoroutetopre.AutoRouteToPreModule
import me.mrai.larpclient.features.impl.kuudra.p1.autoroutetopre.PreRouteManager
import me.mrai.larpclient.features.impl.kuudra.p1.autoroutetopre.PreRouteRenderer
import me.mrai.larpclient.features.impl.kuudra.p1.crateaura.CrateAuraListener
import me.mrai.larpclient.features.impl.kuudra.p1.crateaura.CrateAuraModule
import me.mrai.larpclient.features.impl.kuudra.p1.supplyhitboxes.SupplyHitboxesModule
import me.mrai.larpclient.features.impl.kuudra.p1.summoncrates.SummonCratesSimulator
import me.mrai.larpclient.features.impl.kuudra.p1.tentdangerhorse.TentDangerHorseModule
import me.mrai.larpclient.features.impl.kuudra.p3.skipwaypoint.SkipWaypointModule
import me.mrai.larpclient.features.impl.kuudra.p4.backbonelock.BackboneLockModule
import me.mrai.larpclient.features.impl.misc.other.etherwarp.EtherwarpController
import me.mrai.larpclient.features.impl.misc.ui.playercustomisations.PlayerCustomisationsModule
import me.mrai.larpclient.features.impl.skyblock.general.blink.BlinkModule
import me.mrai.larpclient.features.impl.skyblock.general.cancelinteract.CancelInteractModule
import me.mrai.larpclient.features.impl.skyblock.general.cgywardrobe.CgyWardrobeModule
import me.mrai.larpclient.features.impl.skyblock.general.simpleautoroutes.SimpleAutoroutesModule
import me.mrai.larpclient.integration.AddonAutomationAccess
import me.mrai.larpclient.module.Module
import me.mrai.larpclient.module.ModuleConfigManager
import me.mrai.larpclient.module.ModuleManager
import me.mrai.larpclient.auth.LicenseConfigManager
import me.mrai.larpclient.presence.HeartbeatClientType
import me.mrai.larpclient.presence.HeartbeatManager
import me.mrai.larpclient.util.LarpBranding
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents
import net.minecraft.network.chat.Component

object LarpClientAddon : ClientModInitializer {
    private val worldChangeHandlers = listOf(
        AutoP3Module::onWorldChange,
        AutoCowHatModule::onWorldChange,
        VelocityBufferModule::onWorldChange,
        DebuffSnapModule::onWorldChange,
        AutoSSModule::onWorldChange,
        BlinkModule::onWorldChange,
        SimpleAutoroutesModule::onWorldChange,
        SummonCratesSimulator::onWorldChange,
        TentDangerHorseModule::onWorldChange
    )

    private val renderers: List<(LevelRenderContext) -> Unit> = listOf(
        DungeonbreakerNukerModule::render,
        PreRouteRenderer::render,
        CrateAuraModule::render,
        SupplyHitboxesModule::render,
        AutoP3Module::render,
        AutoCowHatModule::render,
        DebuffSnapModule::render,
        EtherwarpController::render,
        BlinkModule::render,
        SimpleAutoroutesModule::render
    )

    private val fullModules: List<Module> by lazy {
        listOf(
            DungeonbreakerNukerModule,
            VelocityBufferModule,
            AutoP3Module,
            AutoSSModule,
            ArrowStackWaypointsModule,
            AutoCowHatModule,
            DebuffSnapModule,
            LeverAuraModule,
            SoulsandAuraModule,
            LeftClickShopModule,
            AutoPearlsModule,
            AutoRouteToPreModule,
            CrateAuraModule,
            SupplyHitboxesModule,
            TentDangerHorseModule,
            SkipWaypointModule,
            BackboneLockModule,
            BlinkModule,
            CancelInteractModule,
            CgyWardrobeModule,
            SimpleAutoroutesModule
        )
    }

    private var authenticatedServicesInitialized = false

    override fun onInitializeClient() {
        AddonCommandAccess.accessCheck = { AuthManager.state == AuthState.AUTHENTICATED }
        AddonAutomationAccess.hasAddonFeatures = { true }
        AddonAutomationAccess.isAutomationEnabled = AddonCommandAccess::granted
        HeartbeatManager.configure(
            clientType = HeartbeatClientType.ADDON,
            modId = "larpclient-addon",
            userAgentProduct = "LarpClient",
            licenseKeyProvider = { LicenseConfigManager.load().licenseKey },
            playerCustomizationProvider = PlayerCustomisationsModule::heartbeatPayload
        )
        HeartbeatManager.start()

        registerHelp()
        registerCommands()
        registerListeners()
        registerLifecycle()
        registerRenderers()

        AuthManager.beginAuthentication()
    }

    private fun registerHelp() {
        DotCommandRouter.registerManagedRoot("p3")

        DotCommandHelp.registerRootLines {
            listOf(
                helpEntry(".p3 help", "P3 ring and route tools."),
                helpFooter("Install the LarpClient addon to enable paid command roots.")
            )
        }

        DotCommandHelp.registerLarpLines {
            buildList {
                add(helpEntry(".larp vb on|off|pop|flush", "Velocity Buffer controls."))
                add(helpEntry(".larp debuff <radius>", "Add a Debuff Snap ring at your feet."))
                add(helpEntry(".larp etherwarp", "Toggle etherwarp helper mode."))
                add(helpEntry(".larp pearlbypass", "Toggle pearl bypass timing."))
                add(helpEntry(".larp autopearl p1|p2", "Queue debug auto-pearl tests."))
                add(helpEntry(".larp summoncrates", "Summon local Kuudra crate simulators for your current pre."))
                add(helpEntry(".larp soulsand", "Toggle Soulsand Aura edit mode."))
                add(helpEntry(".larp blink <route> <packets>", "Add a blink trigger ring."))
                add(helpEntry(".larp add cow <x> <y> <z>", "Add a Cow Hat zone at your feet."))
                add(helpEntry(".larp clearcow", "Clear all Cow Hat zones."))
                add(helpEntry(".larp preroute <name> add|clear|remove", "Edit preroutes for pre spots."))
                addAll(AddonDotCommandHelp.simpleAutoroutesLines())
            }
        }
    }

    private fun registerCommands() {
        LarpCommand.registerChildren(LarpClientAddonCommands::register)
        ClientCommandRegistrationCallback.EVENT.register { dispatcher, _ ->
            dispatcher.register(P3Command.build())
        }
    }

    private fun registerListeners() {
        LeftClickShopListener.register()
        CrateAuraListener.register()
    }

    private fun registerLifecycle() {
        ClientPlayConnectionEvents.DISCONNECT.register { _, _ ->
            worldChangeHandlers.forEach { it() }
        }

        ClientTickEvents.END_CLIENT_TICK.register { client ->
            HeartbeatManager.tick()

            val authenticated = AuthManager.poll(client)
            if (!authenticated) {
                return@register
            }

            initializeAuthenticatedServices()
            PearlBypassController.onClientTick()
            EtherwarpController.onClientTick(client)
            SummonCratesSimulator.onTick()
        }
    }

    private fun registerRenderers() {
        LevelRenderEvents.END_MAIN.register { context ->
            if (!AddonCommandAccess.granted()) return@register
            renderers.forEach { renderer -> renderer(context) }
        }
    }

    private fun initializeAuthenticatedServices() {
        if (authenticatedServicesInitialized) return

        ModuleManager.registerExternalModules(fullModules)
        ModuleConfigManager.load(fullModules)
        PreRouteManager.load()

        authenticatedServicesInitialized = true
    }

    private fun helpEntry(command: String, description: String): Component {
        return LarpBranding.prefixed(
            LarpBranding.command(command)
                .append(LarpBranding.muted(" - "))
                .append(LarpBranding.text(description, LarpBranding.WHITE))
        )
    }

    private fun helpFooter(text: String): Component {
        return LarpBranding.prefixed(LarpBranding.text(text, LarpBranding.GRAY))
    }
}
