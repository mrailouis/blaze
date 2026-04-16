package me.mrai.larpclient.features.impl.skyblock.general.slotlocking

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.mojang.blaze3d.platform.InputConstants
import me.mrai.larpclient.util.LarpBranding
import me.mrai.larpclient.util.LarpLog
import me.mrai.larpclient.mixin.AbstractContainerScreenAccessor
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.multiplayer.MultiPlayerGameMode
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.sounds.SoundEvents
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.inventory.ContainerInput
import net.minecraft.world.inventory.Slot
import java.nio.file.Files
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

object SlotLockingState {
    private const val MAX_INDEX = 40
    private const val RESERVED_SKYBLOCK_SLOT = 8

    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val lockIcon = Identifier.fromNamespaceAndPath("larpclient", "slotlocking/lock.png")
    private val boundIcon = Identifier.fromNamespaceAndPath("larpclient", "slotlocking/bound.png")
    private val file = FabricLoader.getInstance().configDir.resolve("larpclient").resolve("slot_locking.json")

    private data class LockedSlot(
        var locked: Boolean = false,
        var boundTo: Int = -1
    )

    private val states = linkedMapOf<Int, LockedSlot>()
    private var pairingIndex: Int? = null
    private var pendingToggleIndex: Int? = null
    private var loaded = false

    fun ensureLoaded() {
        if (loaded) return
        loaded = true
        load()
    }

    fun onScreenClosed() {
        pairingIndex = null
        pendingToggleIndex = null
    }

    fun onKeyPressed(hoveredSlot: Slot?, key: Int): Boolean {
        ensureLoaded()
        if (!SlotLockingModule.enabled) return false
        if (key != SlotLockingModule.lockKey.key) return false

        val mc = Minecraft.getInstance()
        val player = mc.player ?: return false
        val index = playerInventoryIndex(hoveredSlot, player.inventory) ?: return false
        if (!isLockable(index)) return false

        if (pendingToggleIndex != null) return true

        pendingToggleIndex = index
        pairingIndex = if (SlotLockingModule.enableBinding.value && canBindFrom(index)) index else null
        return true
    }

    fun onMouseClicked(screen: AbstractContainerScreen<*>, mouseX: Double, mouseY: Double): Boolean {
        ensureLoaded()
        if (!SlotLockingModule.enabled || !SlotLockingModule.enableBinding.value) return false
        if (!isLockKeyDown()) {
            pairingIndex = null
            return false
        }
        return false
    }

    fun onMouseReleased(screen: AbstractContainerScreen<*>, mouseX: Double, mouseY: Double): Boolean {
        return false
    }

    fun interceptSlotClick(
        screen: AbstractContainerScreen<*>,
        slot: Slot?,
        button: Int,
        input: ContainerInput
    ): Boolean {
        ensureLoaded()
        if (!SlotLockingModule.enabled) return false

        if (input == ContainerInput.SWAP && button in 0..8 && isProtected(button)) {
            return true
        }

        val mc = Minecraft.getInstance()
        val player = mc.player ?: return false
        val index = playerInventoryIndex(slot, player.inventory) ?: return false
        val state = state(index) ?: return false

        if (state.locked) {
            return true
        }

        if (SlotLockingModule.enableBinding.value && input == ContainerInput.QUICK_MOVE && state.boundTo in 0..8) {
            return rerouteBoundQuickMove(screen, slot ?: return false, index, state.boundTo)
        }

        return SlotLockingModule.bindingAlsoLocks.value && state.boundTo != -1
    }

    fun shouldCancelDropSelected(selectedSlot: Int): Boolean {
        ensureLoaded()
        if (!SlotLockingModule.enabled) return false
        return isProtected(selectedSlot)
    }

    fun sendDropBlockedMessage() {
        val player = Minecraft.getInstance().player ?: return
        player.sendSystemMessage(
            LarpBranding.prefixed(
                Component.literal("Prevented dropping that locked item.").withColor(LarpBranding.RED)
            )
        )
    }

