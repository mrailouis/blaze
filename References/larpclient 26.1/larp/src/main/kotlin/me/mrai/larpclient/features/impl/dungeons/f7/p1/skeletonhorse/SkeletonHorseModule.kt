package me.mrai.larpclient.features.impl.dungeons.f7.p1.skeletonhorse

import me.mrai.larpclient.mixin.MinecraftAccessor
import me.mrai.larpclient.mixin.MinecraftInvoker
import me.mrai.larpclient.module.BoolSetting
import me.mrai.larpclient.module.Module
import me.mrai.larpclient.module.ModuleCategory
import me.mrai.larpclient.module.SliderSetting
import me.mrai.larpclient.util.TextSanitizer
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.multiplayer.MultiPlayerGameMode
import net.minecraft.core.component.DataComponents
import net.minecraft.world.inventory.ChestMenu
import net.minecraft.world.inventory.ContainerInput
import net.minecraft.world.item.Items
import kotlin.math.roundToLong
import kotlin.random.Random

object SkeletonHorseModule : Module(
    name = "Skeleton Horse",
    description = "Spawns skeleton horse in P1 and spams pearls at 5 CPS, with /gfs toggle.",
    category = ModuleCategory.DUNGEONS_F7_P1
) {
    private val cpsSetting = SliderSetting("Pearl CPS", 5.0, 1.0, 10.0, 0.5)
    private val enableGfsSetting = BoolSetting("Enable /gfs", true)
    private val randomDelaySetting = SliderSetting("Random Delay", 0.15, 0.0, 1.0, 0.01)

    private var nextClickAtNanos = 0L
    private var petsGuiOpenedAt = 0L
    private var horseSpawned = false
    private var waitingForPetsGui = false
    private var gfsExecuted = false

    init {
        settings += listOf(cpsSetting, enableGfsSetting, randomDelaySetting)
    }

    override fun onEnable() {
        nextClickAtNanos = 0L
        petsGuiOpenedAt = 0L
        horseSpawned = false
        waitingForPetsGui = false
        gfsExecuted = false
    }

    override fun onDisable() {
        nextClickAtNanos = 0L
        petsGuiOpenedAt = 0L
        horseSpawned = false
        waitingForPetsGui = false
        gfsExecuted = false
    }

    override fun onTick() {
        val client = Minecraft.getInstance()
        val player = client.player ?: return
        val connection = player.connection

        val screen = client.screen

        if (!horseSpawned && !waitingForPetsGui) {
            connection.sendCommand("pets")
            waitingForPetsGui = true
            petsGuiOpenedAt = System.currentTimeMillis()
            return
        }

        if (waitingForPetsGui && screen is AbstractContainerScreen<*>) {
            val inventory = screen.menu as? ChestMenu ?: return
            val skeletonHorseSlot = findSkeletonHorseSlot(inventory)
            if (skeletonHorseSlot >= 0) {
                performLeftClickOnSlot(client, inventory, skeletonHorseSlot)
                horseSpawned = true
                waitingForPetsGui = false
                nextClickAtNanos = System.nanoTime()
                if (enableGfsSetting.value && !gfsExecuted) {
                    connection.sendCommand("gfs")
                    gfsExecuted = true
                }
                return
            }

            if (System.currentTimeMillis() - petsGuiOpenedAt > 5000L) {
                waitingForPetsGui = false
            }
            return
        }

        if (horseSpawned && screen == null) {
            spamPearls(client)
        }
    }

    private fun findSkeletonHorseSlot(inventory: ChestMenu): Int {
        for ((slotIndex, slot) in inventory.slots.withIndex()) {
            val stack = slot.item
            if (stack.isEmpty) continue

            val lore = stack.get(DataComponents.LORE) ?: continue
            val lines = lore.lines().map { TextSanitizer.stripFormatting(it.string) }
            for (line in lines) {
                if (line.contains("Skeleton Horse", ignoreCase = true)) {
                    return slotIndex
                }
            }
        }
        return -1
    }

    private fun performLeftClickOnSlot(client: Minecraft, inventory: ChestMenu, slot: Int) {
        val player = client.player ?: return
        val gameMode = client.gameMode ?: return
        gameMode.handleContainerInput(inventory.containerId, slot, 0, ContainerInput.PICKUP, player)
    }

    private fun spamPearls(client: Minecraft) {
        val player = client.player ?: return

        val now = System.nanoTime()
        if (nextClickAtNanos != 0L && now < nextClickAtNanos) {
            return
        }

        for (i in 0 until 9) {
            val stack = player.inventory.getItem(i)
            if (stack.isEmpty || stack.item != Items.ENDER_PEARL) continue

            performLeftClick(client)
            nextClickAtNanos = now + computeDelayNanos()
            return
        }
    }

    private fun performLeftClick(client: Minecraft) {
        val accessor = client as MinecraftAccessor
        val invoker = client as MinecraftInvoker

        accessor.setMissTime(0)
        accessor.setRightClickDelay(0)

        invoker.callStartAttack()

        accessor.setMissTime(0)
    }

    private fun computeDelayNanos(): Long {
        val cps = cpsSetting.value.coerceIn(1.0, 10.0)
        var intervalNanos = 1_000_000_000.0 / cps

        val strength = randomDelaySetting.value.coerceIn(0.0, 1.0)
        val offset = (Random.nextDouble() * 2.0 - 1.0) * strength * 0.35
        intervalNanos *= (1.0 + offset).coerceAtLeast(0.15)

        return intervalNanos.roundToLong().coerceAtLeast(1L)
    }
}
