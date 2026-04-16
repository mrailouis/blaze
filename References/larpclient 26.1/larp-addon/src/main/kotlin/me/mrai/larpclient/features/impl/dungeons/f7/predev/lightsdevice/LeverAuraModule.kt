package me.mrai.larpclient.features.impl.dungeons.f7.predev.lightsdevice

import me.mrai.larpclient.module.Module
import me.mrai.larpclient.module.ModuleCategory
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.LeverBlock
import net.minecraft.world.phys.Vec3
import kotlin.math.floor

object LeverAuraModule : Module(
    name = "Lever Aura",
    description = "Flicks nearby levers outside the lights device.",
    category = ModuleCategory.DUNGEONS_F7_PREDEV
) {
    private const val PULSE_TICKS = 20

    private var ticksUntilPulse = 0

    override fun onEnable() {
        ticksUntilPulse = 0
    }

    override fun onDisable() {
        ticksUntilPulse = 0
    }

    override fun onTick() {
        if (ticksUntilPulse > 0) {
            ticksUntilPulse--
            return
        }
        ticksUntilPulse = PULSE_TICKS

        val client = Minecraft.getInstance()
        val player = client.player ?: return
        val level = client.level ?: return
        if (client.screen != null) return

        val range = LeverInteractions.DEFAULT_INTERACTION_RANGE
        val origin = player.eyePosition
        val minX = floor(origin.x - range).toInt()
        val maxX = floor(origin.x + range).toInt()
        val minY = floor(origin.y - range).toInt()
        val maxY = floor(origin.y + range).toInt()
        val minZ = floor(origin.z - range).toInt()
        val maxZ = floor(origin.z + range).toInt()

        val nearbyLevers = mutableListOf<BlockPos>()
        for (x in minX..maxX) {
            for (y in minY..maxY) {
                for (z in minZ..maxZ) {
                    val pos = BlockPos(x, y, z)
                    if (LightsDeviceModule.isManagedLever(pos)) continue
                    val state = level.getBlockState(pos)
                    if (state.block !is LeverBlock) continue
                    if (!LeverInteractions.isWithinInteractionRange(player, pos, range)) continue
                    nearbyLevers += pos
                }
            }
        }

        nearbyLevers
            .sortedBy { origin.distanceToSqr(Vec3.atCenterOf(it)) }
            .forEach { LeverInteractions.tryToggleLever(client, player, it) }
    }
}
