package me.mrai.larpclient.ui.clickgui.render

import net.minecraft.client.gui.GuiGraphicsExtractor
import org.joml.Vector4f

object RoundedRenderer {
    fun endFrame() {
        ShaderRoundRectRenderer.endFrame()
    }

    fun rect(ctx: GuiGraphicsExtractor, x: Float, y: Float, w: Float, h: Float, color: Int) {
        roundedRect(ctx, x, y, w, h, 0f, color, 0, 0f)
    }

    fun roundedRect(
        ctx: GuiGraphicsExtractor,
        x: Float,
        y: Float,
        w: Float,
        h: Float,
        radius: Float,
        fillColor: Int,
        borderColor: Int = 0,
        borderThickness: Float = 0f
    ) {
        val fill = argbToVec4(fillColor)
        val border = argbToVec4(borderColor)

        if (borderThickness > 0f && border.w > 0f) {
            ShaderRoundRectRenderer.draw(
                ctx,
                x,
                y,
                w,
                h,
                radius,
                border,
                border,
                TRANSPARENT,
                borderThickness,
                1f,
                0f
            )
        } else {
            ShaderRoundRectRenderer.draw(
                ctx,
                x,
                y,
                w,
                h,
                radius,
                fill,
                fill,
                TRANSPARENT,
                0f,
                1f,
                0f
            )
        }
    }

    fun shadowedRoundedRect(
        ctx: GuiGraphicsExtractor,
        x: Float,
        y: Float,
        w: Float,
        h: Float,
        radius: Float,
        fillColor: Int,
        borderColor: Int = 0,
        borderThickness: Float = 0f,
        shadowColor: Int = 0,
        shadowSize: Float = 0f,
        shadowOffsetX: Float = 0f,
        shadowOffsetY: Float = 0f
    ) {
        val fill = argbToVec4(fillColor)
        val border = argbToVec4(borderColor)
        val shadow = argbToVec4(shadowColor)

        ShaderRoundRectRenderer.draw(
            ctx,
            x + shadowOffsetX,
            y + shadowOffsetY,
            w,
            h,
            radius,
            fill,
            fill,
            shadow,
            0f,
            1f,
            shadowSize
        )

        if (borderThickness > 0f && border.w > 0f) {
            ShaderRoundRectRenderer.draw(
                ctx,
                x,
                y,
                w,
                h,
                radius,
                border,
                border,
                TRANSPARENT,
                borderThickness,
                1f,
                0f
            )
        }
    }

    fun circle(ctx: GuiGraphicsExtractor, cx: Float, cy: Float, radius: Float, color: Int) {
        val size = kotlin.math.max(1f, radius * 2f)
        roundedRect(ctx, cx - radius, cy - radius, size, size, radius, color, 0, 0f)
    }

    private fun argbToVec4(argb: Int): Vector4f {
        val a = (argb ushr 24 and 0xFF) / 255f
        val r = (argb ushr 16 and 0xFF) / 255f
        val g = (argb ushr 8 and 0xFF) / 255f
        val b = (argb and 0xFF) / 255f
        return Vector4f(r, g, b, a)
    }

    private val TRANSPARENT = Vector4f(0f, 0f, 0f, 0f)
}
