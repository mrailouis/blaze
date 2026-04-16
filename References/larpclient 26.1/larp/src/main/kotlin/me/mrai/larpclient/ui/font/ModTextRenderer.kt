package me.mrai.larpclient.ui.font

import com.mojang.blaze3d.platform.NativeImage
import me.mrai.larpclient.ui.clickgui.config.ClickGuiConfigManager
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import net.minecraft.resources.Identifier
import java.awt.Color
import java.awt.Font
import java.awt.RenderingHints
import java.awt.geom.AffineTransform
import java.awt.image.BufferedImage
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt

object ModTextRenderer {
    private const val supersample = 2f
    private const val texturePadding = 2

    private enum class FontKind(val size: Float) {
        NORMAL(11f),
        TITLE(12f),
        SMALL(10f)
    }

    private data class TextKey(val text: String, val color: Int, val bold: Boolean, val kind: FontKind)
    private data class StyledSegment(val text: String, val bold: Boolean)
    private data class CachedText(
        val texture: Identifier,
        val bakedWidth: Int,
        val bakedHeight: Int,
        val drawWidth: Int,
        val drawHeight: Int,
        val contentTop: Int,
        val contentBottom: Int
    )

    private val cache = ConcurrentHashMap<TextKey, CachedText>()
    private val fontCache = ConcurrentHashMap<Pair<Boolean, FontKind>, Font>()
    private var nextId = 0

    fun drawText(
        context: GuiGraphicsExtractor,
        text: String,
        x: Float,
        y: Float,
        color: Int,
        shadow: Boolean = false
    ) {
        drawScaledText(context, text, x, y, color, 1f, shadow)
    }

    fun drawTextCustom(
        context: GuiGraphicsExtractor,
        text: String,
        x: Float,
        y: Float,
        color: Int,
        shadow: Boolean = false
    ) {
        drawScaledTextCustom(context, text, x, y, color, 1f, shadow)
    }

    fun drawScaledText(
        context: GuiGraphicsExtractor,
        text: String,
        x: Float,
        y: Float,
        color: Int,
        scale: Float = 1f,
        shadow: Boolean = false
    ) {
        if (text.isEmpty()) return

        drawScaledTextCustom(context, text, x, y, color, scale, shadow)
    }

    fun drawScaledTextCustom(
        context: GuiGraphicsExtractor,
        text: String,
        x: Float,
        y: Float,
        color: Int,
        scale: Float = 1f,
        shadow: Boolean = false
    ) {
        if (text.isEmpty()) return

        if (!shadow && ClickGuiConfigManager.config.worldGlow) {
            drawGlow(context, text, x, y, color, scale)
        }

        if (shadow) {
            drawScaledTextCustom(
                context = context,
                text = text,
                x = x + scale,
                y = y + scale,
                color = (color and 0x00FFFFFF) or 0x55000000,
                scale = scale,
                shadow = false
            )
        }

        var drawX = x
        for (segment in parseSegments(text)) {
            if (segment.text.isEmpty()) continue
            drawSegment(context, segment, drawX, y, color, scale)
            drawX += measure(segment.text, segment.bold, fontKindFor(scale)).width.toFloat()
        }
    }

    fun width(text: String, scale: Float = 1f): Int {
        return widthCustom(text, scale)
    }

    fun widthCustom(text: String, scale: Float = 1f): Int {
        val kind = fontKindFor(scale)
        val rawWidth = parseSegments(text).sumOf { measure(it.text, it.bold, kind).width }
        return rawWidth
    }

    fun lineHeight(scale: Float = 1f, extraSpacing: Float = 0f): Int {
        return lineHeightCustom(scale, extraSpacing)
    }

    fun lineHeightCustom(scale: Float = 1f, extraSpacing: Float = 0f): Int {
        return ((textHeightRaw(fontKindFor(scale))) + extraSpacing).roundToInt()
    }

    fun renderedLineHeightCustom(scale: Float = 1f, extraSpacing: Float = 0f): Int {
        return ((textHeightRaw(fontKindFor(scale)) + texturePadding * 2) + extraSpacing).roundToInt()
    }

    fun renderedTextCenterOffsetCustom(text: String, color: Int, scale: Float = 1f, bold: Boolean = false): Float {
        if (text.isEmpty()) return 0f
        val kind = fontKindFor(scale)
        val cached = cache.computeIfAbsent(TextKey(text, color, bold, kind), ::bake)
        return ((cached.contentTop + cached.contentBottom + 1) / 2f) / supersample
    }

    fun wrapWords(
        text: String,
        maxWidth: Int,
        scale: Float = 1f
    ): List<String> {
        if (text.isBlank()) return emptyList()

        val words = text.split(Regex("\\s+"))
        val lines = mutableListOf<String>()
        var current = ""

        for (word in words) {
            val candidate = if (current.isEmpty()) word else "$current $word"
            if (width(candidate, scale) <= maxWidth) {
                current = candidate
            } else if (current.isNotEmpty()) {
                lines += current
                current = word
            } else {
                lines += splitLongWord(word, maxWidth, scale)
                current = ""
            }
        }

        if (current.isNotEmpty()) {
            lines += current
        }

        return lines
    }

