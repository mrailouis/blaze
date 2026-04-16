package me.mrai.larpclient.features.impl.dungeons.general.truesplits

import me.mrai.larpclient.ui.hud.HudPositionManager
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import net.minecraft.network.chat.Style

object TrueSplitsRenderer {

    private const val MAIN_LINE_HEIGHT = 10f
    private const val BREAKDOWN_LINE_HEIGHT = 10f

    fun render(graphics: GuiGraphicsExtractor) {
        if (!TrueSplitsModule.enabled) return

        if (TrueSplitsModule.showMainHud.value && (TrueSplitsState.active || TrueSplitsState.activeStageIndex >= 0)) {
            renderMainHud(graphics)
        }

        if (TrueSplitsModule.showBreakdownHud.value && TrueSplitsState.active) {
            renderBreakdownHud(graphics)
        }
    }

    private fun renderMainHud(graphics: GuiGraphicsExtractor) {
        val cfg = HudPositionManager.config.trueSplits
        val scale = HudPositionManager.config.trueSplitsScale
        var drawY = cfg.y

        for (i in 0..TrueSplitsState.activeStageIndex.coerceAtLeast(-1)) {
            val realMs = TrueSplitsState.stageRealTimeMs(i) ?: continue
            val tickSeconds = TrueSplitsState.stageTickTimeSeconds(i) ?: 0.0
            val line = buildMainLine(
                index = i,
                realMs = realMs,
                tickSeconds = tickSeconds,
                underline = TrueSplitsModule.underlineActive.value && i == TrueSplitsState.activeStageIndex
            )

            drawComponent(graphics, line, cfg.x, drawY, scale)
            drawY += MAIN_LINE_HEIGHT * scale
        }
    }

    fun renderMainPreview(graphics: GuiGraphicsExtractor) {
        if (!TrueSplitsModule.showMainHud.value) return
        val cfg = HudPositionManager.config.trueSplits
        val scale = HudPositionManager.config.trueSplitsScale
        var drawY = cfg.y

        previewMainLines().forEach { line ->
            drawComponent(graphics, line, cfg.x, drawY, scale)
            drawY += MAIN_LINE_HEIGHT * scale
        }
    }

    fun renderBreakdownPreview(graphics: GuiGraphicsExtractor) {
        if (!TrueSplitsModule.showBreakdownHud.value) return
        val cfg = HudPositionManager.config.trueSplitsBreakdown
        val scale = HudPositionManager.config.trueSplitsBreakdownScale
        var drawY = cfg.y

        previewBreakdownLines().forEach { line ->
            drawComponent(graphics, line, cfg.x, drawY, scale)
            drawY += BREAKDOWN_LINE_HEIGHT * scale
        }
    }

    private fun buildMainLine(
        index: Int,
        realMs: Long,
        tickSeconds: Double,
        underline: Boolean
    ): Component {
        val splitStyle = Style.EMPTY
            .withColor(TrueSplitsModule.mainSplitColor(index))
            .withUnderlined(underline)
        val realTimeStyle = Style.EMPTY.withColor(if (TrueSplitsState.isPb(index)) 0xFFFFAA00.toInt() else 0xFF55FF55.toInt())
        val tickStyle = Style.EMPTY.withColor(0xFF55FF55.toInt())
        val arrowStyle = Style.EMPTY.withColor(if (shouldUsePbArrow(index, realMs)) 0xFFFFAA00.toInt() else 0xFF55FFFF.toInt())

        return Component.literal(TrueSplitsState.stageLabels[index]).setStyle(splitStyle)
            .append(Component.literal(" > ").setStyle(arrowStyle))
            .append(styledText("${TrueSplitsState.formatSeconds(realMs / 1000.0)}s", realTimeStyle))
            .append(styledText(" (${TrueSplitsState.formatSeconds(tickSeconds)}s)", tickStyle))
    }

    private fun shouldUsePbArrow(index: Int, realMs: Long): Boolean {
        val pb = TrueSplitsState.storedPbMs(index) ?: return realMs > 1000L
        return realMs <= pb
    }

    private fun renderBreakdownHud(graphics: GuiGraphicsExtractor) {
        val cfg = HudPositionManager.config.trueSplitsBreakdown
        val scale = HudPositionManager.config.trueSplitsBreakdownScale
        val lines = TrueSplitsState.visibleBreakdownLines(TrueSplitsModule.breakdownScrollLength.value.toInt())
        var drawY = cfg.y

        for (line in lines) {
            val component = when (line) {
                is TrueSplitsState.BreakdownLine.Header -> buildBreakdownHeader(line.text)
                is TrueSplitsState.BreakdownLine.Entry -> buildBreakdownEntry(line.label, line.seconds)
            }
            drawComponent(graphics, component, cfg.x, drawY, scale)
            drawY += BREAKDOWN_LINE_HEIGHT * scale
        }
    }

