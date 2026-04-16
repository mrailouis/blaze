package me.mrai.larpclient.features.impl.kuudra.p1.tentdangerhorse

import com.mojang.blaze3d.platform.InputConstants
import me.mrai.larpclient.module.KeybindSetting
import me.mrai.larpclient.module.Module
import me.mrai.larpclient.module.ModuleCategory
import me.mrai.larpclient.module.SliderSetting
import me.mrai.larpclient.util.TextSanitizer
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.MultiPlayerGameMode
import net.minecraft.client.player.LocalPlayer
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Items
import org.lwjgl.glfw.GLFW

object TentDangerHorseModule : Module(
    name = "Tent Danger Horse",
    description = "Toggles Skeleton Horse pearl spam for Kuudra tent danger.",
    category = ModuleCategory.KUUDRA_P1
) {
    private const val PETS_COMMAND = "pets"
    private const val REFILL_COMMAND_PREFIX = "gfs ender pearl"
    private const val TARGET_PEARLS = 16
    private const val OPEN_TIMEOUT_TICKS = 40
    private const val THROW_INTERVAL_MS = 143L
    private const val REFILL_INTERVAL_MS = 450L
    private const val HORSE_SEARCH_RANGE = 6.0
    private const val DOWN_PITCH = 90f

    private val toggleKey = KeybindSetting("Toggle Key", GLFW.GLFW_KEY_UNKNOWN)
    private val cps = SliderSetting("CPS", 7.0, 1.0, 20.0, 1.0)

    private val pressedKeys = hashSetOf<Int>()

    @JvmField
    var suppressPetsScreen = false

    private var active = false
    private var pendingOpen = false
    private var awaitingContents = false
    private var pendingContainerId = -1
    private var clickDelayTicks = 0
    private var timeoutTicks = 0
    private var clickedForContainerId = -1
    private var lastThrowMs = 0L
    private var lastRefillMs = 0L
    private var restoreSlot = -1
    private var restoreYaw = 0f
    private var restorePitch = 0f

    init {
        settings += listOf(toggleKey, cps)
    }

    override fun onEnable() {
        resetRuntimeState()
    }

    override fun onDisable() {
        stopAutomation(restoreView = true, closeContainer = true)
        pressedKeys.clear()
    }

    fun onWorldChange() {
        stopAutomation(restoreView = false, closeContainer = false)
        pressedKeys.clear()
    }

    override fun onTick() {
        val client = Minecraft.getInstance()
        val player = client.player
        val connection = client.connection
        val gameMode = client.gameMode
        val window = client.window

        if (timeoutTicks > 0) timeoutTicks--
        if (clickDelayTicks > 0) clickDelayTicks--

        handleToggleKey(window)

        if (player == null || connection == null) {
            stopAutomation(restoreView = false, closeContainer = false)
            return
        }

        if (timeoutTicks == 0 && isGuiBusy()) {
            stopAutomation(restoreView = true, closeContainer = true)
            return
        }

        if (
            active &&
            !awaitingContents &&
            clickDelayTicks == 0 &&
            pendingContainerId != -1 &&
            player.containerMenu.containerId == pendingContainerId
        ) {
            tryClickHorse(gameMode, player)
        }

        if (!active || client.screen != null) return

        alignPitchDown(player, connection)
        refillPearlsIfNeeded(player)

        if (!hasNearbySkeletonHorse(player)) return

        val now = System.currentTimeMillis()
        val interval = (1000.0 / cps.value).toLong().coerceAtLeast(1L)
        if (now - lastThrowMs < interval) return

        if (throwPearlDown(player)) {
            lastThrowMs = now
        }
    }

    fun onOpenScreenCaptured(containerId: Int, title: String) {
        if (!pendingOpen) return
        if (!isPetsTitle(title)) return

        pendingOpen = false
        awaitingContents = true
        pendingContainerId = containerId
        clickDelayTicks = 1
        clickedForContainerId = -1
    }

    fun shouldSuppressScreen(title: String): Boolean {
        return enabled && suppressPetsScreen && isPetsTitle(title)
    }

    private fun handleToggleKey(window: com.mojang.blaze3d.platform.Window) {
        val key = toggleKey.key
        if (key == GLFW.GLFW_KEY_UNKNOWN) return

        val pressed = InputConstants.isKeyDown(window, key)
        val wasPressed = pressedKeys.contains(key)
        if (pressed) {
            pressedKeys += key
        } else {
            pressedKeys -= key
        }

        if (!pressed || wasPressed) return

        if (active || isGuiBusy()) {
            stopAutomation(restoreView = true, closeContainer = true)
        } else {
            startAutomation()
        }
    }

    private fun startAutomation() {
        val client = Minecraft.getInstance()
        val player = client.player ?: return
        val connection = client.connection ?: return

        resetRuntimeState()
        active = true
        pendingOpen = true
        suppressPetsScreen = true
        timeoutTicks = OPEN_TIMEOUT_TICKS
        restoreSlot = player.inventory.selectedSlot
        restoreYaw = player.yRot
        restorePitch = player.xRot
        lastThrowMs = 0L
        lastRefillMs = 0L

        connection.sendCommand(PETS_COMMAND)
    }

    private fun stopAutomation(restoreView: Boolean, closeContainer: Boolean) {
        val client = Minecraft.getInstance()
        val player = client.player
        val connection = client.connection

        if (closeContainer) {
            player?.closeContainer()
        }

        if (restoreView && player != null && connection != null) {
            if (restoreSlot in 0..8 && player.inventory.selectedSlot != restoreSlot) {
                player.inventory.selectedSlot = restoreSlot
                connection.send(ServerboundSetCarriedItemPacket(restoreSlot))
            }

            player.yRot = restoreYaw
            player.xRot = restorePitch
            connection.send(ServerboundMovePlayerPacket.Rot(restoreYaw, restorePitch, player.onGround(), player.horizontalCollision))
        }

        resetRuntimeState()
    }

    private fun resetRuntimeState() {
        active = false
        pendingOpen = false
        awaitingContents = false
        suppressPetsScreen = false
        pendingContainerId = -1
        clickDelayTicks = 0
        timeoutTicks = 0
        clickedForContainerId = -1
        lastThrowMs = 0L
        lastRefillMs = 0L
        restoreSlot = -1
    }

    private fun isGuiBusy(): Boolean {
        return pendingOpen || awaitingContents || suppressPetsScreen || pendingContainerId != -1
    }

    private fun tryClickHorse(gameMode: MultiPlayerGameMode?, player: LocalPlayer) {
        if (gameMode == null) {
            stopAutomation(restoreView = true, closeContainer = true)
            return
        }

        if (clickedForContainerId == pendingContainerId) return
        clickedForContainerId = pendingContainerId

        val menu = player.containerMenu
        val targetSlot = menu.slots.indexOfFirst { slot ->
            val stack = slot.item
            !stack.isEmpty && TextSanitizer.normalizedLower(stack.displayName.string).contains("skeleton horse")
        }

        if (targetSlot == -1) {
            stopAutomation(restoreView = true, closeContainer = true)
            return
        }

        val success = clickSlot(gameMode, pendingContainerId, targetSlot, player)

        player.closeContainer()
        pendingContainerId = -1
        awaitingContents = false
        suppressPetsScreen = false
        timeoutTicks = 0

        if (!success) {
            stopAutomation(restoreView = true, closeContainer = false)
        }
    }

    private fun refillPearlsIfNeeded(player: LocalPlayer) {
        val connection = player.connection
        val now = System.currentTimeMillis()
        if (now - lastRefillMs < REFILL_INTERVAL_MS) return

        val current = countHotbarPearls(player)
        if (current >= TARGET_PEARLS) return

        val missing = TARGET_PEARLS - current
        connection.sendCommand("$REFILL_COMMAND_PREFIX $missing")
        lastRefillMs = now
    }

    private fun throwPearlDown(player: LocalPlayer): Boolean {
        val client = Minecraft.getInstance()
        val connection = client.connection ?: return false
        val gameMode = client.gameMode ?: return false
        val pearlSlot = findHotbarPearlSlot(player) ?: return false

        if (player.inventory.selectedSlot != pearlSlot) {
            player.inventory.selectedSlot = pearlSlot
            connection.send(ServerboundSetCarriedItemPacket(pearlSlot))
        }

        val yaw = player.yRot
        player.xRot = DOWN_PITCH
        connection.send(ServerboundMovePlayerPacket.Rot(yaw, DOWN_PITCH, player.onGround(), player.horizontalCollision))
        val result = gameMode.useItem(player, InteractionHand.MAIN_HAND)
        player.swing(InteractionHand.MAIN_HAND)
        return result.consumesAction()
    }

    private fun alignPitchDown(player: LocalPlayer, connection: net.minecraft.client.multiplayer.ClientPacketListener) {
        if (player.xRot == DOWN_PITCH) return
        player.xRot = DOWN_PITCH
        connection.send(ServerboundMovePlayerPacket.Rot(player.yRot, DOWN_PITCH, player.onGround(), player.horizontalCollision))
    }

    private fun hasNearbySkeletonHorse(player: LocalPlayer): Boolean {
        val level = Minecraft.getInstance().level ?: return false
        return level.entitiesForRendering().any { entity ->
            entity.isAlive &&
                    entity.distanceTo(player) <= HORSE_SEARCH_RANGE.toFloat() &&
                    TextSanitizer.normalizedLower(entity.displayName.string).contains("skeleton horse")
        }
    }

    private fun clickSlot(
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
            } ?: return false

            val clickTypeClass = clickMethod.parameterTypes[3]
            val clickType = clickTypeClass.enumConstants.firstOrNull {
                it.toString().equals("PICKUP", ignoreCase = true)
            } ?: clickTypeClass.enumConstants.firstOrNull() ?: return false

            clickMethod.isAccessible = true
            clickMethod.invoke(gameMode, syncId, slot, 0, clickType, player)
            true
        } catch (_: Throwable) {
            false
        }
    }

    private fun countHotbarPearls(player: LocalPlayer): Int {
        var total = 0
        for (slot in 0..8) {
            val stack = player.inventory.getItem(slot)
            if (stack.`is`(Items.ENDER_PEARL)) {
                total += stack.count
            }
        }
        return total
    }

    private fun findHotbarPearlSlot(player: LocalPlayer): Int? {
        for (slot in 0..8) {
            if (player.inventory.getItem(slot).`is`(Items.ENDER_PEARL)) {
                return slot
            }
        }
        return null
    }

    private fun isPetsTitle(title: String): Boolean {
        return TextSanitizer.normalizedLower(title).contains("pets")
    }
}
