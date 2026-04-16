package me.mrai.blaze.render.gui

import com.mojang.blaze3d.platform.NativeImage
import java.awt.AlphaComposite
import java.awt.Color
import java.awt.RenderingHints
import java.awt.geom.RoundRectangle2D
import java.awt.image.BufferedImage
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.max
import kotlin.math.roundToInt
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.resources.Identifier

object GuiPrimitives {
    private const val supersample = 5
    private val generatedId = AtomicInteger()
    private val roundedRectCache = ConcurrentHashMap<RoundRectKey, CachedTexture>()
    private val glowRingCache = ConcurrentHashMap<GlowRingKey, CachedTexture>()

    /**
     * 1.26.x larpclient uses a shader-backed rounded rectangle pipeline.
     * On 1.21.11 we approximate that same smooth look with a cached
     * anti-aliased texture path so the corners stay properly rounded instead
     * of stepping across integer scanlines.
     */
    fun fillRoundedRect(graphics: GuiGraphics, rect: GuiRect, radius: Int, color: Int) {
        if (rect.width <= 0 || rect.height <= 0) return

        val clampedRadius = radius.coerceIn(0, minOf(rect.width, rect.height) / 2)
        if (clampedRadius == 0) {
            graphics.fill(rect.x, rect.y, rect.right, rect.bottom, color)
            return
        }

        val cached = roundedRectCache.computeIfAbsent(
            RoundRectKey(rect.width, rect.height, clampedRadius, color),
            ::bakeRoundedRect
        )

        val pose = graphics.pose()
        pose.pushMatrix()
        pose.translate(rect.x.toFloat(), rect.y.toFloat())
        pose.scale(1f / supersample, 1f / supersample)
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

    fun drawRoundedFrame(
        graphics: GuiGraphics,
        rect: GuiRect,
        radius: Int,
        borderColor: Int,
        fillColor: Int
    ) {
        fillRoundedRect(graphics, rect, radius, borderColor)
        fillRoundedRect(graphics, rect.inset(1), (radius - 1).coerceAtLeast(0), fillColor)
    }

    fun drawSoftShadow(
        graphics: GuiGraphics,
        rect: GuiRect,
        radius: Int,
        shadowColor: Int = BlazeColorPalette.SHADOW,
        layers: Int = 6
    ) {
        for (layer in layers downTo 1) {
            val alpha = (shadowColor ushr 24 and 0xFF)
            val scaledAlpha = (alpha * (layer.toFloat() / layers.toFloat()) * 0.65f).roundToInt().coerceIn(0, 255)
            val tinted = (scaledAlpha shl 24) or (shadowColor and 0x00FFFFFF)
            fillRoundedRect(graphics, rect.expand(layer), radius + layer, tinted)
        }
    }

    fun drawGradientShadow(
        graphics: GuiGraphics,
        rect: GuiRect,
        radius: Int,
        innerColor: Int,
        outerColor: Int,
        layers: Int = 10
    ) {
        val cached = glowRingCache.computeIfAbsent(
            GlowRingKey(rect.width, rect.height, radius, innerColor, outerColor, layers),
            ::bakeGlowRing
        )

        val outerRect = rect.expand(layers)
        val pose = graphics.pose()
        pose.pushMatrix()
        pose.translate(outerRect.x.toFloat(), outerRect.y.toFloat())
        pose.scale(1f / supersample, 1f / supersample)
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

    fun mixColor(startColor: Int, endColor: Int, progress: Float): Int {
        val t = progress.coerceIn(0f, 1f)
        val startA = startColor ushr 24 and 0xFF
        val startR = startColor ushr 16 and 0xFF
        val startG = startColor ushr 8 and 0xFF
        val startB = startColor and 0xFF

        val endA = endColor ushr 24 and 0xFF
        val endR = endColor ushr 16 and 0xFF
        val endG = endColor ushr 8 and 0xFF
        val endB = endColor and 0xFF

        val a = startA + ((endA - startA) * t).roundToInt()
        val r = startR + ((endR - startR) * t).roundToInt()
        val g = startG + ((endG - startG) * t).roundToInt()
        val b = startB + ((endB - startB) * t).roundToInt()
        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }

    private fun bakeRoundedRect(key: RoundRectKey): CachedTexture {
        val bakedWidth = max(1, key.width * supersample)
        val bakedHeight = max(1, key.height * supersample)
        val bakedRadius = key.radius * supersample * 2

        val image = BufferedImage(bakedWidth, bakedHeight, BufferedImage.TYPE_INT_ARGB)
        val graphics = image.createGraphics()
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
        graphics.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY)
        graphics.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)
        graphics.color = Color(key.color, true)
        graphics.fill(
            RoundRectangle2D.Float(
                0.5f,
                0.5f,
                bakedWidth.toFloat() - 1f,
                bakedHeight.toFloat() - 1f,
                bakedRadius.toFloat(),
                bakedRadius.toFloat()
            )
        )
        graphics.dispose()

        val native = NativeImage(NativeImage.Format.RGBA, bakedWidth, bakedHeight, false)
        for (y in 0 until bakedHeight) {
            for (x in 0 until bakedWidth) {
                val argb = image.getRGB(x, y)
                val alpha = argb ushr 24 and 0xFF
                val red = argb ushr 16 and 0xFF
                val green = argb ushr 8 and 0xFF
                val blue = argb and 0xFF
                val abgr = (alpha shl 24) or (blue shl 16) or (green shl 8) or red
                native.setPixelABGR(x, y, abgr)
            }
        }

        val index = generatedId.incrementAndGet()
        val textureId = Identifier.fromNamespaceAndPath("blaze", "generated/round_rect_$index")
        Minecraft.getInstance().textureManager.register(textureId, DynamicTexture({ "blaze_round_rect_$index" }, native))
        return CachedTexture(textureId, bakedWidth, bakedHeight)
    }

