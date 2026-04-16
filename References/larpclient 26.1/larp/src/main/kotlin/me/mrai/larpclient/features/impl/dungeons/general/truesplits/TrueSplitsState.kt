package me.mrai.larpclient.features.impl.dungeons.general.truesplits

import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import me.mrai.larpclient.util.LarpBranding
import me.mrai.larpclient.util.LarpLog
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.Minecraft
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale

object TrueSplitsState {
    val stageLabels = listOf(
        "Blood Open",
        "Watcher",
        "Portal",
        "Maxor",
        "Storm",
        "Terminals",
        "Goldor",
        "Necron",
        "Dragons"
    )

    val breakdownHeaders = listOf(
        "MAXOR BREAKDOWN",
        "STORM BREAKDOWN",
        "GOLDOR BREAKDOWN",
        "NECRON BREAKDOWN",
        "DRAGONS BREAKDOWN"
    )

    val breakdownElements = listOf(
        listOf("Crystals", "Maxor Kill"),
        listOf("First Pillar Kill", "Second Pillar Kill"),
        listOf("Section 1", "Section 2", "Section 3", "Section 4", "Goldor Kill"),
        listOf("Necron Kill"),
        listOf("Relics", "Third Dragon Kill", "Fourth Dragon Kill", "Fifth Dragon Kill")
    )

    val bloodStartMessages = setOf(
        "[BOSS] The Watcher: Congratulations, you made it through the Entrance.",
        "[BOSS] The Watcher: Ah, you've finally arrived.",
        "[BOSS] The Watcher: Ah, we meet again...",
        "[BOSS] The Watcher: So you made it this far... interesting.",
        "[BOSS] The Watcher: You've managed to scratch and claw your way here, eh?",
        "[BOSS] The Watcher: I'm starting to get tired of seeing you around here...",
        "[BOSS] The Watcher: Oh.. hello?",
        "[BOSS] The Watcher: Things feel a little more roomy now, eh?"
    )

    const val DUNGEON_START = "[NPC] Mort: Here, I found this map when I first entered the dungeon."
    const val WATCHER_DONE = "[BOSS] The Watcher: You have proven yourself. You may pass."
    const val MAXOR_START = "[BOSS] Maxor: WELL! WELL! WELL! LOOK WHO'S HERE!"
    const val STORM_START = "[BOSS] Storm: Pathetic Maxor, just like expected."
    const val GOLDOR_START = "[BOSS] Goldor: Who dares trespass into my domain?"
    const val CORE_OPEN = "The Core entrance is opening!"
    const val NECRON_START = "[BOSS] Necron: You went further than any human before, congratulations."
    const val DRAGONS_START = "[BOSS] Necron: All this, for nothing..."
    const val DUNGEON_END = "> EXTRA STATS <"

    val dragonKillStrings = setOf(
        "[BOSS] Wither King: Oh, this one hurts!",
        "[BOSS] Wither King: I have more of those.",
        "[BOSS] Wither King: My soul is disposable."
    )

    enum class RunStage {
        IDLE,
        START,
        BLOOD_OPEN,
        WATCHER,
        PORTAL,
        MAXOR,
        STORM,
        TERMINALS,
        GOLDOR,
        NECRON,
        DRAGONS,
        FINISHED
    }

    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val configDir: Path = FabricLoader.getInstance().configDir.resolve("larpclient")
    private val pbFile: Path = configDir.resolve("true_splits_pbs.json")
    private val pbTimes = hashMapOf<String, Long>()
    private var pbDataLoaded = false

    var active = false
    var stage = RunStage.IDLE
    var activeStageIndex = -1

    private val stageStartTimes = mutableListOf<Long>()
    private val stageEndTimes = mutableListOf<Long>()
    private val stageTickTimes = mutableListOf<Int>()
    private var runningTickCounter = 0

    private val breakdownStartTimes = mutableListOf<Long>()
    private val breakdownEndTimes = mutableListOf<Long>()
    var breakdownScrollIndex = 0
    private var pendingBreakdownScrollAtMs = -1L
    private var pendingBreakdownScrollAmount = 0

    private var terminalGateOpen = false
    private var lastCompletedTerminal = 0
    private var terminalSectionStage = 0

