package me.mrai.larpclient.features.impl.kuudra.p4.backbonelock

import me.mrai.larpclient.module.BoolSetting
import me.mrai.larpclient.module.ModeSetting
import me.mrai.larpclient.module.Module
import me.mrai.larpclient.module.ModuleCategory
import me.mrai.larpclient.util.LarpBranding
import net.minecraft.client.Minecraft
import net.minecraft.client.player.LocalPlayer
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.item.ItemStack
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import java.util.UUID
import kotlin.math.sqrt

object BackboneLockModule : Module(
    name = "Backbone Lock",
    description = "Locks your hotbar after a backbone bonemerang hit and can auto swap to your rend weapon.",
    category = ModuleCategory.KUUDRA_P4
) {
    private const val LOCK_TICKS = 20
    private const val BONE_STAND_RANGE = 2.35
    private const val DEBUG_SCAN_TICKS = 20

    private val rendWeaponSetting = ModeSetting(
        "Rend Weapon",
        listOf("Atomsplit", "Ice Spray", "Livid Dagger"),
        0
    )
    private val autoSwapSetting = BoolSetting("Auto Swap", true)
    private val debugArmorStandSetting = BoolSetting("Debug Armor Stands", true)

    private var previousMatchingStandIds: Set<UUID> = emptySet()
    private var sequenceTicksRemaining = 0
    private var lockedSlot = -1
    private var triggerCooldownTicks = 0

    private var debugWatchTicksRemaining = 0
    private var previousDebugStandIds: Set<UUID> = emptySet()
    private var wasUsePressed = false

    init {
        settings += listOf(rendWeaponSetting, autoSwapSetting, debugArmorStandSetting)
    }

    override fun onEnable() {
        resetState()
    }

    override fun onDisable() {
        resetState()
    }

    override fun onTick() {
        val client = client()
        val player = client.player ?: run {
            resetState()
            return
        }
        val level = client.level ?: run {
            resetState()
            return
        }

        if (triggerCooldownTicks > 0) {
            triggerCooldownTicks--
        }

        handleDebugRightClickTrigger(client, player)

        if (debugWatchTicksRemaining > 0) {
            debugWatchTicksRemaining--
            runArmorStandDebug(player)
        }

        val currentMatchingStandIds = level
            .getEntitiesOfClass(ArmorStand::class.java, searchAABB(player)) { stand ->
                isValidBackboneStand(player, stand)
            }
            .map { it.uuid }
            .toSet()

        val sawNewMatchingStand = currentMatchingStandIds.any { it !in previousMatchingStandIds }

        if (
            sequenceTicksRemaining <= 0 &&
            triggerCooldownTicks <= 0 &&
            isHoldingBonemerang(player.mainHandItem) &&
            sawNewMatchingStand
        ) {
            sequenceTicksRemaining = LOCK_TICKS
            triggerCooldownTicks = 10
            lockedSlot = -1

            displayClientMessage("Backbone timer started!")

            if (autoSwapSetting.value) {
                val targetSlot = findTargetSlot(player.inventory)
                if (targetSlot != -1) {
                    swapToSlot(player.inventory, targetSlot)

                    val swappedStackName = player.inventory.getItem(targetSlot).hoverName.string
                    displayClientMessage("Swapped to $swappedStackName")

                    if (isHoldingSelectedRendWeapon(player.inventory)) {
                        lockedSlot = player.inventory.selectedSlot
                        displayClientMessage("Locked Slots for ${sequenceTicksRemaining}t.")
                    }
                } else {
                    displayClientMessage("No rend weapon found? what are you? a duplexer? turn this off then.")
                }
            }
        }

        if (sequenceTicksRemaining > 0) {
            sequenceTicksRemaining--

            if (lockedSlot == -1 && isHoldingSelectedRendWeapon(player.inventory)) {
                lockedSlot = player.inventory.selectedSlot
                displayClientMessage("Locked Slots for ${sequenceTicksRemaining}t.")
            }

            if (lockedSlot != -1) {
                enforceLockedSlot(player.inventory)
            }

            if (sequenceTicksRemaining <= 0) {
                lockedSlot = -1
                displayClientMessage("Slots Unlocked!")
            }
        }

        previousMatchingStandIds = currentMatchingStandIds
    }

    private fun handleDebugRightClickTrigger(client: Minecraft, player: LocalPlayer) {
        val usePressed = client.options.keyUse.isDown

        if (debugArmorStandSetting.value && usePressed && !wasUsePressed && isHoldingBonemerang(player.mainHandItem)) {
            debugWatchTicksRemaining = DEBUG_SCAN_TICKS
            previousDebugStandIds = collectNearbyArmorStandIds(player)
            displayClientMessage("Started armor stand debug watch for $DEBUG_SCAN_TICKS ticks")
        }

        wasUsePressed = usePressed
    }

    private fun runArmorStandDebug(player: LocalPlayer) {
        val level = client().level ?: return
        val currentStands = level.getEntitiesOfClass(ArmorStand::class.java, searchAABB(player)) { true }

        for (stand in currentStands) {
            if (stand.uuid in previousDebugStandIds) continue

            val playerEyePos = player.eyePosition
            val standCenter = Vec3(
                stand.x,
                stand.y + (stand.bbHeight * 0.5),
                stand.z
            )

            val toStand = standCenter.subtract(playerEyePos)
            val distance = sqrt(toStand.lengthSqr())
            val facing = player.getViewVector(1.0f).normalize()
            val direction = if (toStand.lengthSqr() > 0.0) toStand.normalize() else Vec3.ZERO
            val dot = facing.dot(direction)

            val customName = stand.customName?.string ?: "null"
            val mainName = stackDebugName(stand.mainHandItem)
            val offName = stackDebugName(stand.offhandItem)

            displayClientMessage(
                "New stand: " +
                        "invis=${stand.isInvisible}, " +
                        "marker=${stand.isMarker}, " +
                        "small=${stand.isSmall}, " +
                        "name=$customName, " +
                        "main=$mainName, " +
                        "off=$offName, " +
                        "dist=${"%.2f".format(distance)}, " +
                        "dot=${"%.2f".format(dot)}, " +
                        "UUID=${stand.uuid}"
            )
        }

        previousDebugStandIds = currentStands.map { it.uuid }.toSet()
    }

    private fun collectNearbyArmorStandIds(player: LocalPlayer): Set<UUID> {
        val level = client().level ?: return emptySet()
        return level.getEntitiesOfClass(ArmorStand::class.java, searchAABB(player)) { true }
            .map { it.uuid }
            .toSet()
    }

    private fun resetState() {
        previousMatchingStandIds = emptySet()
        sequenceTicksRemaining = 0
        lockedSlot = -1
        triggerCooldownTicks = 0
        debugWatchTicksRemaining = 0
        previousDebugStandIds = emptySet()
        wasUsePressed = false
    }

    private fun searchAABB(player: LocalPlayer): AABB {
        return player.boundingBox.inflate(BONE_STAND_RANGE, 2.0, BONE_STAND_RANGE)
    }

    private fun isValidBackboneStand(player: LocalPlayer, stand: ArmorStand): Boolean {
        if (!stand.isAlive) return false
        if (!stand.isInvisible) return false
        if (!stand.isMarker) return false

        val mainBone = isBoneLikeStack(stand.mainHandItem)
        val offBone = isBoneLikeStack(stand.offhandItem)
        if (!mainBone && !offBone) return false

        val playerEyePos = player.eyePosition
        val standCenter = Vec3(
            stand.x,
            stand.y + (stand.bbHeight * 0.5),
            stand.z
        )

        val toStand = standCenter.subtract(playerEyePos)
        val distance = sqrt(toStand.lengthSqr())

        return distance in 0.05..BONE_STAND_RANGE
    }

    private fun isBoneLikeStack(stack: ItemStack): Boolean {
        if (stack.isEmpty) return false

        val displayName = stack.hoverName.string
        val descriptionId = stack.item.descriptionId

        return displayName.contains("bone", ignoreCase = true) ||
                displayName.contains("bonemerang", ignoreCase = true) ||
                descriptionId.contains("bone", ignoreCase = true)
    }

    private fun stackDebugName(stack: ItemStack): String {
        if (stack.isEmpty) return "empty"
        return "${stack.hoverName.string} | ${stack.item.descriptionId}"
    }

    private fun findTargetSlot(inventory: Inventory): Int {
        val needle = targetNameNeedle()
        for (slot in 0 until 9) {
            val stack = inventory.getItem(slot)
            if (matchesName(stack, needle)) {
                return slot
            }
        }
        return -1
    }

    private fun isHoldingSelectedRendWeapon(inventory: Inventory): Boolean {
        return matchesName(inventory.getItem(inventory.selectedSlot), targetNameNeedle())
    }

    private fun targetNameNeedle(): String {
        return when (rendWeaponSetting.selected) {
            "Ice Spray" -> "spray"
            "Livid Dagger" -> "livid"
            else -> "atomsplit"
        }
    }

    private fun isHoldingBonemerang(stack: ItemStack): Boolean {
        return matchesName(stack, "bonemerang")
    }

    private fun matchesName(stack: ItemStack, needle: String): Boolean {
        if (stack.isEmpty) return false
        return stack.hoverName.string.contains(needle, ignoreCase = true)
    }

    private fun swapToSlot(inventory: Inventory, slot: Int) {
        if (slot !in 0..8) return
        inventory.selectedSlot = slot
    }

    private fun enforceLockedSlot(inventory: Inventory) {
        if (lockedSlot !in 0..8) return
        if (inventory.selectedSlot != lockedSlot) {
            inventory.selectedSlot = lockedSlot
        }
    }

    private fun displayClientMessage(message: String) {
        val player = client().player ?: return
        player.sendSystemMessage(LarpBranding.prefixed(message))
    }
}
