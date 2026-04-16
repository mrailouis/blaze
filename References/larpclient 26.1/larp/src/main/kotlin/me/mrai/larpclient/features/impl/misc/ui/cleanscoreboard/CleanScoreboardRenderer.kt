package me.mrai.larpclient.features.impl.misc.ui.cleanscoreboard

import me.mrai.larpclient.ui.clickgui.render.RoundedRenderer
import me.mrai.larpclient.ui.font.ModText
import me.mrai.larpclient.ui.hud.HudPositionManager
import me.mrai.larpclient.util.LarpBranding
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component
import net.minecraft.world.scores.DisplaySlot
import net.minecraft.world.scores.Objective
import net.minecraft.world.scores.PlayerScoreEntry
import net.minecraft.world.scores.PlayerTeam
import net.minecraft.world.scores.Scoreboard
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.roundToInt

object CleanScoreboardRenderer {

    data class Layout(
        val anchorX: Int,
        val anchorY: Int,
        val renderX: Int,
        val renderY: Int,
        val guiScaledWidth: Int,
        val guiScaledHeight: Int,
        val localWidth: Float,
        val localHeight: Float,
        val padding: Int,
        val lineSpacing: Int,
        val footerGap: Int,
        val radius: Float,
        val scale: Float,
        val titleComponent: Component,
        val titleWidth: Int,
        val lines: List<SidebarLine>,
        val lineHeight: Int
    )

    data class SidebarLine(
        val text: Component,
        val scoreValue: Int
    )

    fun render(context: GuiGraphicsExtractor) {
        if (!CleanScoreboardModule.enabled) return

        val client = Minecraft.getInstance()
        val layout = measureLayout(client, usePreviewWhenUnavailable = false) ?: return
        renderLayout(context, client, layout)
    }

    fun renderPreview(context: GuiGraphicsExtractor) {
        val client = Minecraft.getInstance()
        val layout = measurePreviewLayout(client) ?: return
        renderLayout(context, client, layout)
    }

    private fun renderLayout(
        context: GuiGraphicsExtractor,
        client: Minecraft,
        layout: Layout
    ) {
        val font = client.font
        val backgroundColor = parseColor(CleanScoreboardModule.scoreboardBackgroundColor.text) ?: 0xFF101114.toInt()
        val borderStartColor = parseColor(CleanScoreboardModule.scoreboardBorderStartColor.text) ?: 0xFF48E4E7.toInt()
        val borderEndColor = parseColor(CleanScoreboardModule.scoreboardBorderEndColor.text) ?: 0xFFB04DFF.toInt()

        if (CleanScoreboardModule.showBackground.value) {
            val alpha = (CleanScoreboardModule.backgroundAlpha.value * 255.0).roundToInt()
            val bg = withAlpha(backgroundColor, (alpha * 0.7).roundToInt())
            val inner = withAlpha(0xF7F9FC, (alpha * 0.18).roundToInt())

            RoundedRenderer.shadowedRoundedRect(
                context,
                layout.renderX.toFloat(),
                layout.renderY.toFloat(),
                layout.guiScaledWidth.toFloat(),
                layout.guiScaledHeight.toFloat(),
                layout.radius * layout.scale,
                bg,
                0,
                0f,
                0x22000000,
                14f * layout.scale,
                0f,
                4f * layout.scale
            )
            RoundedRenderer.roundedRect(
                context,
                layout.renderX + 2f * layout.scale,
                layout.renderY + 2f * layout.scale,
                layout.guiScaledWidth - 4f * layout.scale,
                layout.guiScaledHeight - 4f * layout.scale,
                (layout.radius - 2f).coerceAtLeast(4f) * layout.scale,
                inner
            )
        }

        if (CleanScoreboardModule.showBorder.value) {
            drawGradientBorder(
                context = context,
                x = layout.renderX.toFloat(),
                y = layout.renderY.toFloat(),
                w = layout.guiScaledWidth.toFloat(),
                h = layout.guiScaledHeight.toFloat(),
                radius = layout.radius * layout.scale,
                startColor = withAlpha(borderStartColor, 72),
                endColor = withAlpha(borderEndColor, 72)
            )
        }

        var drawY = layout.renderY + (layout.padding * layout.scale).roundToInt()

        val scaledTitleWidth = (layout.titleWidth * layout.scale).roundToInt()
        val titleX = layout.renderX + ((layout.guiScaledWidth - scaledTitleWidth) / 2)

        drawScaledComponent(
            context = context,
            font = font,
            text = layout.titleComponent,
            x = titleX,
            y = drawY,
            scale = layout.scale,
            color = 0xFFFFFFFF.toInt()
        )

        drawY += ((layout.lineHeight + 4) * layout.scale).roundToInt()

        val footerIndex = if (CleanScoreboardModule.showFooter.value && layout.lines.isNotEmpty()) {
            layout.lines.lastIndex
        } else {
            -1
        }

        for ((index, line) in layout.lines.withIndex()) {
            if (index == footerIndex) {
                drawY += (layout.footerGap * layout.scale).roundToInt()

                val footerWidth = (font.width(line.text) * layout.scale).roundToInt()
                val footerX = layout.renderX + ((layout.guiScaledWidth - footerWidth) / 2)

                drawScaledComponent(
                    context = context,
                    font = font,
                    text = line.text,
                    x = footerX,
                    y = drawY,
                    scale = layout.scale,
                    color = 0xFFFFFFFF.toInt()
                )
            } else {
                val lineX = layout.renderX + (layout.padding * layout.scale).roundToInt()

                drawScaledComponent(
                    context = context,
                    font = font,
                    text = line.text,
                    x = lineX,
                    y = drawY,
                    scale = layout.scale,
                    color = 0xFFFFFFFF.toInt()
                )
            }

            drawY += ((layout.lineHeight + layout.lineSpacing) * layout.scale).roundToInt()
        }
    }

