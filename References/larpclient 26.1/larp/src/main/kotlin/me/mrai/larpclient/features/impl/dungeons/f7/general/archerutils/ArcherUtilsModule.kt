package me.mrai.larpclient.features.impl.dungeons.f7.general.archerutils

import me.mrai.larpclient.module.BoolSetting
import me.mrai.larpclient.module.Module
import me.mrai.larpclient.module.ModuleCategory
import me.mrai.larpclient.module.SliderSetting
import me.mrai.larpclient.ui.hud.ArcherUtilsHudRenderer
import me.mrai.larpclient.util.LarpBranding
import me.mrai.larpclient.util.TextSanitizer
import net.minecraft.client.Minecraft
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.BowItem
import net.minecraft.world.item.ItemStack
import net.minecraft.world.phys.AABB
import java.util.UUID
import kotlin.math.roundToInt

object ArcherUtilsModule : Module(
    name = "Archer Utils",
    description = "Spray and death bow helpers for Floor 7.",
    category = ModuleCategory.DUNGEONS_F7_P1
) {
    val showChat = BoolSetting("Show Chat", true)
    val showTitle = BoolSetting("Show Title", true)
    val titleBold = BoolSetting("Title Bold", true)

    val sprayRed = SliderSetting("Spray Red", 85.0, 0.0, 255.0, 1.0)
    val sprayGreen = SliderSetting("Spray Green", 255.0, 0.0, 255.0, 1.0)
    val sprayBlue = SliderSetting("Spray Blue", 255.0, 0.0, 255.0, 1.0)

    val deathRed = SliderSetting("Death Red", 0.0, 0.0, 255.0, 1.0)
    val deathGreen = SliderSetting("Death Green", 170.0, 0.0, 255.0, 1.0)
    val deathBlue = SliderSetting("Death Blue", 0.0, 0.0, 255.0, 1.0)

    private var lastUseDown = false
    private var usingDeathBow = false
    private var deathBowChargeTicks = 0

    private var sprayWindowTicks = 0
    private val sprayedMobIds = linkedSetOf<UUID>()

    private var deathWindowTicks = 0
    private val deathTrackedIds = linkedSetOf<UUID>()
    private var deathKills = 0

    init {
        settings += listOf(
            showChat,
            showTitle,
            titleBold,
            sprayRed,
            sprayGreen,
            sprayBlue,
            deathRed,
            deathGreen,
            deathBlue
        )
    }

    override fun onDisable() {
        resetState()
    }

    override fun onTick() {
        val mc = Minecraft.getInstance()
        val player = mc.player ?: return
        val useDown = mc.options.keyUse.isDown
        val held = player.mainHandItem

        if (useDown && !lastUseDown && isIceSpray(held)) {
            startSprayWindow()
        }

        val holdingDeathBow = isDeathBow(held)
        if (player.isUsingItem && holdingDeathBow && held.item is BowItem) {
            usingDeathBow = true
            deathBowChargeTicks = player.ticksUsingItem
        }

        if (!useDown && lastUseDown && usingDeathBow && holdingDeathBow && deathBowChargeTicks >= 18) {
            startDeathBowWindow(player)
        }

        if (!player.isUsingItem || !holdingDeathBow) {
            usingDeathBow = false
            deathBowChargeTicks = 0
        }

        if (sprayWindowTicks > 0) {
            sprayWindowTicks--
            collectSprayedMobs(player)
            if (sprayWindowTicks == 0) {
                publishSprayedCount(sprayedMobIds.size)
                sprayedMobIds.clear()
            }
        }

        if (deathWindowTicks > 0) {
            deathWindowTicks--
            collectDeathBowKills(mc)
            if (deathWindowTicks == 0) {
                publishDeathBowKills(deathKills)
                deathTrackedIds.clear()
                deathKills = 0
            }
        }

        lastUseDown = useDown
    }

    private fun startSprayWindow() {
        sprayWindowTicks = 8
        sprayedMobIds.clear()
    }

    private fun startDeathBowWindow(player: Player) {
        deathWindowTicks = 20
        deathTrackedIds.clear()
        deathKills = 0
        val level = player.level() ?: return
        val center = player.position()
        val nearby = AABB(center.x - 24.0, center.y - 12.0, center.z - 24.0, center.x + 24.0, center.y + 12.0, center.z + 24.0)
        level.getEntitiesOfClass(LivingEntity::class.java, nearby) { entity ->
            entity.isAlive && entity !is Player && entity !is ArmorStand
        }.forEach { deathTrackedIds += it.uuid }
    }

    private fun collectSprayedMobs(player: Player) {
        val level = player.level() ?: return
        val search = player.boundingBox.inflate(5.0)
        val mobs = level.getEntitiesOfClass(LivingEntity::class.java, search) { entity ->
            entity.isAlive && entity !is Player && entity !is ArmorStand && entity.hurtTime > 0
        }

        for (mob in mobs) {
            if (hasNearbyIceStand(level, mob)) {
                sprayedMobIds += mob.uuid
            }
        }
    }

    private fun hasNearbyIceStand(level: net.minecraft.world.level.Level, mob: LivingEntity): Boolean {
        val box = mob.boundingBox.inflate(1.8, 2.5, 1.8)
        return level.getEntitiesOfClass(ArmorStand::class.java, box) { stand ->
            stand.isAlive && hasIceEquipment(stand)
        }.isNotEmpty()
    }

    private fun hasIceEquipment(stand: ArmorStand): Boolean {
        val stacks = listOf(
            stand.mainHandItem,
            stand.offhandItem,
            stand.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD),
            stand.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST),
            stand.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.LEGS),
            stand.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.FEET)
        )
        return stacks.any(::isIceStack)
    }

    private fun isIceStack(stack: ItemStack): Boolean {
        if (stack.isEmpty) return false
        val compact = TextSanitizer.compactLower(stack.hoverName.string)
        return compact.contains("ice") || compact.contains("packedice") || compact.contains("blueice") || compact.contains("frostedice")
    }

    private fun collectDeathBowKills(mc: Minecraft) {
        val level = mc.level ?: return
        val liveIds = level.entitiesForRendering().map { it.uuid }.toHashSet()
        val iterator = deathTrackedIds.iterator()
        while (iterator.hasNext()) {
            val id = iterator.next()
            if (id !in liveIds) {
                deathKills++
                iterator.remove()
            }
        }
    }

    private fun publishSprayedCount(count: Int) {
        if (count <= 0) return
        if (showChat.value) {
            sendPrefixedChat("${count} mobs sprayed!")
        }
        if (showTitle.value) {
            ArcherUtilsHudRenderer.showSpray(count)
        }
    }

    private fun publishDeathBowKills(count: Int) {
        if (count <= 0) return
        if (showChat.value) {
            sendPrefixedChat("Killed $count")
        }
        if (showTitle.value) {
            ArcherUtilsHudRenderer.showDeathBowKills(count)
        }
    }

    private fun sendPrefixedChat(message: String) {
        val player = Minecraft.getInstance().player ?: return
        player.sendSystemMessage(LarpBranding.prefixed(message))
    }

    fun sprayColor(): Int = argb(255, sprayRed.value.roundToInt(), sprayGreen.value.roundToInt(), sprayBlue.value.roundToInt())
    fun deathColor(): Int = argb(255, deathRed.value.roundToInt(), deathGreen.value.roundToInt(), deathBlue.value.roundToInt())

    private fun argb(a: Int, r: Int, g: Int, b: Int): Int {
        return ((a and 255) shl 24) or ((r and 255) shl 16) or ((g and 255) shl 8) or (b and 255)
    }

    private fun isIceSpray(stack: ItemStack): Boolean = TextSanitizer.compactLower(stack.hoverName.string).contains("icespray")
    private fun isDeathBow(stack: ItemStack): Boolean = TextSanitizer.compactLower(stack.hoverName.string).contains("deathbow")

    private fun resetState() {
        lastUseDown = false
        usingDeathBow = false
        deathBowChargeTicks = 0
        sprayWindowTicks = 0
        sprayedMobIds.clear()
        deathWindowTicks = 0
        deathTrackedIds.clear()
        deathKills = 0
    }
}
