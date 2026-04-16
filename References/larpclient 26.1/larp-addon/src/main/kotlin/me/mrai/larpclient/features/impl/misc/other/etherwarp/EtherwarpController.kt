package me.mrai.larpclient.features.impl.misc.other.etherwarp

import me.mrai.larpclient.mixin.MultiPlayerGameModeInvoker
import me.mrai.larpclient.render.WorldBoxRenderer
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext
import net.minecraft.client.Minecraft
import net.minecraft.world.InteractionHand
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket
import net.minecraft.network.protocol.game.ServerboundPlayerInputPacket
import net.minecraft.network.protocol.game.ServerboundUseItemPacket
import net.minecraft.core.BlockPos
import net.minecraft.world.level.ClipContext
import net.minecraft.world.item.Items
import net.minecraft.world.entity.player.Input
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3

object EtherwarpController {
    private const val MAX_RANGE = 61.0
    private const val FORWARD_TELEPORT_DISTANCE = 12.0
    private const val ACTION_COOLDOWN_MS = 100L
    private const val SAFE_SEARCH_STEP = 0.25
    private const val HIT_PADDING = 0.35

    private var enabled = false
    private var preview: PreviewTarget? = null
    private var lastActionMs = 0L

    private data class PreviewTarget(
        val pos: BlockPos,
        val teleportPosX: Double,
        val teleportPosY: Double,
        val teleportPosZ: Double,
        val valid: Boolean
    )

    fun toggle(): Boolean {
        enabled = !enabled
        if (!enabled) {
            preview = null
            lastActionMs = 0L
        }
        return enabled
    }

    fun onClientTick(client: Minecraft) {
        if (!enabled) {
            preview = null
            return
        }

        val player = client.player ?: run {
            preview = null
            return
        }

        if (client.screen != null) {
            preview = null
            return
        }

        if (!isHoldingTeleportShovel(player)) {
            preview = null
            return
        }

        preview = if (player.isShiftKeyDown) {
            findPreview(client)
        } else {
            null
        }
    }

    fun render(context: LevelRenderContext) {
        if (!enabled) return

        val target = preview ?: return
        val style = if (target.valid) {
            WorldBoxRenderer.BoxStyle(
                fillRed = 0.15f,
                fillGreen = 0.8f,
                fillBlue = 0.2f,
                fillAlpha = 0.22f,
                outlineRed = 0.2f,
                outlineGreen = 1.0f,
                outlineBlue = 0.3f,
                outlineAlpha = 0.95f,
                outlineThickness = 0.03f
            )
        } else {
            WorldBoxRenderer.BoxStyle(
                fillRed = 0.9f,
                fillGreen = 0.15f,
                fillBlue = 0.15f,
                fillAlpha = 0.22f,
                outlineRed = 1.0f,
                outlineGreen = 0.2f,
                outlineBlue = 0.2f,
                outlineAlpha = 0.95f,
                outlineThickness = 0.03f
            )
        }

        WorldBoxRenderer.render(
            context,
            listOf(AABB(target.pos).inflate(0.002) to style)
        )
    }

    private fun findPreview(client: Minecraft): PreviewTarget? {
        val player = client.player ?: return null
        val level = player.level()
        val camera = client.gameRenderer.mainCamera
        val start = camera.position()
        val look = player.getViewVector(1.0f)
        val end = start.add(look.scale(MAX_RANGE))

        val hit = level.clip(
            ClipContext(
                start,
                end,
                ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE,
                player
            )
        )

        if (hit.type != HitResult.Type.BLOCK) return null
        val blockHit = hit as BlockHitResult
        val pos = blockHit.blockPos
        val above = pos.above()
        val above2 = above.above()
        val valid = !level.getBlockState(pos).isAir &&
            level.getBlockState(above).isAir &&
            level.getBlockState(above2).isAir &&
            canOccupy(player, Vec3(pos.x + 0.5, pos.y + 1.0, pos.z + 0.5))

        return PreviewTarget(
            pos = pos,
            teleportPosX = pos.x + 0.5,
            teleportPosY = pos.y + 1.0,
            teleportPosZ = pos.z + 0.5,
            valid = valid
        )
    }

    fun handleUseItem(client: Minecraft): Boolean {
        if (!enabled) return false

        val player = client.player ?: return false
        if (client.screen != null) return false
        if (!isHoldingTeleportShovel(player)) return false
        val now = System.currentTimeMillis()
        if (now - lastActionMs < ACTION_COOLDOWN_MS) return true

        val didTeleport = if (player.isShiftKeyDown) {
            val target = findPreview(client) ?: return true
            if (!target.valid) return true
            performEtherwarpTo(
                client,
                Vec3(target.teleportPosX, target.teleportPosY, target.teleportPosZ)
            )
        } else {
            val destination = computeForwardDestination(player) ?: return true
            performEtherwarpTo(client, destination)
        }

        if (didTeleport) {
            lastActionMs = now
        }
        return true
    }