    private fun drawScaledComponent(
        context: GuiGraphicsExtractor,
        font: Font,
        text: Component,
        x: Int,
        y: Int,
        scale: Float,
        color: Int
    ) {
        if (scale == 1f) {
            context.text(font, text, x, y, color, false)
            return
        }

        val matrices = context.pose()
        matrices.pushMatrix()
        matrices.translate(x.toFloat(), y.toFloat())
        matrices.scale(scale, scale)
        context.text(font, text, 0, 0, color, false)
        matrices.popMatrix()
    }

    private fun parseColor(text: String): Int? {
        val raw = text.trim().removePrefix("#")
        return try {
            when (raw.length) {
                6 -> (0xFF000000 or raw.toLong(16)).toInt()
                8 -> raw.toLong(16).toInt()
                else -> null
            }
        } catch (_: Throwable) {
            null
        }
    }

    fun measureLayout(
        client: Minecraft,
        usePreviewWhenUnavailable: Boolean
    ): Layout? {
        val scoreboard = client.level?.scoreboard
        val objective = scoreboard?.let { getSidebarObjective(it, client) }

        if (objective != null) {
            val liveScoreboard = scoreboard
            val titleComponent = objective.displayName
            val rawLines = collectLines(liveScoreboard, objective)
            if (rawLines.isEmpty() && !usePreviewWhenUnavailable) return null
            return buildLayout(client, titleComponent, rawLines)
        } else {
            if (!usePreviewWhenUnavailable) return null
            return measurePreviewLayout(client)
        }
    }

    fun measurePreviewLayout(client: Minecraft = Minecraft.getInstance()): Layout? {
        return buildLayout(
            client = client,
            titleComponent = ModText.literal("SKYBLOCK", true),
            rawLines = previewLines()
        )
    }