    private fun buildBreakdownHeader(text: String): Component {
        return styledText(
            text,
            Style.EMPTY.withColor(0xFFD0D0D0.toInt()).withUnderlined(true).withBold(true)
        )
    }

    private fun buildBreakdownEntry(label: String, seconds: Double?): Component {
        return styledText(label, Style.EMPTY.withColor(0xFF55FFFF.toInt()))
            .append(styledText(" > ", Style.EMPTY.withColor(0xFFFFAA00.toInt())))
            .append(styledText("${TrueSplitsState.formatSeconds(seconds ?: 0.0)}s", Style.EMPTY.withColor(0xFF55FF55.toInt())))
    }

    private fun styledText(text: String, style: Style): MutableComponent {
        return Component.literal(text).setStyle(style).copy()
    }

    private fun drawComponent(
        graphics: GuiGraphicsExtractor,
        component: Component,
        x: Float,
        y: Float,
        scale: Float
    ) {
        val font = Minecraft.getInstance().font
        val pose = graphics.pose()
        pose.pushMatrix()
        pose.translate(x, y)
        pose.scale(scale, scale)
        graphics.text(font, component, 0, 0, 0xFFFFFFFF.toInt(), true)
        pose.popMatrix()
    }

    data class HudLayout(
        val x: Float,
        val y: Float,
        val width: Float,
        val height: Float
    )

    fun measureMainLayout(preview: Boolean = false): HudLayout {
        val cfg = HudPositionManager.config.trueSplits
        val scale = HudPositionManager.config.trueSplitsScale
        val font = Minecraft.getInstance().font
        val lines = if (preview) previewMainLines() else activeMainLines()
        val widest = lines.maxOfOrNull(font::width) ?: 190
        val count = lines.size.coerceAtLeast(1)
        return HudLayout(cfg.x, cfg.y, widest * scale, count * MAIN_LINE_HEIGHT * scale)
    }

    fun measureBreakdownLayout(preview: Boolean = false): HudLayout {
        val cfg = HudPositionManager.config.trueSplitsBreakdown
        val scale = HudPositionManager.config.trueSplitsBreakdownScale
        val font = Minecraft.getInstance().font
        val lines = if (preview) previewBreakdownLines() else activeBreakdownLines()
        val widest = lines.maxOfOrNull(font::width) ?: 0
        val count = lines.size.coerceAtLeast(1)
        return HudLayout(cfg.x, cfg.y, widest * scale, count * BREAKDOWN_LINE_HEIGHT * scale)
    }

    private fun activeMainLines(): List<Component> {
        val lines = mutableListOf<Component>()
        for (i in 0..TrueSplitsState.activeStageIndex.coerceAtLeast(-1)) {
            val realMs = TrueSplitsState.stageRealTimeMs(i) ?: continue
            val tickSeconds = TrueSplitsState.stageTickTimeSeconds(i) ?: 0.0
            lines += buildMainLine(
                index = i,
                realMs = realMs,
                tickSeconds = tickSeconds,
                underline = TrueSplitsModule.underlineActive.value && i == TrueSplitsState.activeStageIndex
            )
        }
        return lines
    }

    private fun activeBreakdownLines(): List<Component> {
        return TrueSplitsState.visibleBreakdownLines(TrueSplitsModule.breakdownScrollLength.value.toInt()).map { line ->
            when (line) {
                is TrueSplitsState.BreakdownLine.Header -> buildBreakdownHeader(line.text)
                is TrueSplitsState.BreakdownLine.Entry -> buildBreakdownEntry(line.label, line.seconds)
            }
        }
    }

    private fun previewMainLines(): List<Component> {
        return TrueSplitsState.stageLabels.mapIndexed { index, _ ->
            buildMainLine(index, 49600L, 49.60, underline = shouldUnderlinePreviewLine(index))
        }
    }

    private fun previewBreakdownLines(): List<Component> {
        val scrollLength = maxOf(TrueSplitsModule.breakdownScrollLength.value.toInt(), 6)
        val font = Minecraft.getInstance().font
        val widestHeader = TrueSplitsState.breakdownHeaders
            .map(::buildBreakdownHeader)
            .maxByOrNull(font::width)
            ?: buildBreakdownHeader("BREAKDOWN")
        val widestEntries = TrueSplitsState.breakdownElements
            .flatten()
            .map { label -> buildBreakdownEntry(label, 49.60) }
            .sortedByDescending(font::width)

        return buildList {
            add(widestHeader)
            widestEntries.take((scrollLength - 1).coerceAtLeast(0)).forEach(::add)
        }
    }

    private fun shouldUnderlinePreviewLine(index: Int): Boolean {
        return TrueSplitsModule.underlineActive.value && index == TrueSplitsState.stageLabels.lastIndex
    }
}
