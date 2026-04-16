package me.mrai.larpclient.features.impl.skyblock.general.autoclicker

import me.mrai.larpclient.mixin.MinecraftAccessor
import me.mrai.larpclient.mixin.MinecraftInvoker
import me.mrai.larpclient.module.BoolSetting
import me.mrai.larpclient.module.Module
import me.mrai.larpclient.module.ModuleCategory
import me.mrai.larpclient.module.SliderSetting
import net.minecraft.client.Minecraft
import net.minecraft.client.player.LocalPlayer
import java.util.ArrayDeque
import kotlin.math.roundToLong
import kotlin.random.Random

object AutoClickerModule : Module(
    name = "Auto Clicker",
    description = "Configurable left-click autoclicker with CPS and humanisation.",
    category = ModuleCategory.SKYBLOCK_GENERAL
) {
    private val cpsSetting = SliderSetting("CPS", 12.0, 1.0, 30.0, 1.0)
    private val randomDelaySetting = SliderSetting("Random Delay", 0.15, 0.0, 1.0, 0.01)
    private val jitterAmountSetting = SliderSetting("Jitter Amount", 0.10, 0.0, 1.0, 0.01)
    private val randomisedDelaysSetting = BoolSetting("Randomised Delays", true)
    private val jitterSimulationSetting = BoolSetting("Jitter Simulation", true)

    private var nextClickAtNanos = 0L

    private val clickTimesMs = ArrayDeque<Long>()

    init {
        settings += listOf(
            cpsSetting,
            randomDelaySetting,
            jitterAmountSetting,
            randomisedDelaysSetting,
            jitterSimulationSetting
        )
    }

    override fun onEnable() {
        nextClickAtNanos = 0L
        synchronized(clickTimesMs) {
            clickTimesMs.clear()
        }
    }

    override fun onDisable() {
        nextClickAtNanos = 0L
        synchronized(clickTimesMs) {
            clickTimesMs.clear()
        }
    }

    override fun onTick() {
        val client = Minecraft.getInstance()
        val player = client.player ?: run {
            resetTimer()
            pruneClicks()
            return
        }

        if (client.screen != null) {
            resetTimer()
            pruneClicks()
            return
        }

        if (!client.options.keyAttack.isDown) {
            resetTimer()
            pruneClicks()
            return
        }

        val now = System.nanoTime()
        if (nextClickAtNanos != 0L && now < nextClickAtNanos) {
            pruneClicks()
            return
        }

        if (jitterSimulationSetting.value) {
            applyJitter(player)
        }

        performLeftClick(client)
        registerClick()

        nextClickAtNanos = now + computeDelayNanos()
        pruneClicks()
    }

    fun getLiveCps(): Int {
        pruneClicks()
        synchronized(clickTimesMs) {
            return clickTimesMs.size
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

    private fun registerClick() {
        val now = System.currentTimeMillis()
        synchronized(clickTimesMs) {
            clickTimesMs.addLast(now)
            while (clickTimesMs.isNotEmpty() && now - clickTimesMs.first() > 1000L) {
                clickTimesMs.removeFirst()
            }
        }
    }

    private fun pruneClicks() {
        val now = System.currentTimeMillis()
        synchronized(clickTimesMs) {
            while (clickTimesMs.isNotEmpty() && now - clickTimesMs.first() > 1000L) {
                clickTimesMs.removeFirst()
            }
        }
    }

    private fun resetTimer() {
        nextClickAtNanos = 0L
    }

    private fun computeDelayNanos(): Long {
        val cps = cpsSetting.value.coerceIn(1.0, 30.0)
        var intervalNanos = 1_000_000_000.0 / cps

        if (randomisedDelaysSetting.value) {
            val strength = randomDelaySetting.value.coerceIn(0.0, 1.0)
            val offset = (Random.nextDouble() * 2.0 - 1.0) * strength * 0.35
            intervalNanos *= (1.0 + offset).coerceAtLeast(0.15)
        }

        return intervalNanos.roundToLong().coerceAtLeast(1L)
    }

    private fun applyJitter(player: LocalPlayer) {
        val amount = jitterAmountSetting.value.coerceIn(0.0, 1.0)
        if (amount <= 0.0) return

        val yawOffset = ((Random.nextDouble() * 2.0) - 1.0) * (0.45 * amount)
        val pitchOffset = ((Random.nextDouble() * 2.0) - 1.0) * (0.25 * amount)

        player.yRot += yawOffset.toFloat()
        player.xRot = (player.xRot + pitchOffset.toFloat()).coerceIn(-90f, 90f)
    }
}