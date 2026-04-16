package me.mrai.larpclient.ui.clickgui.render

import me.mrai.larpclient.ui.font.ModTextRenderer
import net.minecraft.client.gui.GuiGraphicsExtractor
import kotlin.math.max

object SettingFieldRenderer {

    fun drawPillField(
        context: GuiGraphicsExtractor,
        text: String,
        x: Float,
        y: Float,
        minWidth: Float,
        height: Float,
        bgColor: Int,
        outlineColor: Int,
        textColor: Int,
        textScale: Float = 1f
    ): Float {
        val textWidth = ModTextRenderer.width(text, textScale).toFloat()
        val fieldWidth = max(minWidth, textWidth + 16f)

        RoundedRenderer.roundedRect(
            context,
            x,
            y,
            fieldWidth,
            height,
            height / 2f,
            bgColor,
            outlineColor,
            1f
        )

        val textX = x + (fieldWidth - textWidth) / 2f
        val textY = y + (height - (ModTextRenderer.lineHeight(textScale).toFloat())) / 2f

        ModTextRenderer.drawScaledText(
            context = context,
            text = text,
            x = textX,
            y = textY,
            color = textColor,
            scale = textScale,
            shadow = false
        )

        return fieldWidth
    }
}