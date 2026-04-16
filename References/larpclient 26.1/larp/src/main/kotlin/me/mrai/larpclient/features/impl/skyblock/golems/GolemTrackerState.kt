package me.mrai.larpclient.features.impl.skyblock.golems

import me.mrai.larpclient.util.TextSanitizer
import me.mrai.larpclient.util.LarpChat
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.phys.Vec3

object GolemTrackerState {
    private const val COUNTDOWN_MS = 20_000L
    private const val TREMBLE_MESSAGE = "youfeelatremorfrombeneaththeearth!"
    private const val COUNTDOWN_START_MESSAGE = "thegroundbeginstoshakeasanendstoneprotectorrisesfrombelow!"
    private const val SPAWNED_MESSAGE = "beware-anendstoneprotectorhasrisen!"
    private const val DOWN_MESSAGE = "endstoneprotectordown!"

    private val scanLocations = listOf(
        SpawnLocation("Left", BlockPos(-649, 5, -219), Vec3(-648.5, 7.5, -218.5)),
        SpawnLocation("Middle Front", BlockPos(-644, 5, -269), Vec3(-643.5, 7.5, -268.5)),
        SpawnLocation("Middle Center", BlockPos(-689, 5, -273), Vec3(-688.5, 7.5, -272.5)),
        SpawnLocation("Middle Behind", BlockPos(-727, 5, -284), Vec3(-726.5, 7.5, -283.5)),
        SpawnLocation("Right Front", BlockPos(-639, 5, -328), Vec3(-638.5, 7.5, -327.5)),
        SpawnLocation("Right Behind", BlockPos(-678, 5, -332), Vec3(-677.5, 7.5, -331.5))
    )

    @Volatile
    private var activeLocation: SpawnLocation? = null

    @Volatile
    private var stage: Int = 0

    @Volatile
    private var lobbyStage: Int = 0

    @Volatile
    private var lastLevelIdentity: Int = 0

    @Volatile
    private var countdownStartMs: Long = -1L

    @Volatile
    private var spawned: Boolean = false

    @Volatile
    private var lastSpawnLagSeconds: Double? = null

    @Volatile
    private var stageFourAnnounced = false

    fun onWorldChange() {
        activeLocation = null
        stage = 0
        lobbyStage = 0
        countdownStartMs = -1L
        spawned = false
        lastSpawnLagSeconds = null
        stageFourAnnounced = false
        lastLevelIdentity = 0
    }

    fun onChatMessage(rawMessage: String) {
        val compact = TextSanitizer.compactLower(rawMessage)
        val now = System.currentTimeMillis()
        when {
            compact.contains(TREMBLE_MESSAGE) -> {
                spawned = false
                if (stage == 0) {
                    stage = 1
                }
            }
            compact.contains(COUNTDOWN_START_MESSAGE) -> {
                stage = 4
                lobbyStage = maxOf(lobbyStage, 4)
                spawned = false
                lastSpawnLagSeconds = null
                countdownStartMs = now
            }
            compact.contains(SPAWNED_MESSAGE) -> markSpawned(now)
            compact.contains(DOWN_MESSAGE) -> {
                lastSpawnLagSeconds?.let { lag ->
                    LarpChat.send("End Stone server lag: ${"%.1f".format(lag)}s")
                }
                activeLocation = null
                stage = 0
                lobbyStage = 0
                spawned = false
                countdownStartMs = -1L
                lastSpawnLagSeconds = null
            }
        }
    }

    fun onTick() {
        val level = Minecraft.getInstance().level ?: run {
            onWorldChange()
            return
        }
        val levelIdentity = System.identityHashCode(level)
        if (lastLevelIdentity != 0 && lastLevelIdentity != levelIdentity) {
            onWorldChange()
        }
        lastLevelIdentity = levelIdentity

        scanActiveLocation()
        if (!spawned && detectArmorStandSpawn(level)) {
            markSpawned(System.currentTimeMillis())
        }
    }

    private fun scanActiveLocation() {
        val level = Minecraft.getInstance().level ?: return
        var foundLocation: SpawnLocation? = null
        var foundStage = 0
        var foundLobbyStage = 0

        for (location in scanLocations) {
            val detectedStage = detectStage(level, location.scanBase)
            if (detectedStage > foundLobbyStage) {
                foundLobbyStage = detectedStage
            }
            if (detectedStage > 0) {
                foundLocation = location
                foundStage = detectedStage
                break
            }
        }

        activeLocation = foundLocation
        lobbyStage = foundLobbyStage
        if (!spawned) {
            stage = foundStage
        }

        if (lobbyStage == 4) {
            if (!stageFourAnnounced) {
                val gui = Minecraft.getInstance().gui
                gui.setTimes(5, 35, 10)
                gui.setTitle(Component.literal("Awakening").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD))
                gui.setSubtitle(Component.empty())
                stageFourAnnounced = true
            }
        } else {
            stageFourAnnounced = false
        }
    }

    private fun detectArmorStandSpawn(level: net.minecraft.client.multiplayer.ClientLevel): Boolean {
        return level.entitiesForRendering().filterIsInstance<ArmorStand>().any { stand ->
            val name = stand.customName?.string ?: return@any false
            TextSanitizer.stripFormatting(name).contains("End Stone Protector", ignoreCase = true)
        }
    }

    private fun detectStage(level: net.minecraft.client.multiplayer.ClientLevel, basePos: BlockPos): Int {
        for (y in 9 downTo 5) {
            val pos = BlockPos(basePos.x, y, basePos.z)
            val blockId = BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).block).path
            val belowId = BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos.below()).block).path
            val validTop = blockId.contains("skull") || blockId.contains("head") || blockId.contains("clay") || blockId.contains("terracotta")
            if (validTop && belowId.contains("end_stone")) {
                return (y - 4).coerceIn(1, 5)
            }
        }
        return 0
    }

    private fun markSpawned(now: Long) {
        spawned = true
        stage = 5
        lobbyStage = 5
        if (countdownStartMs > 0L) {
            lastSpawnLagSeconds = ((now - countdownStartMs) - COUNTDOWN_MS).coerceAtLeast(0L).toDouble() / 1000.0
        }
        countdownStartMs = -1L
    }

    fun getLocationName(): String? = activeLocation?.name

    fun getLocationMarker(): Vec3? = activeLocation?.marker

    fun getStage(): Int = stage

    fun getLobbyStage(): Int = lobbyStage

    fun isStageFour(): Boolean = lobbyStage == 4

    fun getLobbyStageName(): String = when (lobbyStage) {
        0 -> "Resting"
        1 -> "Dormant"
        2 -> "Agitated"
        3 -> "Disturbed"
        4 -> "Awakening"
        5 -> "Summoned"
        else -> "Unknown"
    }

    fun isSpawned(): Boolean = spawned

    fun getCountdownText(now: Long = System.currentTimeMillis()): String? {
        if (spawned) return null
        if (countdownStartMs <= 0L) return null
        val seconds = (COUNTDOWN_MS - (now - countdownStartMs)).toDouble() / 1000.0
        return if (seconds >= 0.0) "+${"%.1f".format(seconds)}" else "%.1f".format(seconds)
    }

    private data class SpawnLocation(
        val name: String,
        val scanBase: BlockPos,
        val marker: Vec3
    )
}