    private fun buildLayout(
        client: Minecraft,
        titleComponent: Component,
        rawLines: List<SidebarLine>
    ): Layout? {
        val font = client.font
        val lines = maybeReplaceBottomLine(rawLines)

        val padding = CleanScoreboardModule.padding.value.roundToInt()
        val lineSpacing = CleanScoreboardModule.lineSpacing.value.roundToInt()
        val footerGap = CleanScoreboardModule.footerGap.value.roundToInt()
        val topOffset = CleanScoreboardModule.topOffset.value.roundToInt()
        val rightOffset = CleanScoreboardModule.rightOffset.value.roundToInt()
        val radius = CleanScoreboardModule.roundness.value.toFloat()
        val scale = CleanScoreboardModule.hudScale.value.toFloat().coerceAtLeast(0.05f)
        val extraWidth = CleanScoreboardModule.extraWidth.value.roundToInt()
        val extraHeight = CleanScoreboardModule.extraHeight.value.roundToInt()

        val titleWidth = font.width(titleComponent)
        val maxLineWidth = lines.maxOfOrNull { font.width(it.text) } ?: 0
        val contentWidth = maxOf(titleWidth, maxLineWidth)

        val lineHeight = font.lineHeight
        val titleGap = 4

        val footerExtra = if (CleanScoreboardModule.showFooter.value && lines.isNotEmpty()) footerGap else 0
        val contentHeight =
            lineHeight +
                    titleGap +
                    (lines.size * lineHeight) +
                    ((lines.size - 1).coerceAtLeast(0) * lineSpacing) +
                    footerExtra

        val localWidth = (contentWidth + padding * 2 + extraWidth).coerceAtLeast(40).toFloat()
        val localHeight = (contentHeight + padding * 2 + extraHeight).coerceAtLeast(20).toFloat()

        val guiScaledWidth = (localWidth * scale).roundToInt().coerceAtLeast(1)
        val guiScaledHeight = (localHeight * scale).roundToInt().coerceAtLeast(1)

        if (HudPositionManager.config.scoreboard.x == 0f && HudPositionManager.config.scoreboard.y == 0f) {
            HudPositionManager.config.scoreboard.x =
                (client.window.guiScaledWidth - rightOffset).toFloat()
            HudPositionManager.config.scoreboard.y = topOffset.toFloat()
            HudPositionManager.save()
        }

        val anchorX = HudPositionManager.config.scoreboard.x.roundToInt()
        val anchorY = HudPositionManager.config.scoreboard.y.roundToInt()

        val renderX = anchorX - guiScaledWidth
        val renderY = anchorY

        return Layout(
            anchorX = anchorX,
            anchorY = anchorY,
            renderX = renderX,
            renderY = renderY,
            guiScaledWidth = guiScaledWidth,
            guiScaledHeight = guiScaledHeight,
            localWidth = localWidth,
            localHeight = localHeight,
            padding = padding,
            lineSpacing = lineSpacing,
            footerGap = footerGap,
            radius = radius,
            scale = scale,
            titleComponent = titleComponent,
            titleWidth = titleWidth,
            lines = lines,
            lineHeight = lineHeight
        )
    }

    private fun previewLines(): List<SidebarLine> {
        return listOf(
            SidebarLine(ModText.literal("03/26/26 m3mini"), 6),
            SidebarLine(ModText.literal("Early Spring 8th"), 5),
            SidebarLine(ModText.literal("3:40am ☀"), 4),
            SidebarLine(ModText.literal("Village"), 3),
            SidebarLine(ModText.literal("Purse: 55,681,414"), 2),
            SidebarLine(ModText.literal("Bits: 668"), 1)
        )
    }

    private fun getSidebarObjective(scoreboard: Scoreboard, client: Minecraft): Objective? {
        val player = client.player ?: return scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR)

        val playerTeam = scoreboard.getPlayersTeam(player.scoreboardName)
        if (playerTeam != null) {
            val teamSlot = DisplaySlot.teamColorToSlot(playerTeam.color)
            if (teamSlot != null) {
                val teamObjective = scoreboard.getDisplayObjective(teamSlot)
                if (teamObjective != null) return teamObjective
            }
        }

