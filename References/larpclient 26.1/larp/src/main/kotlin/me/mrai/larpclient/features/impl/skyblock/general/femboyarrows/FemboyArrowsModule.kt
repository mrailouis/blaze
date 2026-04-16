package me.mrai.larpclient.features.impl.skyblock.general.femboyarrows

import me.mrai.larpclient.module.Module
import me.mrai.larpclient.module.ModuleCategory
import me.mrai.larpclient.module.SliderSetting
import net.minecraft.client.Minecraft
import net.minecraft.resources.Identifier
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundSource
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.entity.player.Player
import net.minecraft.world.entity.projectile.arrow.AbstractArrow
import java.util.UUID

object FemboyArrowsModule : Module(
    name = "Femboy Arrows",
    description = "Plays a custom meow clip when your arrows hit mobs.",
    category = ModuleCategory.SKYBLOCK_GENERAL
) {
    private val volume = SliderSetting("Volume", 1.0, 0.05, 2.0, 0.05)
    private val pitch = SliderSetting("Pitch", 1.0, 0.5, 2.0, 0.05)

    private val triggeredArrowIds = linkedSetOf<UUID>()
    private val meowSoundId = Identifier.fromNamespaceAndPath("larpclient", "femboy_arrows.meow")
    private val meowSoundEvent = SoundEvent.createVariableRangeEvent(meowSoundId)

    init {
        settings += volume
        settings += pitch
    }

    override fun onDisable() {
        triggeredArrowIds.clear()
    }

    override fun onTick() {
        val client = Minecraft.getInstance()
        val player = client.player
        val level = client.level

        if (player == null || level == null) {
            triggeredArrowIds.clear()
            return
        }

        val activeArrowIds = hashSetOf<UUID>()
        val targets = ArrayList<LivingEntity>()
        for (entity in level.entitiesForRendering()) {
            val living = entity as? LivingEntity ?: continue
            if (!living.isAlive) continue
            if (living is Player) continue
            if (living is ArmorStand) continue
            targets += living
        }

        for (entity in level.entitiesForRendering()) {
            val arrow = entity as? AbstractArrow ?: continue
            val owner = arrow.owner as? Player ?: continue
            if (owner.uuid != player.uuid) continue

            activeArrowIds += arrow.uuid
            if (arrow.uuid in triggeredArrowIds) continue
            if (!arrow.isAlive) continue

            val hit = targets.any { target ->
                target.boundingBox.inflate(0.2).intersects(arrow.boundingBox.inflate(0.15))
            }
            if (!hit) continue

            triggeredArrowIds += arrow.uuid
            level.playLocalSound(
                player.x,
                player.y,
                player.z,
                meowSoundEvent,
                SoundSource.PLAYERS,
                volume.value.toFloat(),
                pitch.value.toFloat(),
                false
            )
        }

        triggeredArrowIds.retainAll(activeArrowIds)
    }
}
