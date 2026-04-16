package me.mrai.larpclient.features.impl.dungeons.f7.general.termgui

import me.mrai.larpclient.integration.AddonAutomationAccess
import me.mrai.larpclient.module.BoolSetting
import me.mrai.larpclient.module.ComponentSetting
import me.mrai.larpclient.module.Module
import me.mrai.larpclient.module.ModuleCategory
import me.mrai.larpclient.module.SliderSetting
import me.mrai.larpclient.module.shownWhen
import me.mrai.larpclient.util.LarpBranding
import me.mrai.larpclient.util.TextSanitizer
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.world.inventory.ChestMenu
import net.minecraft.world.item.ItemStack
import kotlin.math.ceil
import kotlin.math.roundToInt

object TermGuiModule : Module(
    name = "Term GUI",
    description = "Replaces Floor 7 terminals with a compact custom solver GUI.",
    category = ModuleCategory.DUNGEONS_F7_P3
) {
    private enum class TerminalType {
        NUMBERS,
        COLORS,
        STARTS_WITH,
        RUBIX,
        RED_GREEN,
        MELODY
    }

    data class Layout(
        val originX: Int,
        val originY: Int,
        val scale: Float,
        val totalWidth: Int,
        val totalHeight: Int,
        val contentLeft: Int,
        val contentTop: Int,
        val slotSize: Int
    )

    data class SlotOverlay(
        val fillColor: Int? = null,
        val outlineColor: Int? = null,
        val label: String? = null,
        val labelColor: Int = 0xFFFFFFFF.toInt(),
        val labelScale: Float = 1f
    )

    data class TerminalView(
        val terminalName: String,
        val brandText: Component,
        val queuedCount: Int,
        val visibleSlots: List<Int>,
        val columns: Int,
        val overlays: Map<Int, SlotOverlay>,
        val clicks: Map<Int, Int>
    ) {
        val rows: Int
            get() = if (visibleSlots.isEmpty()) 0 else ceil(visibleSlots.size / columns.toDouble()).toInt()
    }

    private data class QueuedClick(
        val slot: Int,
        val button: Int,
        val predictOnSend: Boolean = false
    )

    private data class ActiveTerminal(
        val type: TerminalType,
        val title: String,
        val containerId: Int,
        val rows: Int,
        var lastSnapshot: List<String> = emptyList(),
        var predictedRemoved: MutableSet<Int> = linkedSetOf(),
        var predictedNumberOrder: MutableList<Int> = mutableListOf(),
        var predictedNumberAcks: MutableMap<Int, String> = linkedMapOf(),
        var predictedRubix: MutableMap<Int, Int> = linkedMapOf(),
        var predictedRubixAcks: MutableMap<Int, String> = linkedMapOf(),
        var clickInFlight: Boolean = false,
        var clickDeadlineMs: Long = 0L,
        var queue: MutableList<QueuedClick> = mutableListOf(),
        var hoverSignature: String? = null,
        var hoverNextActionMs: Long = 0L,
        var hoverRemaining: Int = 0,
        var hoverSlot: Int = -1,
        var hoverButton: Int = 0
    )

    private val enabledNumbers = BoolSetting("Numbers Solver", true)
    private val enabledColors = BoolSetting("Colors Solver", true)
    private val enabledStartsWith = BoolSetting("Starts With Solver", true)
    private val enabledRubix = BoolSetting("Rubix Solver", true)
    private val enabledRedGreen = BoolSetting("Red Green Solver", true)
    private val enabledMelody = BoolSetting("Melody Solver", true)
    private val showSlots = BoolSetting("Show Slots", true)
    private val showNumbers = BoolSetting("Show Numbers", true)
    private val queueTerms = BoolSetting("Queue Terms", false)
        .shownWhen { showAutomationSettings }
    private val hoverTerms = BoolSetting("Hover Terms", false)
        .shownWhen { showAutomationSettings }

    private val scale = SliderSetting("GUI Scale", 1.0, 0.75, 3.0, 0.05)
    private val offsetX = SliderSetting("Offset X", 0.0, -400.0, 400.0, 1.0)
    private val offsetY = SliderSetting("Offset Y", 0.0, -300.0, 300.0, 1.0)
    private val paddingX = SliderSetting("Padding X", 8.0, 0.0, 80.0, 1.0)
    private val paddingY = SliderSetting("Padding Y", 8.0, 0.0, 80.0, 1.0)
    private val queueTimeout = SliderSetting("Queue Timeout Ms", 500.0, 50.0, 2000.0, 10.0)
        .shownWhen { showAutomationSettings }
    private val hoverFirstDelay = SliderSetting("Hover First Delay Ms", 150.0, 0.0, 1000.0, 10.0)
        .shownWhen { showAutomationSettings }
    private val hoverRepeatDelay = SliderSetting("Hover Repeat Delay Ms", 125.0, 0.0, 1000.0, 10.0)
        .shownWhen { showAutomationSettings }

    private val dimColor = ComponentSetting("Dim Color", "#50000000")
    private val frameColor = ComponentSetting("Frame Color", "#FF2A2A2A")
    private val backgroundColor = ComponentSetting("Background Color", "#D0101010")
    private val titleColor = ComponentSetting("Title Color", "#FFFFFFFF")
    private val promptColor = ComponentSetting("Prompt Color", "#FFFFFFFF")
    private val slotColor = ComponentSetting("Slot Color", "#FF171717")
    private val slotBorderColor = ComponentSetting("Slot Border Color", "#FF303030")
    private val colorsHighlightColor = ComponentSetting("Colors Highlight Color", "#7F00FF00")
    private val startsWithHighlightColor = ComponentSetting("Starts With Highlight Color", "#7F00FF00")
    private val redGreenHighlightColor = ComponentSetting("Red Green Highlight Color", "#7F00FF00")
    private val numbersColor1 = ComponentSetting("Numbers Color 1", "#FF00FF00")
    private val numbersColor2 = ComponentSetting("Numbers Color 2", "#9F00FF00")
    private val numbersColor3 = ComponentSetting("Numbers Color 3", "#4F00FF00")
    private val numbersTextColor = ComponentSetting("Numbers Text Color", "#FFFFFFFF")
    private val rubixLeftColor = ComponentSetting("Rubix Left Color", "#FF00FF00")
    private val rubixRightColor = ComponentSetting("Rubix Right Color", "#FFFF3333")
    private val rubixTextColor = ComponentSetting("Rubix Text Color", "#FFFFFFFF")
    private val melodyColumnColor = ComponentSetting("Melody Column Color", "#7FFF00FF")
    private val melodySlotColor = ComponentSetting("Melody Slot Color", "#FF00FF00")
    private val melodyCorrectButtonColor = ComponentSetting("Melody Correct Button Color", "#FF00FF00")
    private val melodyWrongButtonColor = ComponentSetting("Melody Wrong Button Color", "#FFFF3333")

    private var activeTerminal: ActiveTerminal? = null
    private var stickyRemovedSignature: String? = null
    private val stickyRemovedSlots = linkedSetOf<Int>()
    private val showAutomationSettings: Boolean
        get() = AddonAutomationAccess.hasAddonFeatures()
    private val automationAllowed: Boolean
        get() = AddonAutomationAccess.isAutomationEnabled()

    init {
        settings += enabledNumbers
        settings += enabledColors
        settings += enabledStartsWith
        settings += enabledRubix
        settings += enabledRedGreen
        settings += enabledMelody
        settings += showSlots
        settings += showNumbers
        settings += scale
        settings += offsetX
        settings += offsetY
        settings += paddingX
        settings += paddingY
        settings += queueTerms
        settings += hoverTerms
        settings += queueTimeout
        settings += hoverFirstDelay
        settings += hoverRepeatDelay
        settings += dimColor
        settings += frameColor
        settings += backgroundColor
        settings += titleColor
        settings += promptColor
        settings += slotColor
        settings += slotBorderColor
        settings += colorsHighlightColor
        settings += startsWithHighlightColor
        settings += redGreenHighlightColor
        settings += numbersColor1
        settings += numbersColor2
        settings += numbersColor3
        settings += numbersTextColor
        settings += rubixLeftColor
        settings += rubixRightColor
        settings += rubixTextColor
        settings += melodyColumnColor
        settings += melodySlotColor
        settings += melodyCorrectButtonColor
        settings += melodyWrongButtonColor
    }

    fun createReplacementScreen(screen: Screen): Screen? {
        if (!enabled) return null
        if (screen !is AbstractContainerScreen<*>) return null

        val menu = screen.menu as? ChestMenu ?: return null
        val title = TextSanitizer.stripFormatting(screen.title.string)
        val type = classify(title) ?: return null
        if (!isTypeEnabled(type)) return null
        val signature = "${type.name}|$title|${menu.rowCount}"

        if (stickyRemovedSignature != signature) {
            stickyRemovedSignature = signature
            stickyRemovedSlots.clear()
        }

        val previous = activeTerminal
        activeTerminal = if (
            previous != null &&
            previous.type == type &&
            previous.title == title &&
            previous.containerId == menu.containerId &&
            previous.rows == menu.rowCount
        ) {
            previous
        } else {
            ActiveTerminal(
                type = type,
                title = title,
                containerId = menu.containerId,
                rows = menu.rowCount
            )
        }
        return TermGuiScreen()
    }

    override fun onDisable() {
        if (client().screen is TermGuiScreen) {
            client().setScreen(null)
        }
        activeTerminal = null
        stickyRemovedSignature = null
        stickyRemovedSlots.clear()
    }

    override fun onTick() {
        val client = client()
        val player = client.player
        val active = activeTerminal

        if (player == null || active == null) {
            if (client.screen is TermGuiScreen) {
                client.setScreen(null)
            }
            activeTerminal = null
            stickyRemovedSignature = null
            stickyRemovedSlots.clear()
            return
        }

        val menu = player.containerMenu as? ChestMenu
        val valid = menu != null && menu.containerId == active.containerId && menu.rowCount == active.rows
        if (!valid) {
            if (client.screen is TermGuiScreen) {
                client.setScreen(null)
            }
            activeTerminal = null
            stickyRemovedSignature = null
            stickyRemovedSlots.clear()
            return
        }

        if (active.clickInFlight && System.currentTimeMillis() >= active.clickDeadlineMs) {
            active.clickInFlight = false
            active.queue.clear()
        }
    }

    fun onWorldChange() {
        activeTerminal = null
        stickyRemovedSignature = null
        stickyRemovedSlots.clear()
    }

    fun closeActiveFromUser() {
        client().player?.closeContainer()
        activeTerminal = null
        stickyRemovedSignature = null
        stickyRemovedSlots.clear()
    }

    fun buildView(): TerminalView? {
        val active = activeTerminal ?: return null
        val menu = client().player?.containerMenu as? ChestMenu ?: return null
        if (menu.containerId != active.containerId || menu.rowCount != active.rows) return null

        val topSize = active.rows * 9
        val slots = menu.slots.take(topSize).map { it.item.copy() }
        syncPredictions(active, slots)
        val view = when (active.type) {
            TerminalType.NUMBERS -> buildNumbersView(active, slots)
            TerminalType.COLORS -> buildColorsView(active, slots)
            TerminalType.STARTS_WITH -> buildStartsWithView(active, slots)
            TerminalType.RUBIX -> buildRubixView(active, slots)
            TerminalType.RED_GREEN -> buildRedGreenView(active, slots)
            TerminalType.MELODY -> buildMelodyView(active, slots)
        }
        processQueuedClick(active, view)
        return view
    }

    fun clickSlot(slot: Int, button: Int): Boolean {
        return requestClick(slot, button)
    }

    fun onHoverSlot(slot: Int, button: Int) {
        val active = activeTerminal ?: return
        val signature = "$slot:$button"
        val now = System.currentTimeMillis()

        if (active.hoverSignature != signature) {
            active.hoverSignature = signature
            active.hoverSlot = slot
            active.hoverButton = button
            active.hoverRemaining = hoverClickCount(active, slot)
            active.hoverNextActionMs = now + hoverFirstDelay.value.toLong()
        }

        if (active.hoverRemaining <= 0) return
        if (now < active.hoverNextActionMs) return

        val queued = requestClick(
            slot = active.hoverSlot,
            button = active.hoverButton,
            allowQueue = true,
            predictWhenQueued = false
        )
        if (queued) {
            active.hoverRemaining--
            active.hoverNextActionMs = now + hoverRepeatDelay.value.toLong()
        }
    }

    fun clearHoverState() {
        activeTerminal?.let { active ->
            active.hoverSignature = null
            active.hoverNextActionMs = 0L
            active.hoverRemaining = 0
            active.hoverSlot = -1
            active.hoverButton = 0
        }
    }

    fun hoverTermsEnabled(): Boolean = automationAllowed && hoverTerms.value

    private fun requestClick(
        slot: Int,
        button: Int,
        allowQueue: Boolean = automationAllowed && queueTerms.value,
        predictWhenQueued: Boolean = true
    ): Boolean {
        val client = client()
        val player = client.player ?: return false
        val active = activeTerminal ?: return false
        val menu = player.containerMenu as? ChestMenu ?: return false
        if (menu.containerId != active.containerId) return false

        if (allowQueue && (active.clickInFlight || active.queue.isNotEmpty())) {
            active.queue += TermGuiModule.QueuedClick(
                slot = slot,
                button = button,
                predictOnSend = !predictWhenQueued
            )
            if (predictWhenQueued) {
                applyPrediction(active, slot, button, menu)
            }
            return true
        }

        val success = sendContainerClick(active, slot, button)
        if (success) {
            active.clickInFlight = true
            active.clickDeadlineMs = System.currentTimeMillis() + queueTimeout.value.toLong()
            applyPrediction(active, slot, button, menu)
        }
        return success
    }

    private fun sendContainerClick(active: ActiveTerminal, slot: Int, button: Int): Boolean {
        val client = client()
        val player = client.player ?: return false
        val gameMode = client.gameMode ?: return false
        val success = try {
            val clickMethod = gameMode.javaClass.declaredMethods.firstOrNull { method ->
                method.parameterCount == 5 &&
                        method.parameterTypes[0] == Int::class.javaPrimitiveType &&
                        method.parameterTypes[1] == Int::class.javaPrimitiveType &&
                        method.parameterTypes[2] == Int::class.javaPrimitiveType &&
                        method.parameterTypes[3].isEnum &&
                        net.minecraft.world.entity.player.Player::class.java.isAssignableFrom(method.parameterTypes[4])
            } ?: return false

            clickMethod.isAccessible = true
            val clickTypeClass = clickMethod.parameterTypes[3]
            val clickType = clickTypeClass.enumConstants.firstOrNull {
                it.toString().equals("PICKUP", ignoreCase = true)
            } ?: clickTypeClass.enumConstants.firstOrNull() ?: return false

            clickMethod.invoke(gameMode, active.containerId, slot, button, clickType, player)
            true
        } catch (_: Throwable) {
            false
        }
        return success
    }

    private fun applyPrediction(active: ActiveTerminal, slot: Int, button: Int, menu: ChestMenu) {
        when (active.type) {
            TerminalType.COLORS, TerminalType.STARTS_WITH, TerminalType.RED_GREEN -> {
                active.predictedRemoved += slot
                stickyRemovedSlots += slot
            }
            TerminalType.NUMBERS -> {
                val current = if (active.predictedNumberOrder.isNotEmpty()) {
                    active.predictedNumberOrder
                } else {
                    predictedNumberBaseOrder(currentTopStacks(menu))
                }
                if (current.firstOrNull() == slot) {
                    current.removeFirstOrNull()
                    active.predictedNumberOrder = current.toMutableList()
                    active.predictedNumberAcks[slot] = snapshotOf(menu.getSlot(slot).item)
                }
            }
            TerminalType.RUBIX -> {
                val current = buildRubixState(currentTopStacks(menu), active.predictedRubix)
                val delta = current[slot] ?: 0
                if (delta != 0) {
                    val next = if (button == 0) delta - 1 else delta + 1
                    if (next == 0) active.predictedRubix.remove(slot) else active.predictedRubix[slot] = next
                    active.predictedRubixAcks[slot] = snapshotOf(menu.getSlot(slot).item)
                }
            }
            else -> Unit
        }
    }

    fun layout(screenWidth: Int, screenHeight: Int, columns: Int, rows: Int): Layout {
        val scaleValue = scale.value.toFloat()
        val slotSize = (18f * scaleValue).roundToInt().coerceAtLeast(18)
        val padX = (paddingX.value * scaleValue).roundToInt()
        val padY = (paddingY.value * scaleValue).roundToInt()
        val headerHeight = (18f * scaleValue).roundToInt()
        val totalWidth = columns * slotSize + padX * 2 + 4
        val totalHeight = rows * slotSize + padY * 2 + headerHeight + 4
        val originX = ((screenWidth - totalWidth) / 2f + offsetX.value).roundToInt()
        val originY = ((screenHeight - totalHeight) / 2f + offsetY.value).roundToInt()

        return Layout(
            originX = originX,
            originY = originY,
            scale = scaleValue,
            totalWidth = totalWidth,
            totalHeight = totalHeight,
            contentLeft = originX + 2 + padX,
            contentTop = originY + 2 + headerHeight + padY,
            slotSize = slotSize
        )
    }

    fun color(setting: ComponentSetting, fallback: Int): Int {
        val raw = setting.text.trim().removePrefix("#")
        return try {
            when (raw.length) {
                6 -> (0xFF000000 or raw.toLong(16)).toInt()
                8 -> raw.toLong(16).toInt()
                else -> fallback
            }
        } catch (_: Throwable) {
            fallback
        }
    }

    fun dimColor(): Int = color(dimColor, 0x50000000)
    fun frameColor(): Int = color(frameColor, 0xFF2A2A2A.toInt())
    fun backgroundColor(): Int = color(backgroundColor, 0xD0101010.toInt())
    fun titleColor(): Int = color(titleColor, 0xFFFFFFFF.toInt())
    fun promptColor(): Int = color(promptColor, 0xFFFFFFFF.toInt())
    fun slotColor(): Int = color(slotColor, 0xFF171717.toInt())
    fun slotBorderColor(): Int = color(slotBorderColor, 0xFF303030.toInt())
    fun showSlots(): Boolean = showSlots.value

    private fun isTypeEnabled(type: TerminalType): Boolean {
        return when (type) {
            TerminalType.NUMBERS -> enabledNumbers.value
            TerminalType.COLORS -> enabledColors.value
            TerminalType.STARTS_WITH -> enabledStartsWith.value
            TerminalType.RUBIX -> enabledRubix.value
            TerminalType.RED_GREEN -> enabledRedGreen.value
            TerminalType.MELODY -> enabledMelody.value
        }
    }

    private fun classify(title: String): TerminalType? {
        return when {
            title == "Click in order!" -> TerminalType.NUMBERS
            title.startsWith("Select all the ") && title.endsWith(" items!") -> TerminalType.COLORS
            title.startsWith("What starts with: '") && title.endsWith("'?") -> TerminalType.STARTS_WITH
            title == "Change all to same color!" -> TerminalType.RUBIX
            title == "Correct all the panes!" -> TerminalType.RED_GREEN
            title == "Click the button on time!" -> TerminalType.MELODY
            else -> null
        }
    }

    private fun syncPredictions(active: ActiveTerminal, slots: List<ItemStack>) {
        val snapshot = slots.map(::snapshotOf)
        if (snapshot != active.lastSnapshot) {
            active.clickInFlight = false
            val numberIt = active.predictedNumberAcks.entries.iterator()
            while (numberIt.hasNext()) {
                val entry = numberIt.next()
                if (snapshot.getOrNull(entry.key) != entry.value) {
                    numberIt.remove()
                }
            }

            val rubixIt = active.predictedRubixAcks.entries.iterator()
            while (rubixIt.hasNext()) {
                val entry = rubixIt.next()
                if (snapshot.getOrNull(entry.key) != entry.value) {
                    rubixIt.remove()
                    active.predictedRubix.remove(entry.key)
                }
            }

            active.lastSnapshot = snapshot
            if (active.predictedNumberAcks.isEmpty()) {
                active.predictedNumberOrder.clear()
            } else {
                val baseOrder = predictedNumberBaseOrder(slots)
                active.predictedNumberOrder = active.predictedNumberOrder.filter { it in baseOrder }.toMutableList()
            }
            if (active.predictedRubixAcks.isEmpty()) {
                active.predictedRubix.clear()
            }
        }
    }

    private fun processQueuedClick(active: ActiveTerminal, view: TerminalView) {
        if (!automationAllowed) return
        if (active.clickInFlight) return
        if (active.queue.isEmpty()) return

        val next = active.queue.firstOrNull() ?: return
        val expected = view.clicks[next.slot]
        if (expected == next.button) {
            val menu = client().player?.containerMenu as? ChestMenu ?: return
            val success = sendContainerClick(active, next.slot, next.button)
            if (!success) return
            active.queue.removeAt(0)
            active.clickInFlight = true
            active.clickDeadlineMs = System.currentTimeMillis() + queueTimeout.value.toLong()
            if (next.predictOnSend) {
                applyPrediction(active, next.slot, next.button, menu)
            }
        } else {
            active.queue.clear()
        }
    }

    fun queuedCount(): Int {
        val active = activeTerminal ?: return 0
        return active.queue.size + if (active.clickInFlight) 1 else 0
    }

    private fun hoverClickCount(active: ActiveTerminal, slot: Int): Int {
        return when (active.type) {
            TerminalType.RUBIX -> {
                val menu = client().player?.containerMenu as? ChestMenu ?: return 0
                kotlin.math.abs(buildRubixState(currentTopStacks(menu), active.predictedRubix)[slot] ?: 0)
            }
            else -> 1
        }
    }

    private fun currentTopStacks(menu: ChestMenu): List<ItemStack> {
        return menu.slots.take(menu.rowCount * 9).map { it.item.copy() }
    }

    private fun predictedNumberBaseOrder(slots: List<ItemStack>): MutableList<Int> {
        return slots
            .mapIndexedNotNull { slot, stack ->
                if (slot in NUMBERS_ALLOWED && isRedGlassPane(stack)) slot to stack.count else null
            }
            .sortedBy { it.second }
            .map { it.first }
            .toMutableList()
    }

    private fun buildNumbersView(active: ActiveTerminal, slots: List<ItemStack>): TerminalView {
        val fullSolution = slots
            .mapIndexedNotNull { slot, stack ->
                if (slot in NUMBERS_ALLOWED && paneColor(stack) != null) slot to stack.count else null
            }
            .sortedBy { it.second }
            .map { it.first }

        val effectiveOrder = if (active.predictedNumberOrder.isNotEmpty()) {
            active.predictedNumberOrder.filter { it in fullSolution }.toMutableList()
        } else {
            predictedNumberBaseOrder(slots)
        }
        if (active.predictedNumberOrder.isEmpty()) {
            active.predictedNumberOrder = effectiveOrder.toMutableList()
        }

        val overlays = linkedMapOf<Int, SlotOverlay>()
        effectiveOrder.take(3).forEachIndexed { index, slot ->
            val fill = when (index) {
                0 -> color(numbersColor1, 0xFF00FF00.toInt())
                1 -> color(numbersColor2, 0x9F00FF00.toInt())
                else -> color(numbersColor3, 0x4F00FF00)
            }
            overlays[slot] = SlotOverlay(fillColor = fill)
        }

        if (showNumbers.value) {
            fullSolution.forEachIndexed { index, slot ->
                val existing = overlays[slot]
                overlays[slot] = SlotOverlay(
                    fillColor = existing?.fillColor,
                    outlineColor = existing?.outlineColor,
                    label = (index + 1).toString(),
                    labelColor = color(numbersTextColor, 0xFFFFFFFF.toInt()),
                    labelScale = 1.4f
                )
            }
        }

        return TerminalView(
            terminalName = "Numbers",
            brandText = LarpBranding.brandWord(),
            queuedCount = queuedCount(),
            visibleSlots = NUMBERS_VISIBLE,
            columns = 7,
            overlays = overlays,
            clicks = effectiveOrder.firstOrNull()?.let { mapOf(it to 0) } ?: emptyMap()
        )
    }

    private fun buildColorsView(active: ActiveTerminal, slots: List<ItemStack>): TerminalView {
        val extra = Regex("^Select all the ([\\w ]+) items!$").find(active.title)?.groupValues?.getOrNull(1)?.lowercase()
            ?: return emptyView("Colors", LARGE_VISIBLE, 7)

        val solution = slots.indices.filter { slot ->
            slot in LARGE_ALLOWED &&
                    !slots[slot].isEmpty &&
                    !slots[slot].isEnchanted &&
                    fixColorName(TextSanitizer.normalizedLower(slots[slot].hoverName.string)).startsWith(extra) &&
                    slot !in active.predictedRemoved &&
                    slot !in stickyRemovedSlots
        }

        return setView(
            terminalName = "Colors",
            visibleSlots = LARGE_VISIBLE,
            columns = 7,
            solution = solution,
            highlightColor = color(colorsHighlightColor, 0x7F00FF00)
        )
    }

    private fun buildStartsWithView(active: ActiveTerminal, slots: List<ItemStack>): TerminalView {
        val extra = Regex("^What starts with: '(\\w)'\\?$").find(active.title)?.groupValues?.getOrNull(1)?.lowercase()
            ?: return emptyView("Starts With", LARGE_VISIBLE, 7)

        val solution = slots.indices.filter { slot ->
            slot in LARGE_ALLOWED &&
                    !slots[slot].isEmpty &&
                    !slots[slot].isEnchanted &&
                    TextSanitizer.normalizedLower(slots[slot].hoverName.string).startsWith(extra) &&
                    slot !in active.predictedRemoved &&
                    slot !in stickyRemovedSlots
        }

        return setView(
            terminalName = "Starts With",
            visibleSlots = LARGE_VISIBLE,
            columns = 7,
            solution = solution,
            highlightColor = color(startsWithHighlightColor, 0x7F00FF00)
        )
    }

    private fun buildRedGreenView(active: ActiveTerminal, slots: List<ItemStack>): TerminalView {
        val solution = slots.indices.filter { slot ->
            slot in RED_GREEN_ALLOWED &&
                    isRedGlassPane(slots[slot]) &&
                    slot !in active.predictedRemoved &&
                    slot !in stickyRemovedSlots
        }

        return setView(
            terminalName = "Red Green",
            visibleSlots = RED_GREEN_VISIBLE,
            columns = 5,
            solution = solution,
            highlightColor = color(redGreenHighlightColor, 0x7F00FF00)
        )
    }

    private fun buildRubixView(active: ActiveTerminal, slots: List<ItemStack>): TerminalView {
        val state = buildRubixState(slots, active.predictedRubix)
        val overlays = linkedMapOf<Int, SlotOverlay>()
        val actions = linkedMapOf<Int, Int>()

        state.forEach { (slot, delta) ->
            overlays[slot] = SlotOverlay(
                fillColor = if (delta > 0) color(rubixLeftColor, 0xFF00FF00.toInt()) else color(rubixRightColor, 0xFFFF3333.toInt()),
                label = delta.toString(),
                labelColor = color(rubixTextColor, 0xFFFFFFFF.toInt()),
                labelScale = 1.2f
            )
            actions[slot] = if (delta > 0) 0 else 1
        }

        return TerminalView(
            terminalName = "Rubix",
            brandText = LarpBranding.brandWord(),
            queuedCount = queuedCount(),
            visibleSlots = RUBIX_VISIBLE,
            columns = 3,
            overlays = overlays,
            clicks = actions
        )
    }

    private fun buildMelodyView(active: ActiveTerminal, slots: List<ItemStack>): TerminalView {
        val overlays = linkedMapOf<Int, SlotOverlay>()
        var correctColumn = -1
        var buttonRow = -1
        var currentColumn = -1

        slots.forEachIndexed { slot, stack ->
            val pane = paneColor(stack) ?: return@forEachIndexed
            if (pane == "magenta") {
                correctColumn = slot - 1
            }
            if (pane == "lime") {
                buttonRow = slot / 9 - 1
                currentColumn = slot % 9 - 1
            }
        }

        if (correctColumn >= 0) {
            listOf(correctColumn + 10, correctColumn + 19, correctColumn + 28, correctColumn + 37).forEach { slot ->
                if (slot in MELODY_ALLOWED) {
                    overlays[slot] = SlotOverlay(fillColor = color(melodyColumnColor, 0x7FFF00FF))
                }
            }
        }

        val actions = linkedMapOf<Int, Int>()
        listOf(16, 25, 34, 43).forEach { slot ->
            if (slot !in MELODY_ALLOWED) return@forEach
            overlays[slot] = SlotOverlay(fillColor = color(melodyWrongButtonColor, 0xFFFF3333.toInt()))
            actions[slot] = 0
        }

        if (buttonRow >= 0) {
            val slot = buttonRow * 9 + 16
            if (slot in MELODY_ALLOWED) {
                overlays[slot] = SlotOverlay(fillColor = color(melodyCorrectButtonColor, 0xFF00FF00.toInt()))
                actions.clear()
                actions[slot] = 0
            }
        }

        if (buttonRow >= 0 && currentColumn >= 0) {
            val slot = buttonRow * 9 + 10 + currentColumn
            if (slot in MELODY_ALLOWED) {
                overlays[slot] = SlotOverlay(fillColor = color(melodySlotColor, 0xFF00FF00.toInt()))
            }
        }

        return TerminalView(
            terminalName = "Melody",
            brandText = LarpBranding.brandWord(),
            queuedCount = queuedCount(),
            visibleSlots = LARGE_VISIBLE,
            columns = 7,
            overlays = overlays,
            clicks = actions
        )
    }

    private fun setView(
        terminalName: String,
        visibleSlots: List<Int>,
        columns: Int,
        solution: List<Int>,
        highlightColor: Int
    ): TerminalView {
        return TerminalView(
            terminalName = terminalName,
            brandText = LarpBranding.brandWord(),
            queuedCount = queuedCount(),
            visibleSlots = visibleSlots,
            columns = columns,
            overlays = solution.associateWith { SlotOverlay(fillColor = highlightColor) },
            clicks = solution.associateWith { 0 }
        )
    }

    private fun emptyView(name: String, visibleSlots: List<Int>, columns: Int): TerminalView {
        return TerminalView(
            terminalName = name,
            brandText = LarpBranding.brandWord(),
            queuedCount = queuedCount(),
            visibleSlots = visibleSlots,
            columns = columns,
            overlays = emptyMap(),
            clicks = emptyMap()
        )
    }

    private fun buildRubixState(slots: List<ItemStack>, predictions: Map<Int, Int>): Map<Int, Int> {
        val order = listOf("red", "orange", "yellow", "green", "blue")
        val clicks = IntArray(order.size)

        repeat(order.size) { i ->
            val target = order[wrapIndex(i, order.size)]
            RUBIX_ALLOWED.forEach { slot ->
                val pane = paneColor(slots[slot]) ?: return@forEach
                if (pane == target) return@forEach
                if (pane == order[wrapIndex(i - 2, order.size)]) clicks[i] += 2
                else if (pane == order[wrapIndex(i - 1, order.size)]) clicks[i] += 1
                else if (pane == order[wrapIndex(i + 1, order.size)]) clicks[i] += 1
                else if (pane == order[wrapIndex(i + 2, order.size)]) clicks[i] += 2
            }
        }

        val origin = clicks.withIndex().minByOrNull { it.value }?.index ?: 0
        val base = linkedMapOf<Int, Int>()
        RUBIX_ALLOWED.forEach { slot ->
            val pane = paneColor(slots[slot]) ?: return@forEach
            val delta = when (pane) {
                order[wrapIndex(origin - 2, order.size)] -> 2
                order[wrapIndex(origin - 1, order.size)] -> 1
                order[wrapIndex(origin + 1, order.size)] -> -1
                order[wrapIndex(origin + 2, order.size)] -> -2
                else -> 0
            }
            if (delta != 0) {
                base[slot] = delta
            }
        }

        if (predictions.isEmpty()) return base

        val merged = linkedMapOf<Int, Int>()
        merged.putAll(base)
        predictions.forEach { (slot, delta) ->
            if (delta == 0) {
                merged.remove(slot)
            } else {
                merged[slot] = delta
            }
        }
        return merged
    }

    private fun snapshotOf(stack: ItemStack): String {
        if (stack.isEmpty) return "empty"
        return buildString {
            append(BuiltInRegistries.ITEM.getKey(stack.item))
            append('|')
            append(stack.count)
            append('|')
            append(paneColor(stack) ?: "none")
            append('|')
            append(TextSanitizer.normalizedLower(stack.hoverName.string))
            append('|')
            append(stack.isEnchanted)
        }
    }

    private fun paneColor(stack: ItemStack): String? {
        if (stack.isEmpty) return null
        val path = BuiltInRegistries.ITEM.getKey(stack.item).path
        return when {
            path.endsWith("_stained_glass_pane") -> path.removeSuffix("_stained_glass_pane")
            path.endsWith("_stained_glass") -> path.removeSuffix("_stained_glass")
            else -> null
        }
    }

    private fun isRedGlassPane(stack: ItemStack): Boolean = paneColor(stack) == "red"

    private fun fixColorName(name: String): String {
        return name
            .replace(Regex("^light gray"), "silver")
            .replace(Regex("^wool"), "white")
            .replace(Regex("^bone"), "white")
            .replace(Regex("^ink"), "black")
            .replace(Regex("^lapis"), "blue")
            .replace(Regex("^cocoa"), "brown")
            .replace(Regex("^dandelion"), "yellow")
            .replace(Regex("^rose"), "red")
            .replace(Regex("^cactus"), "green")
    }

    private fun wrapIndex(index: Int, size: Int): Int = ((index % size) + size) % size

    private val NUMBERS_ALLOWED = setOf(10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25)
    private val LARGE_ALLOWED = setOf(
        10, 11, 12, 13, 14, 15, 16,
        19, 20, 21, 22, 23, 24, 25,
        28, 29, 30, 31, 32, 33, 34,
        37, 38, 39, 40, 41, 42, 43
    )
    private val RED_GREEN_ALLOWED = setOf(11, 12, 13, 14, 15, 20, 21, 22, 23, 24, 29, 30, 31, 32, 33)
    private val RUBIX_ALLOWED = listOf(12, 13, 14, 21, 22, 23, 30, 31, 32)
    private val MELODY_ALLOWED = LARGE_ALLOWED

    private val NUMBERS_VISIBLE = listOf(10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25)
    private val LARGE_VISIBLE = listOf(
        10, 11, 12, 13, 14, 15, 16,
        19, 20, 21, 22, 23, 24, 25,
        28, 29, 30, 31, 32, 33, 34,
        37, 38, 39, 40, 41, 42, 43
    )
    private val RED_GREEN_VISIBLE = listOf(11, 12, 13, 14, 15, 20, 21, 22, 23, 24, 29, 30, 31, 32, 33)
    private val RUBIX_VISIBLE = listOf(12, 13, 14, 21, 22, 23, 30, 31, 32)
}
