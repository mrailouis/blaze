package me.mrai.larpclient.features.impl.kuudra.p1.autoroutetopre

import me.mrai.larpclient.features.impl.kuudra.p1.autoroutetopre.PreRouteManager
import me.mrai.larpclient.features.impl.kuudra.p1.autoroutetopre.PreRouteName
import me.mrai.larpclient.mixin.MinecraftInvoker
import me.mrai.larpclient.module.ModeSetting
import me.mrai.larpclient.module.Module
import me.mrai.larpclient.module.ModuleCategory
import me.mrai.larpclient.module.SliderSetting
import net.minecraft.client.Minecraft
import net.minecraft.client.player.LocalPlayer
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import kotlin.math.atan2
import kotlin.math.sqrt

object AutoRouteToPreModule : Module(
    name = "Auto Route to Pre Locations",
    description = "Renders and plays saved preroutes.",
    category = ModuleCategory.KUUDRA_P1
) {
    val targetSetting = ModeSetting("Target", listOf("All", "Tri", "X", "Slash", "Equals"), 0)
    private val firstWarpDelaySetting = SliderSetting("First Warp Delay", 1.0, 0.0, 20.0, 1.0)
    private val chainDelaySetting = SliderSetting("Chain Delay", 5.0, 0.0, 20.0, 1.0)

    private var activeRoute: PreRouteName? = null
    private var activeIndex = -1
    private var blockedTriggerBlock: BlockPos? = null
    private var firedForIndex = -1
    private var wasSneakingForced = false

    private var tickCounter = 0L
    private var nextUseTick = 0L
    private var firstWarpPending = false

    init {
        settings += targetSetting
        settings += firstWarpDelaySetting
        settings += chainDelaySetting
    }

    override fun onEnable() {
        PreRouteManager.load()
        resetRuntimeState()
    }

    override fun onDisable() {
        stopPlayback()
        resetRuntimeState()
    }

    override fun onTick() {
        tickCounter++

        val client = Minecraft.getInstance()
        val player = client.player ?: return
        if (client.screen != null) return

        val standingBlock = player.blockPosition().below()

        if (blockedTriggerBlock != null && blockedTriggerBlock != standingBlock) {
            blockedTriggerBlock = null
        }

        if (activeRoute != null) {
            tickPlayback(client, player)
            return
        }

        if (blockedTriggerBlock == standingBlock) return

        val filter = currentRouteFilter()

        for (route in PreRouteManager.getAll()) {
            if (filter != null && route.name != filter) continue

            val segments = route.segments
            if (segments.isEmpty()) continue

            val standingIndex = segments.indexOfFirst { it.triggerBlock == standingBlock }
            if (standingIndex == -1) continue

            activeRoute = route.name
            activeIndex = standingIndex
            firedForIndex = -1
            blockedTriggerBlock = standingBlock

            firstWarpPending = true
            nextUseTick = tickCounter + firstWarpDelayTicks()

            tickPlayback(client, player)
            return
        }
    }

    private fun tickPlayback(client: Minecraft, player: LocalPlayer) {
        val routeName = activeRoute ?: run {
            stopPlayback()
            return
        }

        val route = PreRouteManager.get(routeName) ?: run {
            stopPlayback()
            return
        }

        val segments = route.segments

        if (tickCounter < nextUseTick) return

        var safetyIterator = 0
        while (activeIndex in segments.indices && safetyIterator < 10) {
            safetyIterator++
            val segment = segments[activeIndex]

            val isAtTrigger =
                player.blockPosition().below() == segment.triggerBlock || firedForIndex == activeIndex - 1

            if (!isAtTrigger) break

            forceSneak(client, true)

            lookAtPoint(
                player.x,
                player.eyeY,
                player.z,
                segment.hitX,
                segment.hitY,
                segment.hitZ
            )

            val hit = player.pick(5.0, 0.0f, false)
            if (hit.type == HitResult.Type.BLOCK) {
                val blockHit = hit as BlockHitResult
                if (blockHit.blockPos != segment.destinationBlock) {
                    break
                }
            }

            val invoker = client as MinecraftInvoker
            invoker.callStartUseItem()

            firedForIndex = activeIndex
            activeIndex++

            firstWarpPending = false
            nextUseTick = tickCounter + chainDelayTicks()

            if (chainDelayTicks() > 0) break
        }

        if (activeIndex >= segments.size) {
            stopPlayback()
        }
    }

    fun shouldRender(route: PreRouteName): Boolean {
        if (!enabled) return false
        return when (targetSetting.selected.lowercase()) {
            "all" -> true
            "tri" -> route == PreRouteName.TRI
            "x" -> route == PreRouteName.X
            "slash" -> route == PreRouteName.SLASH
            "equals" -> route == PreRouteName.EQUALS
            else -> false
        }
    }

    private fun currentRouteFilter(): PreRouteName? {
        return when (targetSetting.selected.lowercase()) {
            "tri" -> PreRouteName.TRI
            "x" -> PreRouteName.X
            "slash" -> PreRouteName.SLASH
            "equals" -> PreRouteName.EQUALS
            else -> null
        }
    }

    private fun firstWarpDelayTicks(): Long {
        return firstWarpDelaySetting.value.toLong().coerceAtLeast(0L)
    }

    private fun chainDelayTicks(): Long {
        return chainDelaySetting.value.toLong().coerceAtLeast(0L)
    }

    private fun stopPlayback() {
        val client = Minecraft.getInstance()
        if (wasSneakingForced) {
            forceSneak(client, false)
        }

        activeRoute = null
        activeIndex = -1
        firedForIndex = -1
        firstWarpPending = false
        nextUseTick = 0L
        wasSneakingForced = false
    }

    private fun resetRuntimeState() {
        activeRoute = null
        activeIndex = -1
        blockedTriggerBlock = null
        firedForIndex = -1
        firstWarpPending = false
        nextUseTick = 0L
        wasSneakingForced = false
        tickCounter = 0L
    }

    private fun forceSneak(client: Minecraft, value: Boolean) {
        client.options.keyShift.isDown = value
        wasSneakingForced = value
    }

    private fun lookAtPoint(
        px: Double,
        py: Double,
        pz: Double,
        tx: Double,
        ty: Double,
        tz: Double
    ) {
        val client = Minecraft.getInstance()
        val player = client.player ?: return

        val dx = tx - px
        val dy = ty - py
        val dz = tz - pz

        val distHorizontal = sqrt(dx * dx + dz * dz)
        val yaw = Math.toDegrees(atan2(dz, dx)).toFloat() - 90f
        val pitch = (-Math.toDegrees(atan2(dy, distHorizontal))).toFloat()

        player.yRot = yaw
        player.xRot = pitch
    }
}