    fun drawWrappedText(
        context: GuiGraphicsExtractor,
        text: String,
        x: Float,
        y: Float,
        maxWidth: Int,
        color: Int,
        scale: Float = 1f,
        lineSpacing: Float = 2f,
        shadow: Boolean = false
    ): Int {
        val lines = wrapWords(text, maxWidth, scale)
        var drawY = y

        for (line in lines) {
            drawScaledText(context, line, x, drawY, color, scale, shadow)
            drawY += lineHeight(scale, lineSpacing)
        }

        return (drawY - y).roundToInt()
    }

    private fun splitLongWord(
        word: String,
        maxWidth: Int,
        scale: Float
    ): List<String> {
        val out = mutableListOf<String>()
        var current = ""

        for (ch in word) {
            val candidate = current + ch
            if (width(candidate, scale) <= maxWidth) {
                current = candidate
            } else {
                if (current.isNotEmpty()) out += current
                current = ch.toString()
            }
        }

        if (current.isNotEmpty()) out += current
        return out
    }

    private fun parseSegments(text: String): List<StyledSegment> {
        if ('§' !in text) return listOf(StyledSegment(text, false))

        val segments = mutableListOf<StyledSegment>()
        val current = StringBuilder()
        var bold = false
        var index = 0

        while (index < text.length) {
            val char = text[index]
            if (char == '§' && index + 1 < text.length) {
                if (current.isNotEmpty()) {
                    segments += StyledSegment(current.toString(), bold)
                    current.clear()
                }

                when (text[index + 1].lowercaseChar()) {
                    'l' -> bold = true
                    'r', '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
                    'a', 'b', 'c', 'd', 'e', 'f' -> bold = false
                }
                index += 2
                continue
            }

            current.append(char)
            index++
        }

        if (current.isNotEmpty()) {
            segments += StyledSegment(current.toString(), bold)
        }

        return if (segments.isEmpty()) listOf(StyledSegment("", false)) else segments
    }

    private fun drawGlow(
        context: GuiGraphicsExtractor,
        text: String,
        x: Float,
        y: Float,
        color: Int,
        scale: Float
    ) {
        val innerColor = withAlpha(color, 0.20f)
        val outerColor = withAlpha(color, 0.08f)
        val inner = 0.6f * scale
        val outer = 1.15f * scale
        drawSegments(context, text, x + inner, y, innerColor, scale)
        drawSegments(context, text, x - inner, y, innerColor, scale)
        drawSegments(context, text, x, y + inner, innerColor, scale)
        drawSegments(context, text, x, y - inner, innerColor, scale)
        drawSegments(context, text, x + outer, y, outerColor, scale)
        drawSegments(context, text, x - outer, y, outerColor, scale)
        drawSegments(context, text, x, y + outer, outerColor, scale)
        drawSegments(context, text, x, y - outer, outerColor, scale)
    }

    private fun drawSegments(
        context: GuiGraphicsExtractor,
        text: String,
        x: Float,
        y: Float,
        color: Int,
        scale: Float
    ) {
        val kind = fontKindFor(scale)
        var drawX = x
        for (segment in parseSegments(text)) {
            if (segment.text.isEmpty()) continue
            drawSegment(context, segment, drawX, y, color, scale)
            drawX += measure(segment.text, segment.bold, kind).width.toFloat()
        }
    }

    private fun drawSegment(
        context: GuiGraphicsExtractor,
        segment: StyledSegment,
        x: Float,
        y: Float,
        color: Int,
        scale: Float
    ) {
        val fontKind = fontKindFor(scale)
        val cached = cache.computeIfAbsent(
            TextKey(segment.text, color, segment.bold, fontKind),
            ::bake
        )

        val pose = context.pose()
        pose.pushMatrix()
        pose.translate(x, y)
        pose.scale(1f / supersample, 1f / supersample)
        context.blit(
            RenderPipelines.GUI_TEXTURED,
            cached.texture,
            0,
            0,
            0f,
            0f,
            cached.bakedWidth,
            cached.bakedHeight,
            cached.bakedWidth,
            cached.bakedHeight
        )
        pose.popMatrix()
    }

    private fun loadFont(size: Float, style: Int): Font {
        return runCatching {
            ModTextRenderer::class.java
                .getResourceAsStream("/assets/larpclient/font/font/golos_regular.ttf")!!
                .use { input -> Font.createFont(Font.TRUETYPE_FONT, input).deriveFont(style, size) }
        }.getOrElse {
            Font("Dialog", style, size.toInt())
        }
    }

    private fun fontKindFor(scale: Float): FontKind {
        return when {
            scale >= 1.0f -> FontKind.TITLE
            scale <= 0.8f -> FontKind.SMALL
            else -> FontKind.NORMAL
        }
    }

    private fun fontFor(bold: Boolean, kind: FontKind): Font {
        return fontCache.computeIfAbsent(bold to kind) { (isBold, selectedKind) ->
            loadFont(selectedKind.size, if (isBold) Font.BOLD else Font.PLAIN)
        }
    }