    fun performEtherwarpTo(
        client: Minecraft,
        target: Vec3,
        lookOrigin: Vec3? = null,
        lightweightPackets: Boolean = false,
        forceSneakPacket: Boolean = false
    ): Boolean {
        val player = client.player ?: return false
        if (!isHoldingTeleportShovel(player)) return false
        if (!canOccupy(player, target)) return false

        val blockPos = BlockPos.containing(target.x, target.y - 1.0, target.z)
        val (yaw, pitch) = yawPitch(lookOrigin ?: player.eyePosition, target)

        if (client.hasSingleplayerServer() || client.isLocalServer) {
            teleportPlayer(player, target.x, target.y, target.z)
            preview = PreviewTarget(
                pos = blockPos,
                teleportPosX = target.x,
                teleportPosY = target.y,
                teleportPosZ = target.z,
                valid = true
            )
            lastActionMs = System.currentTimeMillis()
            return true
        }

        val gameMode = client.gameMode ?: return false
        val connection = client.connection ?: return false
        val level = client.level ?: return false
        val oldYaw = player.yRot
        val oldPitch = player.xRot
        val oldShift = player.isShiftKeyDown

        if (lightweightPackets) {
            if (forceSneakPacket && !oldShift) {
                connection.send(ServerboundPlayerInputPacket(Input(false, false, false, false, false, true, false)))
            }
            (gameMode as MultiPlayerGameModeInvoker).callStartPrediction(level) { sequence ->
                ServerboundUseItemPacket(InteractionHand.MAIN_HAND, sequence, yaw, pitch)
            }
            if (forceSneakPacket && !oldShift) {
                connection.send(ServerboundPlayerInputPacket(Input(false, false, false, false, false, false, false)))
            }
        } else {
            player.yRot = yaw
            player.xRot = pitch

            connection.send(ServerboundPlayerInputPacket(Input(false, false, false, false, false, true, false)))
            connection.send(ServerboundMovePlayerPacket.Rot(yaw, pitch, player.onGround(), player.horizontalCollision))
            connection.send(ServerboundPlayerInputPacket(Input(false, false, false, false, false, true, false)))
            (gameMode as MultiPlayerGameModeInvoker).callStartPrediction(level) { sequence ->
                ServerboundUseItemPacket(InteractionHand.MAIN_HAND, sequence, yaw, pitch)
            }

            player.yRot = oldYaw
            player.xRot = oldPitch
            connection.send(ServerboundMovePlayerPacket.Rot(oldYaw, oldPitch, player.onGround(), player.horizontalCollision))
            connection.send(ServerboundPlayerInputPacket(Input(false, false, false, false, false, oldShift, false)))
        }

        preview = PreviewTarget(
            pos = blockPos,
            teleportPosX = target.x,
            teleportPosY = target.y,
            teleportPosZ = target.z,
            valid = true
        )
        lastActionMs = System.currentTimeMillis()
        return true
    }

    fun simulateTeleportTo(client: Minecraft, target: Vec3): Boolean {
        if (!enabled) return false

        val player = client.player ?: return false
        if (client.screen != null) return false
        if (!isHoldingTeleportShovel(player)) return false

        val blockPos = BlockPos.containing(target.x, target.y - 1.0, target.z)
        val valid = canOccupy(player, target)
        if (!valid) return false

        teleportPlayer(player, target.x, target.y, target.z)
        preview = PreviewTarget(
            pos = blockPos,
            teleportPosX = target.x,
            teleportPosY = target.y,
            teleportPosZ = target.z,
            valid = true
        )
        lastActionMs = System.currentTimeMillis()
        return true
    }

    private fun yawPitch(from: Vec3, to: Vec3): Pair<Float, Float> {
        val dx = to.x - from.x
        val dy = to.y - from.y
        val dz = to.z - from.z
        val horizontal = kotlin.math.sqrt(dx * dx + dz * dz)
        val yaw = Math.toDegrees(kotlin.math.atan2(dz, dx)).toFloat() - 90f
        val pitch = (-Math.toDegrees(kotlin.math.atan2(dy, horizontal))).toFloat()
        return yaw to pitch
    }

    private fun computeForwardDestination(player: net.minecraft.client.player.LocalPlayer): Vec3? {
        val look = player.getViewVector(1.0f).normalize()
        val start = player.eyePosition
        val intendedEnd = start.add(
            look.x * FORWARD_TELEPORT_DISTANCE,
            look.y * FORWARD_TELEPORT_DISTANCE,
            look.z * FORWARD_TELEPORT_DISTANCE
        )
        val level = player.level()
        val hit = level.clip(
            ClipContext(
                start,
                intendedEnd,
                ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE,
                player
            )
        )

        if (hit.type == HitResult.Type.BLOCK) {
            val blockHit = hit as BlockHitResult
            val safeDistance = (start.distanceTo(blockHit.location) - HIT_PADDING).coerceAtLeast(0.0)
            return findFurthestSafeDestination(player, look, safeDistance)
        }

        return findFurthestSafeDestination(player, look, FORWARD_TELEPORT_DISTANCE)
    }

    private fun findFurthestSafeDestination(
        player: net.minecraft.client.player.LocalPlayer,
        look: Vec3,
        maxDistance: Double
    ): Vec3? {
        var distance = maxDistance
        while (distance >= 0.0) {
            val candidate = player.position().add(
                look.x * distance,
                look.y * distance,
                look.z * distance
            )
            if (canOccupy(player, candidate)) {
                return candidate
            }
            distance -= SAFE_SEARCH_STEP
        }
        return null
    }

    private fun canOccupy(player: net.minecraft.client.player.LocalPlayer, destination: Vec3): Boolean {
        val offset = destination.subtract(player.position())
        val targetBox = player.boundingBox.move(offset)
        return player.level().noCollision(player, targetBox)
    }

    private fun teleportPlayer(player: net.minecraft.client.player.LocalPlayer, x: Double, y: Double, z: Double) {
        val yaw = player.getYRot()
        val pitch = player.getXRot()
        player.snapTo(x, y, z, yaw, pitch)
        player.resetPos()
        player.setYRot(yaw)
        player.setXRot(pitch)
        player.setYHeadRot(yaw)
        player.setYBodyRot(yaw)
        player.yRotO = yaw
        player.xRotO = pitch
        player.setOldPosAndRot(player.position(), yaw, pitch)
    }

    private fun isHoldingTeleportShovel(player: net.minecraft.client.player.LocalPlayer): Boolean {
        return player.mainHandItem.`is`(Items.DIAMOND_SHOVEL)
    }
}
