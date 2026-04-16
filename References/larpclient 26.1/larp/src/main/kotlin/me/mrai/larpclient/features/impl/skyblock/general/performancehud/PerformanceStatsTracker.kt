package me.mrai.larpclient.features.impl.skyblock.general.performancehud

import net.minecraft.client.Minecraft
import net.minecraft.util.Util
import kotlin.math.min

object PerformanceStatsTracker {
    @Volatile
    var averageTps: Float = 20f
        private set

    @Volatile
    var currentPing: Int = 0
        private set

    @Volatile
    var averagePing: Int = 0
        private set

    @Volatile
    private var previousTimePacketAt: Long = 0L

    fun onTimePacketReceived() {
        val now = System.currentTimeMillis()
        val previous = previousTimePacketAt
        if (previous != 0L) {
            averageTps = (20000f / (now - previous + 1L)).coerceIn(0f, 20f)
        }
        previousTimePacketAt = now
    }

    fun onPongResponse(sentAt: Long) {
        currentPing = (Util.getMillis() - sentAt).toInt().coerceAtLeast(0)
    }

    fun refreshPingAverage() {
        averagePing = runCatching {
            val client = Minecraft.getInstance()
            if (client.player == null || client.level == null || client.connection == null) {
                return@runCatching currentPing.coerceAtLeast(0)
            }

            val pingLog = client.debugOverlay.pingLogger
            val sampleSize = min(pingLog.size(), 20)
            if (sampleSize == 0) {
                currentPing
            } else {
                var total = 0L
                for (index in 0 until sampleSize) {
                    total += pingLog.get(index)
                }
                (total / sampleSize).toInt()
            }
        }.getOrDefault(currentPing.coerceAtLeast(0))
    }

    fun reset() {
        averageTps = 20f
        currentPing = 0
        averagePing = 0
        previousTimePacketAt = 0L
    }
}
