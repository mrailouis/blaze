package me.mrai.larpclient.features.impl.skyblock.general.cgywardrobe

import com.mojang.blaze3d.platform.InputConstants
import me.mrai.larpclient.module.KeybindSetting
import me.mrai.larpclient.module.Module
import me.mrai.larpclient.module.ModuleCategory
import me.mrai.larpclient.util.LarpLog
import me.mrai.larpclient.util.TextSanitizer
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.MultiPlayerGameMode
import net.minecraft.client.player.LocalPlayer
import net.minecraft.world.entity.player.Player
import org.lwjgl.glfw.GLFW

object CgyWardrobeModule : Module(
    name = "CGY Wardrobe",
    description = "Swaps wardrobe slots silently without opening the GUI.",
    category = ModuleCategory.SKYBLOCK_GENERAL
) {
    private const val COMMAND = "wd"
    private const val COOLDOWN_TICKS = 2
    private const val FIRST_WARDROBE_SLOT = 36

    private val slotKeybinds = List(9) { index ->
        KeybindSetting("Slot ${index + 1}", GLFW.GLFW_KEY_UNKNOWN)
    }

    private val pressedKeys = hashSetOf<Int>()

    private var cooldownTicks = 0
    private var timeoutTicks = 0
    private var clickDelayTicks = 0
    private var awaitingContents = false
    private var clickAttemptedForSyncId = -1

    @JvmField
    var awaitingWardrobe = false

    @JvmField
    var suppressWardrobeScreen = false

    @JvmField
    var pendingSyncId = -1

    @JvmField
    var pendingWardrobeSlot = -1

    init {
        settings += slotKeybinds
    }

    override fun onEnable() {
        cooldownTicks = 0
        timeoutTicks = 0
        clickDelayTicks = 0
        awaitingContents = false
        clickAttemptedForSyncId = -1
        pressedKeys.clear()
        resetState()
    }

    override fun onDisable() {
        cooldownTicks = 0
        timeoutTicks = 0
        clickDelayTicks = 0
        awaitingContents = false
        clickAttemptedForSyncId = -1
        pressedKeys.clear()
        resetState()
    }

    override fun onTick() {
        val client = Minecraft.getInstance()
        val player = client.player
        val gameMode = client.gameMode

        if (cooldownTicks > 0) cooldownTicks--
        if (timeoutTicks > 0) timeoutTicks--
        if (clickDelayTicks > 0) clickDelayTicks--

        if (player == null || gameMode == null || client.connection == null) {
            pressedKeys.clear()
            resetState()
            return
        }

        if (timeoutTicks == 0 && isBusy()) {
            player.closeContainer()
            resetState()
            return
        }

        if (!isBusy() && client.screen == null) {
            val window = client.window

            for ((index, setting) in slotKeybinds.withIndex()) {
                val key = setting.key
                if (key == GLFW.GLFW_KEY_UNKNOWN) continue

                val isPressed = InputConstants.isKeyDown(window, key)
                val wasPressed = pressedKeys.contains(key)

                if (isPressed && !wasPressed) {
                    startWardrobe(client, index + 1)
                }

                if (isPressed) {
                    pressedKeys += key
                } else {
                    pressedKeys -= key
                }
            }
        }

        if (
            !awaitingContents &&
            clickDelayTicks == 0 &&
            pendingSyncId != -1 &&
            pendingWardrobeSlot in 1..9
        ) {
            val menu = player.containerMenu
            if (menu.containerId == pendingSyncId) {
                val targetSlot = wardrobeContainerSlot(pendingWardrobeSlot)
                val slotCount = menu.slots.size

                if (targetSlot !in 0 until slotCount) {
                    LarpLog.warn("Wardrobe target slot $targetSlot is out of bounds for syncId=$pendingSyncId.")
                    resetState()
                    return
                }

                if (clickAttemptedForSyncId == pendingSyncId) {
                    return
                }
                clickAttemptedForSyncId = pendingSyncId

                if (clickWardrobeSlot(gameMode, pendingSyncId, targetSlot, player)) {
                    player.closeContainer()
                    resetState()
                }
            }
        }
    }

    private fun startWardrobe(client: Minecraft, slot: Int) {
        if (cooldownTicks > 0) return
        if (isBusy()) return
        client.player ?: return

        cooldownTicks = COOLDOWN_TICKS
        timeoutTicks = 40
        clickDelayTicks = 0
        awaitingContents = false
        clickAttemptedForSyncId = -1

        pendingWardrobeSlot = slot
        pendingSyncId = -1
        awaitingWardrobe = true
        suppressWardrobeScreen = true

        client.connection?.sendCommand(COMMAND)
    }

    fun onOpenScreenCaptured(syncId: Int, title: String) {
        if (!awaitingWardrobe) return
        if (!isWardrobeTitle(title)) return

        pendingSyncId = syncId
        awaitingWardrobe = false
        awaitingContents = true
        clickAttemptedForSyncId = -1
        LarpLog.debug("Captured wardrobe screen open (syncId=$syncId, slot=$pendingWardrobeSlot).")
    }

    fun onWardrobeContents(syncId: Int, slotCount: Int) {
        if (!awaitingContents) return
        if (syncId != pendingSyncId) return

        awaitingContents = false
        clickDelayTicks = 1
        LarpLog.debug(
            "Captured wardrobe contents (syncId=$syncId, slots=$slotCount, target=${wardrobeContainerSlot(pendingWardrobeSlot)})."
        )
    }

    fun isWardrobeTitle(title: String): Boolean {
        return TextSanitizer.stripFormatting(title).contains("Wardrobe", ignoreCase = true)
    }

    private fun wardrobeContainerSlot(slot: Int): Int = FIRST_WARDROBE_SLOT + (slot - 1)

    private fun isBusy(): Boolean {
        return awaitingWardrobe ||
                awaitingContents ||
                suppressWardrobeScreen ||
                pendingSyncId != -1 ||
                pendingWardrobeSlot != -1
    }

    private fun resetState() {
        awaitingWardrobe = false
        awaitingContents = false
        suppressWardrobeScreen = false
        pendingSyncId = -1
        pendingWardrobeSlot = -1
        timeoutTicks = 0
        clickDelayTicks = 0
        clickAttemptedForSyncId = -1
    }

    private fun clickWardrobeSlot(
        gameMode: MultiPlayerGameMode,
        syncId: Int,
        slot: Int,
        player: LocalPlayer
    ): Boolean {
        return try {
            val methods = gameMode.javaClass.declaredMethods.toList()

            val clickMethod = methods.firstOrNull { method ->
                method.parameterCount == 5 &&
                        method.parameterTypes[0] == Int::class.javaPrimitiveType &&
                        method.parameterTypes[1] == Int::class.javaPrimitiveType &&
                        method.parameterTypes[2] == Int::class.javaPrimitiveType &&
                        method.parameterTypes[3].isEnum &&
                        Player::class.java.isAssignableFrom(method.parameterTypes[4])
            }

            if (clickMethod == null) {
                LarpLog.warn("No wardrobe click method found on ${gameMode.javaClass.name}.")
                return false
            }

            val clickTypeClass = clickMethod.parameterTypes[3]
            val clickType = clickTypeClass.enumConstants.firstOrNull {
                it.toString().equals("PICKUP", ignoreCase = true)
            } ?: clickTypeClass.enumConstants.firstOrNull()

            if (clickType == null) {
                LarpLog.warn("Could not resolve wardrobe click enum for ${clickTypeClass.name}.")
                return false
            }

            clickMethod.isAccessible = true

            clickMethod.invoke(gameMode, syncId, slot, 0, clickType, player)
            LarpLog.debug("Wardrobe click invoked successfully (syncId=$syncId, slot=$slot, method=${clickMethod.name}).")
            true
        } catch (t: Throwable) {
            LarpLog.error("Wardrobe click invocation failed for syncId=$syncId slot=$slot.", t)
            false
        }
    }
}