    private var crystalCount = 0
    private var relicCount = 0
    private var dragonCount = 0

    fun resetAll() {
        ensurePbDataLoaded()
        active = false
        stage = RunStage.IDLE
        activeStageIndex = -1
        stageStartTimes.clear()
        stageEndTimes.clear()
        stageTickTimes.clear()
        runningTickCounter = 0
        breakdownStartTimes.clear()
        breakdownEndTimes.clear()
        breakdownScrollIndex = 0
        pendingBreakdownScrollAtMs = -1L
        pendingBreakdownScrollAmount = 0
        terminalGateOpen = false
        lastCompletedTerminal = 0
        terminalSectionStage = 0
        crystalCount = 0
        relicCount = 0
        dragonCount = 0
    }

    fun onWorldChange() {
        resetAll()
    }

    fun onClientTick() {
        if (!TrueSplitsModule.enabled || !active) return

        runningTickCounter++

        if (pendingBreakdownScrollAtMs > 0L && System.currentTimeMillis() >= pendingBreakdownScrollAtMs) {
            repeat(pendingBreakdownScrollAmount) {
                breakdownScrollIndex++
            }
            pendingBreakdownScrollAtMs = -1L
            pendingBreakdownScrollAmount = 0
        }
    }

    fun onDungeonChat(raw: String) {
        if (!TrueSplitsModule.enabled) return
        val text = stripFormatting(raw).trim()

        when {
            text == DUNGEON_START -> {
                startRun()
            }

            active && stage == RunStage.BLOOD_OPEN && bloodStartMessages.contains(text) -> {
                forwardStage(1)
            }

            active && stage == RunStage.WATCHER && text == WATCHER_DONE -> {
                forwardStage(2)
            }

            active && stage == RunStage.PORTAL && text == MAXOR_START -> {
                forwardStage(3)
                startBreakdownSegment()
            }

            active && stage == RunStage.MAXOR && text == STORM_START -> {
                endBreakdownSegment()
                scheduleBreakdownScroll(6)
                forwardStage(4)
                startBreakdownSegment()
            }

            active && stage == RunStage.STORM && text == GOLDOR_START -> {
                endBreakdownSegment()
                scheduleBreakdownScroll(6)
                forwardStage(5)
                startBreakdownSegment()
                terminalGateOpen = false
                lastCompletedTerminal = 0
                terminalSectionStage = 0
            }

            active && stage == RunStage.TERMINALS && text == CORE_OPEN -> {
                forwardStage(6)
            }

            active && stage == RunStage.GOLDOR && text == NECRON_START -> {
                endBreakdownSegment()
                scheduleBreakdownScroll(2)
                forwardStage(7)
                startBreakdownSegment()
            }

            active && stage == RunStage.NECRON && text == DRAGONS_START -> {
                endBreakdownSegment()
                forwardStage(8)
                startBreakdownSegment()
                relicCount = 0
                dragonCount = 0
            }

            active && text.contains(DUNGEON_END) -> {
                finishRun()
            }

            active && (stage == RunStage.TERMINALS || stage == RunStage.GOLDOR) && text == "The gate has been destroyed!" -> {
                terminalGateOpen = true
                checkGoldorProgression()
            }

            active && (stage == RunStage.TERMINALS || stage == RunStage.GOLDOR) -> {
                parseTerminalCompletion(text)?.let { (completed, total) ->
                    if (total == completed && completed > lastCompletedTerminal) {
                        lastCompletedTerminal = completed
                    }
                    checkGoldorProgression()
                }
            }

            active && stage == RunStage.STORM && text == "⚠ Storm is enraged! ⚠" -> {
                endBreakdownSegment()
                startBreakdownSegment()
            }

            active && stage == RunStage.DRAGONS && dragonKillStrings.contains(text) -> {
                dragonCount++
                if (dragonCount in 3..5) {
                    endBreakdownSegment()
                    startBreakdownSegment()
                }
            }
        }
    }

    fun onWitherDeathSound() {
        if (!TrueSplitsModule.enabled || !active || stage != RunStage.DRAGONS) return
        relicCount++
        if (relicCount == 5) {
            endBreakdownSegment()
            startBreakdownSegment()
        }
    }

