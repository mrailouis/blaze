package me.mrai.larpclient.features.impl.dungeons.f7.predev.lightsdevice

import net.minecraft.client.Minecraft
import net.minecraft.client.player.LocalPlayer
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.InteractionHand
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3
import kotlin.math.abs

object LeverInteractions {
    const val DEFAULT_INTERACTION_RANGE = 4.5

    fun isWithinInteractionRange(player: LocalPlayer, leverPos: BlockPos, range: Double = DEFAULT_INTERACTION_RANGE): Boolean {
        return player.eyePosition.distanceToSqr(Vec3.atCenterOf(leverPos)) <= range * range
    }

    fun tryToggleLever(client: Minecraft, player: LocalPlayer, leverPos: BlockPos): Boolean {
        val gameMode = client.gameMode ?: return false
        val hit = BlockHitResult(
            Vec3.atCenterOf(leverPos),
            clickSide(client, leverPos),
            leverPos,
            false
        )

        val result = gameMode.useItemOn(player, InteractionHand.MAIN_HAND, hit)
        if (result.consumesAction()) {
            player.swing(InteractionHand.MAIN_HAND)
            return true
        }
        return false
    }

    private fun clickSide(client: Minecraft, leverPos: BlockPos): Direction {
        val cameraPos = client.gameRenderer.mainCamera.position()
        val center = Vec3.atCenterOf(leverPos)
        val dx = cameraPos.x - center.x
        val dy = cameraPos.y - center.y
        val dz = cameraPos.z - center.z

        return when {
            abs(dx) >= abs(dy) && abs(dx) >= abs(dz) -> if (dx >= 0.0) Direction.EAST else Direction.WEST
            abs(dz) >= abs(dx) && abs(dz) >= abs(dy) -> if (dz >= 0.0) Direction.SOUTH else Direction.NORTH
            else -> if (dy >= 0.0) Direction.UP else Direction.DOWN
        }
    }
}
