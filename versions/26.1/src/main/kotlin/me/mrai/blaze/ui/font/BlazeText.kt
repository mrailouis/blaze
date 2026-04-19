package me.mrai.blaze.ui.font

import com.mojang.blaze3d.platform.NativeImage
import java.awt.Color
import java.awt.Font as AwtFont
import java.awt.GradientPaint
import java.awt.RenderingHints
import java.awt.geom.AffineTransform
import java.awt.image.BufferedImage
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.ceil
import kotlin.math.roundToInt
import me.mrai.blaze.meta.BlazeMetadata
import me.mrai.blaze.feature.module.BlazeCategory
import me.mrai.blaze.platform.BlazeIdentifier
import me.mrai.blaze.platform.BlazeIdentifiers
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.client.renderer.texture.DynamicTexture

object BlazeText {
    private const val supersample = 2f
    private const val rasterScaleSteps = 4f
    private const val texturePadding = 2
    private val generatedId = AtomicInteger()
    private val cache = ConcurrentHashMap<TextKey, CachedText>()
    private val fontCache = ConcurrentHashMap<FontKind, AwtFont>()

    fun prewarmClickGui() {
        val warmStrings = buildList {
            add("BLAZE")
            add("Categories")
            add("Edit HUDs")
            add(BlazeMetadata.version)
            add("Toggle")
            add("Hold")
            add("On")
            add("Off")
            add("Listening...")
            add("Left CPS")
            add("Right CPS")
            add("Enabled")
            add("Key input")
            BlazeCategory.entries.forEach { category ->
                add(category.displayName)
                add(category.description)
                category.modules.forEach { module ->
                    add(module.name)
                    add(module.description)
                    module.children.forEach(::add)
                }
            }
        }

        warmStrings.forEach { text ->
            cache.computeIfAbsent(TextKey(text, 0xFFFFFFFF.toInt(), 0xFFFFFFFF.toInt(), FontKind.BODY, rasterScaleLevel(1f)), ::bake)
            cache.computeIfAbsent(TextKey(text, 0xFFFFFFFF.toInt(), 0xFFFFFFFF.toInt(), FontKind.LABEL, rasterScaleLevel(1f)), ::bake)
        }
        cache.computeIfAbsent(TextKey("BLAZE", 0xFFFF4E2F.toInt(), 0xFFFFD54A.toInt(), FontKind.TITLE, rasterScaleLevel(1f)), ::bake)
    }

    fun width(font: Font, text: String, bold: Boolean = false): Int {
        return widthScaled(font, text, 1f, bold)
    }

    fun height(font: Font, text: String, bold: Boolean = false): Int {
        return heightScaled(font, text, 1f, bold)
    }

    fun widthScaled(font: Font, text: String, scale: Float, bold: Boolean = false): Int {
        val kind = if (bold) FontKind.LABEL else FontKind.BODY
        return (cached(text, kind).drawWidth * scale).roundToInt()
    }

    fun heightScaled(font: Font, text: String, scale: Float, bold: Boolean = false): Int {
        val kind = if (bold) FontKind.LABEL else FontKind.BODY
        return (cached(text, kind).drawHeight * scale).roundToInt()
    }

    fun centerOffset(font: Font, text: String, bold: Boolean = false): Float {
        val kind = if (bold) FontKind.LABEL else FontKind.BODY
        val cached = cached(text, kind)
        return ((cached.contentTop + cached.contentBottom + 1) / 2f) / supersample
    }

    fun titleWidth(text: String, scale: Float): Int {
        return (cached(text, FontKind.TITLE).drawWidth * scale).roundToInt()
    }

    fun draw(
        graphics: GuiGraphicsExtractor,
        font: Font,
        text: String,
        x: Int,
        y: Int,
        color: Int,
        bold: Boolean = false,
        shadow: Boolean = false
    ) {
        val kind = if (bold) FontKind.LABEL else FontKind.BODY
        if (shadow) {
            drawInternal(graphics, text, x + 1, y + 1, 0x78000000, 0x78000000, 1f, kind)
        }
        drawInternal(graphics, text, x, y, color, color, 1f, kind)
    }

    fun drawScaled(
        graphics: GuiGraphicsExtractor,
        font: Font,
        text: String,
        x: Int,
        y: Int,
        color: Int,
        scale: Float,
        bold: Boolean = false,
        shadow: Boolean = false
    ) {
        val kind = if (bold) FontKind.LABEL else FontKind.BODY
        if (shadow) {
            drawInternal(graphics, text, x + 1, y + 1, 0x78000000, 0x78000000, scale, kind)
        }
        drawInternal(graphics, text, x, y, color, color, scale, kind)
    }

    fun drawGradientTitle(
        graphics: GuiGraphicsExtractor,
        font: Font,
        text: String,
        x: Int,
        y: Int,
        startColor: Int,
        endColor: Int,
        scale: Float,
        shadow: Boolean = true
    ) {
        if (shadow) {
            drawInternal(graphics, text, x + 1, y + 1, 0x7A000000, 0x33000000, scale, FontKind.TITLE)
        }
        drawInternal(graphics, text, x, y, startColor, endColor, scale, FontKind.TITLE)
    }