    fun onMaxorLaserCharged() {
        if (!TrueSplitsModule.enabled || !active || stage != RunStage.MAXOR) return
        crystalCount++
        if (crystalCount == 2) {
            endBreakdownSegment()
            startBreakdownSegment()
        }
    }

    private fun startRun() {
        resetAll()
        active = true
        stage = RunStage.START
        activeStageIndex = 0
        startStageTimer()
        stage = RunStage.BLOOD_OPEN
    }

    private fun forwardStage(completedStageIndex: Int) {
        endStageTimer()

        activeStageIndex = completedStageIndex
        stage = when (completedStageIndex) {
            1 -> RunStage.WATCHER
            2 -> RunStage.PORTAL
            3 -> RunStage.MAXOR
            4 -> RunStage.STORM
            5 -> RunStage.TERMINALS
            6 -> RunStage.GOLDOR
            7 -> RunStage.NECRON
            8 -> RunStage.DRAGONS
            else -> RunStage.FINISHED
        }

        if (stage != RunStage.FINISHED) {
            startStageTimer()
        }
    }

    private fun finishRun() {
        if (!active) return
        if (stage != RunStage.FINISHED) {
            endStageTimer()
        }
        endBreakdownSegment()
        stage = RunStage.FINISHED
        active = false
        activeStageIndex = stageLabels.lastIndex

        updatePbs()
        if (TrueSplitsModule.sendBreakdownsInChat.value) {
            sendBreakdownSummaryLinks()
        }
    }

    private fun startStageTimer() {
        stageStartTimes += System.currentTimeMillis()
        runningTickCounter = 0
    }

    private fun endStageTimer() {
        stageEndTimes += System.currentTimeMillis()
        stageTickTimes += runningTickCounter
        runningTickCounter = 0
    }

    private fun startBreakdownSegment() {
        breakdownStartTimes += System.currentTimeMillis()
    }

    private fun endBreakdownSegment() {
        if (breakdownEndTimes.size < breakdownStartTimes.size) {
            breakdownEndTimes += System.currentTimeMillis()
        }
    }

    private fun scheduleBreakdownScroll(amount: Int) {
        pendingBreakdownScrollAmount += amount
        pendingBreakdownScrollAtMs =
            System.currentTimeMillis() + (TrueSplitsModule.breakdownScrollDelay.value.toLong() * 1000L)
    }

    private fun checkGoldorProgression() {
        if (!terminalGateOpen) return
        if (lastCompletedTerminal !in setOf(7, 8)) return

        terminalGateOpen = false
        lastCompletedTerminal = 0
        endBreakdownSegment()
        startBreakdownSegment()
        terminalSectionStage++
    }

    private fun parseTerminalCompletion(text: String): Pair<Int, Int>? {
        val regex = Regex(""".+\((\d)/(\d)\)""")
        val match = regex.find(text) ?: return null
        val completed = match.groupValues[1].toIntOrNull() ?: return null
        val total = match.groupValues[2].toIntOrNull() ?: return null
        return completed to total
    }

    fun stageRealTimeMs(index: Int): Long? {
        val start = stageStartTimes.getOrNull(index) ?: return null
        val end = stageEndTimes.getOrNull(index) ?: System.currentTimeMillis()
        return end - start
    }

    fun stageTickTimeSeconds(index: Int): Double? {
        val ticks = stageTickTimes.getOrNull(index)
            ?: if (index == activeStageIndex && active) runningTickCounter else null
        return ticks?.toDouble()?.div(20.0)
    }

    fun visibleBreakdownLines(scrollLength: Int): List<BreakdownLine> {
        val all = mutableListOf<BreakdownLine>()
        var breakdownIndex = 0

        breakdownHeaders.forEachIndexed { headerIndex, header ->
            all += BreakdownLine.Header(header)
            breakdownElements[headerIndex].forEach { element ->
                val start = breakdownStartTimes.getOrNull(breakdownIndex)
                val end = breakdownEndTimes.getOrNull(breakdownIndex)
                    ?: if (start != null && active) System.currentTimeMillis() else null
                val seconds = if (start != null && end != null) ((end - start) / 1000.0) else null
                all += BreakdownLine.Entry(element, seconds)
                breakdownIndex++
            }
        }

        val from = breakdownScrollIndex.coerceIn(0, all.size.coerceAtLeast(0))
        val to = (from + scrollLength).coerceAtMost(all.size)
        return all.subList(from, to)
    }