    private fun textHeightRaw(kind: FontKind): Int {
        return measure("Ay", bold = false, kind = kind).height
    }

    private fun measure(text: String, bold: Boolean, kind: FontKind): java.awt.Dimension {
        val tmp = BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB)
        val g = tmp.createGraphics()
        g.font = fontFor(bold, kind)
        applyHints(g)
        val fm = g.fontMetrics
        val width = fm.stringWidth(text).coerceAtLeast(1)
        val height = fm.height.coerceAtLeast(1)
        g.dispose()
        return java.awt.Dimension(width, height)
    }

    private fun bake(key: TextKey): CachedText {
        val padding = texturePadding

        val tmp = BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB)
        val g0 = tmp.createGraphics()
        g0.font = fontFor(key.bold, key.kind)
        applyHints(g0)
        val fm = g0.fontMetrics
        val textWidth = fm.stringWidth(key.text).coerceAtLeast(1)
        val textHeight = fm.height.coerceAtLeast(1)
        val ascent = fm.ascent
        g0.dispose()

        val bakedWidth = ((textWidth + padding * 2) * supersample).toInt().coerceAtLeast(1)
        val bakedHeight = ((textHeight + padding * 2) * supersample).toInt().coerceAtLeast(1)

        val image = BufferedImage(bakedWidth, bakedHeight, BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()
        applyHints(g)
        g.transform = AffineTransform.getScaleInstance(supersample.toDouble(), supersample.toDouble())
        g.font = fontFor(key.bold, key.kind)

        val alpha = (key.color ushr 24) and 0xFF
        val red = (key.color ushr 16) and 0xFF
        val green = (key.color ushr 8) and 0xFF
        val blue = key.color and 0xFF
        g.color = Color(red, green, blue, alpha)
        g.drawString(key.text, padding.toFloat(), (padding + ascent).toFloat())
        g.dispose()

        val native = NativeImage(NativeImage.Format.RGBA, image.width, image.height, false)
        var contentTop = image.height
        var contentBottom = 0
        for (y in 0 until image.height) {
            var rowHasContent = false
            for (x in 0 until image.width) {
                val argb = image.getRGB(x, y)
                val aa = (argb ushr 24) and 0xFF
                val rr = (argb ushr 16) and 0xFF
                val gg = (argb ushr 8) and 0xFF
                val bb = argb and 0xFF
                if (aa > 0) {
                    rowHasContent = true
                }
                val abgr = (aa shl 24) or (bb shl 16) or (gg shl 8) or rr
                native.setPixelABGR(x, y, abgr)
            }
            if (rowHasContent) {
                contentTop = minOf(contentTop, y)
                contentBottom = maxOf(contentBottom, y)
            }
        }
        if (contentTop > contentBottom) {
            contentTop = 0
            contentBottom = image.height - 1
        }

        val name = "font_${nextId++}"
        val id = Identifier.fromNamespaceAndPath("larpclient", "generated/$name")
        val texture = DynamicTexture({ "larpclient_$name" }, native)
        Minecraft.getInstance().textureManager.register(id, texture)

        return CachedText(
            texture = id,
            bakedWidth = bakedWidth,
            bakedHeight = bakedHeight,
            drawWidth = textWidth,
            drawHeight = textHeight,
            contentTop = contentTop,
            contentBottom = contentBottom
        )
    }

    private fun drawVanillaScaledText(
        context: GuiGraphicsExtractor,
        text: String,
        x: Float,
        y: Float,
        color: Int,
        scale: Float,
        shadow: Boolean
    ) {
        val font = Minecraft.getInstance().font
        val component = vanillaComponent(text)

        if (scale == 1f) {
            context.text(font, component, x.toInt(), y.toInt(), color, shadow)
            return
        }

        val pose = context.pose()
        pose.pushMatrix()
        pose.translate(x, y)
        pose.scale(scale, scale)
        context.text(font, component, 0, 0, color, shadow)
        pose.popMatrix()
    }

    private fun vanillaComponent(text: String): Component {
        val segments = parseSegments(text)
        if (segments.isEmpty()) return Component.literal("")

        val root = Component.empty()
        for (segment in segments) {
            if (segment.text.isEmpty()) continue
            root.append(
                if (segment.bold) Component.literal(segment.text).withStyle(Style.EMPTY.withBold(true))
                else Component.literal(segment.text)
            )
        }
        return root
    }

    private fun stripFormatting(text: String): String {
        val out = StringBuilder(text.length)
        var index = 0
        while (index < text.length) {
            val char = text[index]
            if (char == '§' && index + 1 < text.length) {
                index += 2
                continue
            }
            out.append(char)
            index++
        }
        return out.toString()
    }

    private fun applyHints(graphics: java.awt.Graphics2D) {
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
        graphics.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY)
        graphics.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
        graphics.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON)
    }

    private fun withAlpha(color: Int, multiplier: Float): Int {
        val alpha = (color ushr 24) and 0xFF
        val scaled = (alpha * multiplier).roundToInt().coerceIn(0, 255)
        return (color and 0x00FFFFFF) or (scaled shl 24)
    }
}