    fun render(screen: AbstractContainerScreen<*>, graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {
        ensureLoaded()
        if (!SlotLockingModule.enabled) return

        val mc = Minecraft.getInstance()
        val player = mc.player ?: return
        val accessor = screen as AbstractContainerScreenAccessor
        val hovered = accessor.getLarpclientHoveredSlot()
        val hoveredIndex = playerInventoryIndex(hovered, player.inventory)
        val leftPos = accessor.getLarpclientLeftPos()
        val topPos = accessor.getLarpclientTopPos()

        if (!isLockKeyDown()) {
            pendingToggleIndex?.let { pending ->
                when {
                    hoveredIndex == pending -> {
                        if (hasAnyBinding(pending)) {
                            clearAllBindingsFor(pending)
                            save()
                        } else {
                            toggleLock(pending)
                        }
                    }
                    pairingIndex != null && hoveredIndex != null && canBindPair(pairingIndex!!, hoveredIndex) && !isStrictlyLocked(hoveredIndex) -> {
                        bind(pairingIndex!!, hoveredIndex)
                    }
                }
            }
            pairingIndex = null
            pendingToggleIndex = null
        }

        for (slot in screen.menu.slots) {
            val index = playerInventoryIndex(slot, player.inventory) ?: continue
            val slotState = state(index)
            val incomingBindings = inboundBindings(index)
            if (slotState == null && incomingBindings.isEmpty()) continue
            val x = leftPos + slot.x
            val y = topPos + slot.y

            if (slotState?.locked == true) {
                drawIcon(graphics, lockIcon, x, y)
                continue
            }

            if (SlotLockingModule.enableBinding.value && (slotState?.boundTo in 0..MAX_INDEX || incomingBindings.isNotEmpty())) {
                drawIcon(graphics, boundIcon, x, y)

                if (hoveredIndex == index) {
                    val directBoundTo = slotState?.boundTo
                    if (directBoundTo != null && directBoundTo in 0..MAX_INDEX) {
                        val boundSlot = menuSlotForInventoryIndex(screen, directBoundTo, player.inventory)
                        if (boundSlot != null) {
                            drawSlotLink(graphics, leftPos, topPos, slot, boundSlot)
                        } else {
                            clearOutgoingBinding(index)
                        }
                    }
                    for (sourceIndex in incomingBindings) {
                        val sourceSlot = menuSlotForInventoryIndex(screen, sourceIndex, player.inventory) ?: continue
                        drawSlotLink(graphics, leftPos, topPos, slot, sourceSlot)
                    }
                }
            }
        }

        val source = pairingIndex
        if (
            SlotLockingModule.enableBinding.value &&
            source != null &&
            isLockKeyDown() &&
            isBindableIndex(source)
        ) {
            menuSlotForInventoryIndex(screen, source, player.inventory)?.let { slot ->
                drawLine(
                    graphics,
                    leftPos + slot.x + 8f,
                    topPos + slot.y + 8f,
                    mouseX.toFloat(),
                    mouseY.toFloat(),
                    0xFF33EEDD.toInt()
                )
            }

            for (target in 0..39) {
                if (!canBindPair(source, target)) continue
                val candidate = menuSlotForInventoryIndex(screen, target, player.inventory) ?: continue
                val x = leftPos + candidate.x
                val y = topPos + candidate.y
                graphics.fill(x, y, x + 16, y + 16, 0x40FFFFFF)
            }
        }
    }

    private fun rerouteBoundQuickMove(
        screen: AbstractContainerScreen<*>,
        slot: Slot,
        index: Int,
        hotbarIndex: Int
    ): Boolean {
        val mc = Minecraft.getInstance()
        val gameMode = mc.gameMode ?: return false
        val player = mc.player ?: return false

        if (index in 0..8) {
            val boundIndex = state(index)?.boundTo ?: return false
            if (boundIndex !in 9..39) {
                clearOutgoingBinding(index)
                save()
                return false
            }

            val sourceSlot = menuSlotForInventoryIndex(screen, boundIndex, player.inventory) ?: run {
                clearOutgoingBinding(index)
                save()
                return false
            }

            gameMode.handleContainerInput(screen.menu.containerId, sourceSlot.index, index, ContainerInput.SWAP, player)
            return true
        }

        gameMode.handleContainerInput(screen.menu.containerId, slot.index, hotbarIndex, ContainerInput.SWAP, player)
        return true
    }

    private fun toggleLock(index: Int) {
        val slot = mutableState(index)
        slot.locked = !slot.locked
        slot.boundTo = -1

        if (index in 0..8 && slot.locked) {
            for ((otherIndex, otherState) in states.toList()) {
                if (otherIndex != index && otherState.boundTo == index) {
                    otherState.boundTo = -1
                    cleanupIfEmpty(otherIndex)
                }
            }
        }

        cleanupIfEmpty(index)
        save()
        playToggleSound(slot.locked)
    }

    private fun bind(sourceIndex: Int, targetIndex: Int) {
        if (!canBindPair(sourceIndex, targetIndex) || isStrictlyLocked(targetIndex)) return

        clearOutgoingBinding(sourceIndex)

        val source = mutableState(sourceIndex)
        source.locked = false
        source.boundTo = targetIndex

        cleanupIfEmpty(sourceIndex)
        save()
    }

    private fun clearAllBindingsFor(index: Int) {
        clearOutgoingBinding(index)
        for ((otherIndex, otherState) in states.toList()) {
            if (otherIndex != index && otherState.boundTo == index) {
                otherState.boundTo = -1
                cleanupIfEmpty(otherIndex)
            }
        }
    }

    private fun clearOutgoingBinding(index: Int) {
        val current = states[index] ?: return
        current.boundTo = -1
        cleanupIfEmpty(index)
    }

    private fun load() {
        states.clear()
        try {
            if (!Files.exists(file)) return
            val root = Files.newBufferedReader(file).use { gson.fromJson(it, JsonObject::class.java) } ?: return
            val slots = root.getAsJsonObject("slots") ?: return

            for ((key, value) in slots.entrySet()) {
                val index = key.toIntOrNull() ?: continue
                if (index !in 0..MAX_INDEX) continue
                val obj = value.asJsonObject
                states[index] = LockedSlot(
                    locked = obj.get("locked")?.asBoolean ?: false,
                    boundTo = obj.get("boundTo")?.asInt ?: -1
                )
            }
        } catch (throwable: Throwable) {
            LarpLog.error("Failed to load slot locking config from $file.", throwable)
        }
    }

    private fun save() {
        try {
            if (!Files.exists(file.parent)) Files.createDirectories(file.parent)
            val root = JsonObject()
            val slots = JsonObject()
            for ((index, state) in states) {
                if (!state.locked && state.boundTo == -1) continue
                val obj = JsonObject()
                obj.addProperty("locked", state.locked)
                obj.addProperty("boundTo", state.boundTo)
                slots.add(index.toString(), obj)
            }
            root.add("slots", slots)
            Files.newBufferedWriter(file).use { gson.toJson(root, it) }
        } catch (throwable: Throwable) {
            LarpLog.error("Failed to save slot locking config to $file.", throwable)
        }
    }

    private fun playToggleSound(locked: Boolean) {
        if (!SlotLockingModule.lockSound.value) return
        val mc = Minecraft.getInstance()
        val volume = (SlotLockingModule.soundVolume.value / 100.0).toFloat().coerceIn(0f, 1f)
        if (volume <= 0f) return
        mc.soundManager.play(
            SimpleSoundInstance.forUI(
                SoundEvents.EXPERIENCE_ORB_PICKUP,
                volume,
                if (locked) 0.943f else 0.1f
            )
        )
    }

    private fun playerInventoryIndex(slot: Slot?, inventory: Inventory): Int? {
        if (slot == null || slot.container !== inventory) return null
        return slot.getContainerSlot().takeIf { it in 0..MAX_INDEX }
    }

    private fun menuSlotForInventoryIndex(
        screen: AbstractContainerScreen<*>,
        inventoryIndex: Int,
        inventory: Inventory
    ): Slot? {
        val optional = screen.menu.findSlot(inventory, inventoryIndex)
        return if (optional.isPresent) screen.menu.getSlot(optional.asInt) else null
    }

    private fun mutableState(index: Int): LockedSlot {
        return states.getOrPut(index) { LockedSlot() }
    }

    private fun state(index: Int): LockedSlot? {
        return states[index]
    }

    private fun cleanupIfEmpty(index: Int) {
        val slot = states[index] ?: return
        if (!slot.locked && slot.boundTo == -1) {
            states.remove(index)
        }
    }

    private fun isLockKeyDown(): Boolean {
        val key = SlotLockingModule.lockKey.key
        if (key == InputConstants.UNKNOWN.value) return false
        val window = Minecraft.getInstance().window
        return InputConstants.isKeyDown(window, key)
    }

    private fun isLockable(index: Int): Boolean = index in 0..MAX_INDEX && index != RESERVED_SKYBLOCK_SLOT

    private fun isBindableIndex(index: Int): Boolean = index in 0..39 && isLockable(index)

    private fun canBindFrom(index: Int): Boolean = isBindableIndex(index)

    private fun canBindPair(sourceIndex: Int, targetIndex: Int): Boolean {
        return sourceIndex != targetIndex && isBindableIndex(sourceIndex) && isBindableIndex(targetIndex)
    }

    private fun isStrictlyLocked(index: Int): Boolean = state(index)?.locked == true

    private fun inboundBindings(targetIndex: Int): List<Int> {
        return states.entries
            .asSequence()
            .filter { (index, state) -> index != targetIndex && state.boundTo == targetIndex }
            .map { it.key }
            .toList()
    }

    private fun hasAnyBinding(index: Int): Boolean {
        return state(index)?.boundTo != -1 || states.values.any { it.boundTo == index }
    }

    private fun isProtected(index: Int): Boolean {
        val slot = state(index)
        return slot?.locked == true || (SlotLockingModule.bindingAlsoLocks.value && hasAnyBinding(index))
    }

    private fun drawIcon(graphics: GuiGraphicsExtractor, texture: Identifier, x: Int, y: Int) {
        graphics.blit(
            RenderPipelines.GUI_TEXTURED,
            texture,
            x,
            y,
            0f,
            0f,
            16,
            16,
            16,
            16
        )
    }

    private fun drawSlotLink(graphics: GuiGraphicsExtractor, leftPos: Int, topPos: Int, from: Slot, to: Slot) {
        val startX = leftPos + from.x + 8f
        val startY = topPos + from.y + 8f
        val endX = leftPos + to.x + 8f
        val endY = topPos + to.y + 8f
        drawLine(graphics, startX, startY, endX, endY, 0xFF33EEDD.toInt())
    }

    private fun drawLine(graphics: GuiGraphicsExtractor, x1: Float, y1: Float, x2: Float, y2: Float, color: Int) {
        val dx = x2 - x1
        val dy = y2 - y1
        val steps = max(abs(dx), abs(dy)).roundToInt().coerceAtLeast(1)

        for (i in 0..steps) {
            val t = i.toFloat() / steps.toFloat()
            val px = (x1 + dx * t).roundToInt()
            val py = (y1 + dy * t).roundToInt()
            graphics.fill(px, py, px + 2, py + 2, color)
        }
    }
}
