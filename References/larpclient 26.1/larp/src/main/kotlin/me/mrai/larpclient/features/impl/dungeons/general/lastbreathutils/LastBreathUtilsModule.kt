package me.mrai.larpclient.features.impl.dungeons.general.lastbreathutils

import me.mrai.larpclient.module.BoolSetting
import me.mrai.larpclient.module.ComponentSetting
import me.mrai.larpclient.module.Module
import me.mrai.larpclient.module.ModuleCategory
import me.mrai.larpclient.ui.hud.LastBreathUtilsHudRenderer
import me.mrai.larpclient.util.TextSanitizer
import net.minecraft.client.Minecraft
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.entity.player.Player
import net.minecraft.world.entity.projectile.arrow.AbstractArrow
import net.minecraft.world.item.BowItem
import net.minecraft.world.item.ItemStack
import java.util.UUID

object LastBreathUtilsModule : Module(
    name = "Last Breath Utils",
    description = "Tracks Last Breath arrows and shows how many hit entities.",
    category = ModuleCategory.DUNGEONS_GENERAL
) {
    val showTitle = BoolSetting("On-screen Title", true)
    val titleBold = BoolSetting("Title Bold", true)
    val titlePreview = ComponentSetting("Title Text", "# LB's hit!")

    private var lastUsingLastBreath = false
    private var shotCaptureTicks = 0
    private var resultDelayTicks = 0

    private val trackedArrowIds = linkedSetOf<UUID>()
    private val countedArrowIds = linkedSetOf<UUID>()
    private var pendingHits = 0

    init {
        settings += listOf(
            showTitle,
            titleBold,
            titlePreview
        )
    }

    override fun onDisable() {
        resetState()
    }

    override fun onTick() {
        val mc = Minecraft.getInstance()
        val player = mc.player
        val level = mc.level

        if (player == null || level == null) {
            resetState()
            return
        }

        val mainHand = player.mainHandItem
        val holdingLastBreath = isLastBreathBow(mainHand) && mainHand.item is BowItem
        val usingLastBreath = holdingLastBreath && player.isUsingItem

        if (!usingLastBreath && lastUsingLastBreath) {
            shotCaptureTicks = 10
            resultDelayTicks = 12
            trackedArrowIds.clear()
            countedArrowIds.clear()
            pendingHits = 0
        }

        lastUsingLastBreath = usingLastBreath

        if (shotCaptureTicks > 0) {
            shotCaptureTicks--

            for (entity in level.entitiesForRendering()) {
                val arrow = entity as? AbstractArrow ?: continue
                val owner = arrow.owner as? Player ?: continue
                if (owner.uuid == player.uuid) {
                    trackedArrowIds += arrow.uuid
                }
            }
        }

        if (trackedArrowIds.isNotEmpty()) {
            val arrowsById = HashMap<UUID, AbstractArrow>()
            for (entity in level.entitiesForRendering()) {
                val arrow = entity as? AbstractArrow ?: continue
                arrowsById[arrow.uuid] = arrow
            }

            val targets = ArrayList<LivingEntity>()
            for (entity in level.entitiesForRendering()) {
                val living = entity as? LivingEntity ?: continue
                if (!living.isAlive) continue
                if (living is Player) continue
                if (living is ArmorStand) continue
                targets += living
            }

            for (arrowId in trackedArrowIds) {
                if (arrowId in countedArrowIds) continue

                val arrow = arrowsById[arrowId] ?: continue
                if (!arrow.isAlive) continue

                var hit = false
                for (target in targets) {
                    if (target.boundingBox.inflate(0.2).intersects(arrow.boundingBox.inflate(0.15))) {
                        hit = true
                        break
                    }
                }

                if (hit) {
                    countedArrowIds += arrowId
                    pendingHits++
                }
            }
        }

        if (resultDelayTicks > 0) {
            resultDelayTicks--
            if (resultDelayTicks == 0) {
                if (pendingHits > 0 && showTitle.value) {
                    LastBreathUtilsHudRenderer.showHits(pendingHits)
                }
                trackedArrowIds.clear()
                countedArrowIds.clear()
                pendingHits = 0
            }
        }
    }

    private fun isLastBreathBow(stack: ItemStack): Boolean {
        if (stack.isEmpty) return false
        return TextSanitizer.compactLower(stack.hoverName.string).contains("lastbreath")
    }

    private fun resetState() {
        lastUsingLastBreath = false
        shotCaptureTicks = 0
        resultDelayTicks = 0
        trackedArrowIds.clear()
        countedArrowIds.clear()
        pendingHits = 0
    }
}