    private fun bakeGlowRing(key: GlowRingKey): CachedTexture {
        val totalWidth = key.width + key.layers * 2
        val totalHeight = key.height + key.layers * 2
        val bakedWidth = max(1, totalWidth * supersample)
        val bakedHeight = max(1, totalHeight * supersample)

        val image = BufferedImage(bakedWidth, bakedHeight, BufferedImage.TYPE_INT_ARGB)
        val graphics = image.createGraphics()
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
        graphics.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY)
        graphics.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)

        for (layer in key.layers downTo 1) {
            val progress = (layer - 1).toFloat() / key.layers.toFloat()
            val color = mixColor(key.innerColor, key.outerColor, progress)
            graphics.color = Color(color, true)

            val expandedX = (key.layers - layer) * supersample + 0.5f
            val expandedY = (key.layers - layer) * supersample + 0.5f
            val expandedWidth = (key.width + layer * 2) * supersample - 1f
            val expandedHeight = (key.height + layer * 2) * supersample - 1f
            val expandedRadius = (key.radius + layer) * supersample * 2f

            graphics.fill(
                RoundRectangle2D.Float(
                    expandedX,
                    expandedY,
                    expandedWidth,
                    expandedHeight,
                    expandedRadius,
                    expandedRadius
                )
            )
        }

        graphics.composite = AlphaComposite.Clear
        val innerX = key.layers * supersample + 0.5f
        val innerY = key.layers * supersample + 0.5f
        val innerWidth = key.width * supersample - 1f
        val innerHeight = key.height * supersample - 1f
        val innerRadius = key.radius * supersample * 2f
        graphics.fill(
            RoundRectangle2D.Float(
                innerX,
                innerY,
                innerWidth,
                innerHeight,
                innerRadius,
                innerRadius
            )
        )
        graphics.dispose()

        val native = NativeImage(NativeImage.Format.RGBA, bakedWidth, bakedHeight, false)
        for (y in 0 until bakedHeight) {
            for (x in 0 until bakedWidth) {
                val argb = image.getRGB(x, y)
                val alpha = argb ushr 24 and 0xFF
                val red = argb ushr 16 and 0xFF
                val green = argb ushr 8 and 0xFF
                val blue = argb and 0xFF
                val abgr = (alpha shl 24) or (blue shl 16) or (green shl 8) or red
                native.setPixelABGR(x, y, abgr)
            }
        }

        val index = generatedId.incrementAndGet()
        val textureId = Identifier.fromNamespaceAndPath("blaze", "generated/glow_ring_$index")
        Minecraft.getInstance().textureManager.register(textureId, DynamicTexture({ "blaze_glow_ring_$index" }, native))
        return CachedTexture(textureId, bakedWidth, bakedHeight)
    }

    private data class RoundRectKey(
        val width: Int,
        val height: Int,
        val radius: Int,
        val color: Int
    )

    private data class GlowRingKey(
        val width: Int,
        val height: Int,
        val radius: Int,
        val innerColor: Int,
        val outerColor: Int,
        val layers: Int
    )

    private data class CachedTexture(
        val texture: Identifier,
        val bakedWidth: Int,
        val bakedHeight: Int
    )
}

data class GuiRect(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int
) {
    val right: Int get() = x + width
    val bottom: Int get() = y + height

    fun inset(amount: Int): GuiRect {
        val doubled = amount * 2
        return GuiRect(
            x = x + amount,
            y = y + amount,
            width = (width - doubled).coerceAtLeast(0),
            height = (height - doubled).coerceAtLeast(0)
        )
    }

    fun expand(amount: Int): GuiRect {
        val doubled = amount * 2
        return GuiRect(
            x = x - amount,
            y = y - amount,
            width = width + doubled,
            height = height + doubled
        )
    }

    fun contains(mouseX: Double, mouseY: Double): Boolean {
        return mouseX >= x && mouseX <= right && mouseY >= y && mouseY <= bottom
    }
}