    fun buildBreakdownDump(sectionIndex: Int): List<String> {
        if (sectionIndex !in breakdownHeaders.indices) return emptyList()

        var max = 0
        for (i in 0..sectionIndex) {
            max += breakdownElements[i].size
        }
        val min = max - breakdownElements[sectionIndex].size

        val out = mutableListOf<String>()
        out += breakdownHeaders[sectionIndex]
        for (i in min until max) {
            val start = breakdownStartTimes.getOrNull(i)
            val end = breakdownEndTimes.getOrNull(i)
            val sec = if (start != null && end != null) ((end - start) / 1000.0) else 0.0
            out += "${breakdownElements[sectionIndex][i - min]} > ${formatSeconds(sec)}s"
        }
        return out
    }

    private fun ensurePbDataLoaded() {
        if (pbDataLoaded) return
        pbDataLoaded = true
        try {
            if (!Files.exists(pbFile)) return
            Files.newBufferedReader(pbFile).use { reader ->
                val type = object : TypeToken<Map<String, Long>>() {}.type
                val loaded: Map<String, Long>? = gson.fromJson(reader, type)
                pbTimes.clear()
                if (loaded != null) {
                    pbTimes.putAll(loaded.filterValues { it > 0L })
                }
            }
        } catch (throwable: Throwable) {
            LarpLog.warn("Failed to load True Splits PB data from $pbFile: ${throwable.message ?: throwable.javaClass.simpleName}")
        }
    }

    private fun savePbs() {
        ensurePbDataLoaded()
        try {
            if (!Files.exists(configDir)) {
                Files.createDirectories(configDir)
            }
            Files.newBufferedWriter(pbFile).use { writer ->
                gson.toJson(pbTimes.toSortedMap(), writer)
            }
        } catch (throwable: Throwable) {
            LarpLog.warn("Failed to save True Splits PB data to $pbFile: ${throwable.message ?: throwable.javaClass.simpleName}")
        }
    }

    private fun updatePbs() {
        ensurePbDataLoaded()
        var changed = false
        for (i in stageLabels.indices) {
            val elapsed = stageRealTimeMs(i) ?: continue
            if (elapsed <= 1000L) continue
            val key = "r${i + 1}"
            val current = pbTimes[key]
            if (current == null || elapsed < current) {
                pbTimes[key] = elapsed
                changed = true
            }
        }
        if (changed) {
            savePbs()
        }
    }

    fun storedPbMs(index: Int): Long? {
        ensurePbDataLoaded()
        return pbTimes["r${index + 1}"]
    }

    fun isPb(index: Int): Boolean {
        val elapsed = stageRealTimeMs(index) ?: return false
        val current = storedPbMs(index) ?: return elapsed > 1000L
        return elapsed <= current
    }

    private fun sendBreakdownSummaryLinks() {
        val player = Minecraft.getInstance().player ?: return
        breakdownHeaders.forEachIndexed { index, header ->
            player.sendSystemMessage(
                LarpBranding.prefixed(
                    LarpBranding.text(header, LarpBranding.AQUA, bold = true)
                        .append(LarpBranding.muted(" - "))
                        .append(LarpBranding.text("Run ", LarpBranding.WHITE))
                        .append(LarpBranding.command(".truesplits breakdown ${index + 1}"))
                )
            )
        }
    }

    fun sendBreakdownDumpToChat(sectionIndex: Int) {
        val player = Minecraft.getInstance().player ?: return
        for (line in buildBreakdownDump(sectionIndex)) {
            player.sendSystemMessage(LarpBranding.prefixed(LarpBranding.text(line, LarpBranding.AQUA)))
        }
    }

    fun formatSeconds(seconds: Double): String {
        return String.format(Locale.US, "%.2f", seconds)
    }

    private fun stripFormatting(text: String): String {
        return text.replace(Regex("§[0-9A-FK-ORa-fk-or]"), "")
    }

    sealed class BreakdownLine {
        data class Header(val text: String) : BreakdownLine()
        data class Entry(val label: String, val seconds: Double?) : BreakdownLine()
    }
}