        return scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR)
    }

    private fun collectLines(scoreboard: Scoreboard, objective: Objective): List<SidebarLine> {
        return scoreboard.listPlayerScores(objective)
            .asSequence()
            .filter { !it.isHidden }
            .sortedWith(
                compareByDescending<PlayerScoreEntry> { it.value() }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.owner() }
            )
            .take(15)
            .map { entry ->
                val team = scoreboard.getPlayersTeam(entry.owner())
                val name = PlayerTeam.formatNameForTeam(team, entry.ownerName())
                SidebarLine(name, entry.value())
            }
            .filter { stripControl(it.text.string).isNotBlank() }
            .toList()
    }

    private fun maybeReplaceBottomLine(lines: List<SidebarLine>): List<SidebarLine> {
        if (!CleanScoreboardModule.showFooter.value || lines.isEmpty()) return lines

        val replaced = lines.toMutableList()
        val lastIndex = replaced.lastIndex

        replaced[lastIndex] = SidebarLine(
            text = LarpBranding.brandWord(),
            scoreValue = replaced[lastIndex].scoreValue
        )

        return replaced
    }

    private fun stripControl(input: String): String {
        val out = StringBuilder(input.length)
        var i = 0
        while (i < input.length) {
            val c = input[i]
            if (c == '§' && i + 1 < input.length) {
                i += 2
                continue
            }
            out.append(c)
            i++
        }
        return out.toString()
    }

    private fun withAlpha(color: Int, alpha: Int): Int {
        return ((alpha and 0xFF) shl 24) or (color and 0x00FFFFFF)
    }

    private fun drawGradientBorder(
        context: GuiGraphicsExtractor,
        x: Float,
        y: Float,
        w: Float,
        h: Float,
        radius: Float,
        startColor: Int,
        endColor: Int
    ) {
        val thickness = CleanScoreboardModule.borderThickness.value.coerceAtLeast(1.0).toFloat()
        val speed = CleanScoreboardModule.borderAnimSpeed.value.toFloat()

        val xi = x.roundToInt()
        val yi = y.roundToInt()
        val wi = w.roundToInt().coerceAtLeast(4)
        val hi = h.roundToInt().coerceAtLeast(4)

        val outerRadius = radius.coerceAtLeast(0f).coerceAtMost(minOf(w, h) / 2f)
        val innerRadius = (outerRadius - thickness).coerceAtLeast(0f)

        val straightW = (w - 2f * outerRadius).coerceAtLeast(0f)
        val straightH = (h - 2f * outerRadius).coerceAtLeast(0f)
        val arcLen = (PI.toFloat() * outerRadius) / 2f
        val perimeter = (straightW * 2f + straightH * 2f + arcLen * 4f).coerceAtLeast(1f)

        val time = System.nanoTime() / 1_000_000_000.0
        val offset = if (CleanScoreboardModule.animateBorder.value) {
            ((time * speed * 120.0) % perimeter.toDouble()).toFloat()
        } else {
            0f
        }

        fun wrapDistance(value: Float): Float {
            var v = value % perimeter
            if (v < 0f) v += perimeter
            return v
        }

        fun colorAt(distance: Float): Int {
            val half = perimeter * 0.5f
            val d = wrapDistance(distance - offset)
            val t = if (d <= half) d / half else 1f - ((d - half) / half)
            return mixColor(startColor, endColor, t.coerceIn(0f, 1f))
        }

        for (py in yi until yi + hi) {
            for (px in xi until xi + wi) {
                val coverage = borderCoverageAA(
                    px = px,
                    py = py,
                    x = x,
                    y = y,
                    w = w,
                    h = h,
                    outerRadius = outerRadius,
                    thickness = thickness,
                    innerRadius = innerRadius
                )

                if (coverage <= 0f) continue

                val sampleX = px + 0.5f
                val sampleY = py + 0.5f
                val distance = roundedPerimeterPosition(sampleX, sampleY, x, y, w, h, outerRadius)
                val color = colorAt(distance)

                val baseAlpha = (color ushr 24) and 0xFF
                val outAlpha = (baseAlpha * coverage).roundToInt().coerceIn(0, 255)
                if (outAlpha <= 0) continue

                context.fill(px, py, px + 1, py + 1, withAlpha(color, outAlpha))
            }
        }
    }

    private fun borderCoverageAA(
        px: Int,
        py: Int,
        x: Float,
        y: Float,
        w: Float,
        h: Float,
        outerRadius: Float,
        thickness: Float,
        innerRadius: Float
    ): Float {
        val innerX = x + thickness
        val innerY = y + thickness
        val innerW = (w - thickness * 2f).coerceAtLeast(0f)
        val innerH = (h - thickness * 2f).coerceAtLeast(0f)

        var hits = 0
        val samples = 4

        for (sy in 0 until samples) {
            for (sx in 0 until samples) {
                val fx = px + (sx + 0.5f) / samples.toFloat()
                val fy = py + (sy + 0.5f) / samples.toFloat()

                val inOuter = containsRoundedRect(fx, fy, x, y, w, h, outerRadius)
                if (!inOuter) continue

                val inInner = if (innerW > 0f && innerH > 0f) {
                    containsRoundedRect(fx, fy, innerX, innerY, innerW, innerH, innerRadius)
                } else {
                    false
                }

                if (!inInner) hits++
            }
        }

        return hits.toFloat() / (samples * samples).toFloat()
    }

    private fun containsRoundedRect(
        px: Float,
        py: Float,
        x: Float,
        y: Float,
        w: Float,
        h: Float,
        radius: Float
    ): Boolean {
        if (w <= 0f || h <= 0f) return false

        val r = radius.coerceAtLeast(0f).coerceAtMost(minOf(w, h) / 2f)

        if (r <= 0f) {
            return px >= x && px <= x + w && py >= y && py <= y + h
        }

        if (px < x || px > x + w || py < y || py > y + h) return false

        val cx = px.coerceIn(x + r, x + w - r)
        val cy = py.coerceIn(y + r, y + h - r)
        val dx = px - cx
        val dy = py - cy

        return dx * dx + dy * dy <= r * r
    }

    private fun roundedPerimeterPosition(
        px: Float,
        py: Float,
        x: Float,
        y: Float,
        w: Float,
        h: Float,
        radius: Float
    ): Float {
        val r = radius.coerceAtLeast(0f).coerceAtMost(minOf(w, h) / 2f)
        val straightW = (w - 2f * r).coerceAtLeast(0f)
        val straightH = (h - 2f * r).coerceAtLeast(0f)
        val arcLen = (PI.toFloat() * r) / 2f

        val left = x
        val right = x + w
        val top = y
        val bottom = y + h

        val innerLeft = left + r
        val innerRight = right - r
        val innerTop = top + r
        val innerBottom = bottom - r

        if (px in innerLeft..innerRight && py <= innerTop) {
            return px - innerLeft
        }

        if (px >= innerRight && py <= innerTop) {
            val cx = innerRight
            val cy = innerTop
            var angle = atan2(py - cy, px - cx)
            if (angle < 0f) angle += (2f * PI).toFloat()
            val local = angle - (1.5f * PI).toFloat()
            return straightW + local.coerceIn(0f, (PI / 2.0).toFloat()) * r
        }

        if (px >= innerRight && py in innerTop..innerBottom) {
            return straightW + arcLen + (py - innerTop)
        }

        if (px >= innerRight && py >= innerBottom) {
            val cx = innerRight
            val cy = innerBottom
            var angle = atan2(py - cy, px - cx)
            if (angle < 0f) angle += (2f * PI).toFloat()
            val local = angle
            return straightW + arcLen + straightH + local.coerceIn(0f, (PI / 2.0).toFloat()) * r
        }

        if (px in innerLeft..innerRight && py >= innerBottom) {
            return straightW + arcLen + straightH + arcLen + (innerRight - px)
        }

        if (px <= innerLeft && py >= innerBottom) {
            val cx = innerLeft
            val cy = innerBottom
            var angle = atan2(py - cy, px - cx)
            if (angle < 0f) angle += (2f * PI).toFloat()
            val local = angle - (PI / 2.0).toFloat()
            return straightW + arcLen + straightH + arcLen + straightW +
                    local.coerceIn(0f, (PI / 2.0).toFloat()) * r
        }

        if (px <= innerLeft && py in innerTop..innerBottom) {
            return straightW + arcLen + straightH + arcLen + straightW + arcLen + (innerBottom - py)
        }

        val cx = innerLeft
        val cy = innerTop
        var angle = atan2(py - cy, px - cx)
        if (angle < 0f) angle += (2f * PI).toFloat()
        val local = angle - PI.toFloat()

        return straightW + arcLen + straightH + arcLen + straightW + arcLen + straightH +
                local.coerceIn(0f, (PI / 2.0).toFloat()) * r
    }

    private fun mixColor(from: Int, to: Int, t: Float): Int {
        val clamped = t.coerceIn(0f, 1f)

        val a1 = (from ushr 24) and 0xFF
        val r1 = (from ushr 16) and 0xFF
        val g1 = (from ushr 8) and 0xFF
        val b1 = from and 0xFF

        val a2 = (to ushr 24) and 0xFF
        val r2 = (to ushr 16) and 0xFF
        val g2 = (to ushr 8) and 0xFF
        val b2 = to and 0xFF

        val a = (a1 + ((a2 - a1) * clamped)).roundToInt()
        val r = (r1 + ((r2 - r1) * clamped)).roundToInt()
        val g = (g1 + ((g2 - g1) * clamped)).roundToInt()
        val b = (b1 + ((b2 - b1) * clamped)).roundToInt()

        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }
}