    private fun drawInternal(
        graphics: GuiGraphicsExtractor,
        text: String,
        x: Int,
        y: Int,
        startColor: Int,
        endColor: Int,
        scale: Float,
        kind: FontKind
    ) {
        if (text.isEmpty()) return

        val cached = cache.computeIfAbsent(TextKey(text, startColor, endColor, kind, rasterScaleLevel(scale)), ::bake)
        val pose = graphics.pose()
        pose.pushMatrix()
        pose.translate(x.toFloat(), y.toFloat())
        pose.scale(scale / cached.rasterScale, scale / cached.rasterScale)
        graphics.blit(
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

    private fun bake(key: TextKey): CachedText {
        val measureImage = BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB)
        val measureGraphics = measureImage.createGraphics()
        applyHints(measureGraphics)
        measureGraphics.font = fontFor(key.kind)
        val metrics = measureGraphics.fontMetrics
        val textWidth = metrics.stringWidth(key.text).coerceAtLeast(1)
        val textHeight = metrics.height.coerceAtLeast(1)
        val ascent = metrics.ascent
        measureGraphics.dispose()

        val rasterScale = key.rasterScale()
        val bakedWidth = ((textWidth + texturePadding * 2) * rasterScale).toInt().coerceAtLeast(1)
        val bakedHeight = ((textHeight + texturePadding * 2) * rasterScale).toInt().coerceAtLeast(1)

        val image = BufferedImage(bakedWidth, bakedHeight, BufferedImage.TYPE_INT_ARGB)
        val graphics = image.createGraphics()
        applyHints(graphics)
        graphics.transform = AffineTransform.getScaleInstance(rasterScale.toDouble(), rasterScale.toDouble())
        graphics.font = fontFor(key.kind)
        graphics.paint = if (key.startColor == key.endColor) {
            Color(key.startColor, true)
        } else {
            GradientPaint(
                texturePadding.toFloat(),
                0f,
                Color(key.startColor, true),
                (texturePadding + textWidth).toFloat(),
                0f,
                Color(key.endColor, true)
            )
        }
        graphics.drawString(key.text, texturePadding.toFloat(), (texturePadding + ascent).toFloat())
        graphics.dispose()

        val native = NativeImage(NativeImage.Format.RGBA, bakedWidth, bakedHeight, false)
        for (yy in 0 until bakedHeight) {
            for (xx in 0 until bakedWidth) {
                val argb = image.getRGB(xx, yy)
                val alpha = argb ushr 24 and 0xFF
                val red = argb ushr 16 and 0xFF
                val green = argb ushr 8 and 0xFF
                val blue = argb and 0xFF
                val abgr = (alpha shl 24) or (blue shl 16) or (green shl 8) or red
                native.setPixelABGR(xx, yy, abgr)
            }
        }

        val index = generatedId.incrementAndGet()
        val id = BlazeIdentifiers.of("blaze", "generated/text_$index")
        Minecraft.getInstance().textureManager.register(id, DynamicTexture({ "blaze_text_$index" }, native))
        var contentTop = image.height
        var contentBottom = 0
        for (yy in 0 until image.height) {
            var hasContent = false
            for (xx in 0 until image.width) {
                if ((image.getRGB(xx, yy) ushr 24 and 0xFF) > 0) {
                    hasContent = true
                    break
                }
            }
            if (hasContent) {
                contentTop = minOf(contentTop, yy)
                contentBottom = maxOf(contentBottom, yy)
            }
        }
        if (contentTop > contentBottom) {
            contentTop = 0
            contentBottom = image.height - 1
        }

        return CachedText(id, bakedWidth, bakedHeight, textWidth, textHeight, contentTop, contentBottom, rasterScale)
    }

    private fun cached(text: String, kind: FontKind): CachedText {
        return cache.computeIfAbsent(TextKey(text, 0xFFFFFFFF.toInt(), 0xFFFFFFFF.toInt(), kind, rasterScaleLevel(1f)), ::bake)
    }

    private fun rasterScaleLevel(scale: Float): Int {
        return maxOf(rasterScaleSteps.toInt(), ceil(scale.coerceAtLeast(1f) * rasterScaleSteps).toInt())
    }

    private fun fontFor(kind: FontKind): AwtFont {
        return fontCache.computeIfAbsent(kind) { selected ->
            loadFont(selected.size, selected.style)
        }
    }

    private fun loadFont(size: Float, style: Int): AwtFont {
        return runCatching {
            BlazeText::class.java
                .getResourceAsStream("/assets/blaze/font/font/golos_regular.ttf")!!
                .use { input -> AwtFont.createFont(AwtFont.TRUETYPE_FONT, input).deriveFont(style, size) }
        }.getOrElse {
            AwtFont("Dialog", style, size.toInt())
        }
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

    private enum class FontKind(
        val size: Float,
        val style: Int
    ) {
        BODY(11f, AwtFont.PLAIN),
        LABEL(11f, AwtFont.BOLD),
        TITLE(12f, AwtFont.BOLD)
    }

    private data class TextKey(
        val text: String,
        val startColor: Int,
        val endColor: Int,
        val kind: FontKind,
        val rasterLevel: Int
    ) {
        fun rasterScale(): Float = supersample * (rasterLevel / rasterScaleSteps)
    }

    private data class CachedText(
        val texture: BlazeIdentifier,
        val bakedWidth: Int,
        val bakedHeight: Int,
        val drawWidth: Int,
        val drawHeight: Int,
        val contentTop: Int,
        val contentBottom: Int,
        val rasterScale: Float
    )
}
