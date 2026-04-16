package me.mrai.larpclient.ui.toast

import me.mrai.larpclient.ui.clickgui.config.ClickGuiConfigManager
import me.mrai.larpclient.ui.clickgui.config.ClickGuiColorConfigManager
import me.mrai.larpclient.ui.clickgui.render.RoundedRenderer
import me.mrai.larpclient.ui.font.ModTextRenderer
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import kotlin.math.max
import kotlin.math.roundToInt

object ToastManager {
    private data class ToastEntry(
        val title: String,
        val message: String,
        val createdAt: Long = System.currentTimeMillis(),
        val durationMs: Long = 2600L
    )

    private val toasts = mutableListOf<ToastEntry>()

    private const val ENTER_MS = 180f
    private const val EXIT_MS = 220f
    private const val GAP = 6.5f
    private const val MIN_WIDTH = 143f
    private const val MAX_WIDTH = 221f
    private const val HEIGHT = 34f
    private const val MAX_TOASTS = 4
    private const val TITLE_SCALE = 0.72f
    private const val MESSAGE_SCALE = 0.64f
    private const val TEXT_GAP = 2f
    private const val TEXT_LEFT_PAD = 10f

    fun show(title: String, message: String) {
        if (!ClickGuiConfigManager.config.showToasts) return

        toasts += ToastEntry(title, message)
        if (toasts.size > MAX_TOASTS) {
            toasts.removeAt(0)
        }
    }

    fun render(context: GuiGraphicsExtractor) {
        if (!ClickGuiConfigManager.config.showToasts) {
            toasts.clear()
            return
        }

        val client = Minecraft.getInstance()
        val screenW = client.window.guiScaledWidth.toFloat()
        val screenH = client.window.guiScaledHeight.toFloat()
        val colors = ClickGuiColorConfigManager.config

        val now = System.currentTimeMillis()
        val it = toasts.iterator()
        val visible = mutableListOf<ToastEntry>()

        while (it.hasNext()) {
            val toast = it.next()
            if (now - toast.createdAt >= toast.durationMs) {
                it.remove()
            } else {
                visible += toast
            }
        }

        if (visible.isEmpty()) return

        var stackOffset = 0f

        visible.forEach { toast ->
            val age = (now - toast.createdAt).toFloat()
            val enterT = (age / ENTER_MS).coerceIn(0f, 1f)
            val exitStart = toast.durationMs - EXIT_MS
            val exitT = if (age >= exitStart) {
                1f - ((age - exitStart) / EXIT_MS).coerceIn(0f, 1f)
            } else {
                1f
            }

            val anim = easeOutCubic(enterT) * exitT
            val alphaMul = anim.coerceIn(0f, 1f)

            val w = toastWidth(toast)
            val h = HEIGHT
            val visibleX = screenW - w - 10f
            val hiddenX = screenW + 18f
            val x = lerp(hiddenX, visibleX, anim)
            val y = screenH - 12f - h - stackOffset

            val backgroundColor = scaleAlpha(colors.backgroundColor, colors.backgroundAlpha * alphaMul)
            val accentColor = scaleAlpha(colors.moduleEnabledColor, alphaMul)
            val borderColor = scaleAlpha(colors.moduleEnabledColor, alphaMul)
            val glowColor = scaleAlpha(colors.moduleEnabledColor, 0.38f * alphaMul)

            RoundedRenderer.shadowedRoundedRect(
                context,
                x,
                y,
                w,
                h,
                12f,
                backgroundColor,
                borderColor = borderColor,
                borderThickness = 1.2f,
                shadowColor = glowColor,
                shadowSize = 18f
            )

            val title = "§l${toast.title}"
            val titleWidth = ModTextRenderer.widthCustom(title, TITLE_SCALE).toFloat()
            val titleHeight = ModTextRenderer.lineHeightCustom(TITLE_SCALE).toFloat()
            val messageHeight = ModTextRenderer.lineHeightCustom(MESSAGE_SCALE).toFloat()
            val blockHeight = titleHeight + TEXT_GAP + messageHeight
            val titleX = x + TEXT_LEFT_PAD
            val messageX = x + TEXT_LEFT_PAD
            val titleY = y + (h - blockHeight) / 2f
            val messageY = titleY + titleHeight + TEXT_GAP

            text(
                context = context,
                value = title,
                x = titleX,
                y = titleY,
                color = accentColor,
                scale = TITLE_SCALE
            )
            text(
                context = context,
                value = toast.message,
                x = messageX,
                y = messageY,
                color = accentColor,
                scale = MESSAGE_SCALE
            )

            stackOffset += h + GAP
        }
    }

    private fun text(
        context: GuiGraphicsExtractor,
        value: String,
        x: Float,
        y: Float,
        color: Int,
        scale: Float
    ) {
        ModTextRenderer.drawScaledTextCustom(context, value, x, y, color, scale, false)
    }

    private fun toastWidth(toast: ToastEntry): Float {
        val titleWidth = ModTextRenderer.widthCustom("§l${toast.title}", TITLE_SCALE).toFloat()
        val messageWidth = ModTextRenderer.widthCustom(toast.message, MESSAGE_SCALE).toFloat()
        return (max(titleWidth, messageWidth) + 20f).coerceIn(MIN_WIDTH, MAX_WIDTH)
    }

    private fun scaleAlpha(color: Int, alphaScale: Float): Int {
        val scaled = (((color ushr 24) and 0xFF) * alphaScale.coerceIn(0f, 1f)).roundToInt().coerceIn(0, 255)
        return (scaled shl 24) or (color and 0x00FFFFFF)
    }

    private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

    private fun easeOutCubic(t: Float): Float {
        val c = t.coerceIn(0f, 1f)
        val inv = 1f - c
        return 1f - inv * inv * inv
    }
}
