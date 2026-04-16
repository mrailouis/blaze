package me.mrai.blaze.chat

import me.mrai.blaze.meta.BlazeMetadata
import me.mrai.blaze.render.gui.BlazeColorPalette
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent

object BlazeChat {
    fun prefix(): MutableComponent {
        val prefix = Component.empty()
        val label = "BLAZE"
        label.forEachIndexed { index, character ->
            prefix.append(
                Component.literal(character.toString()).withColor(
                    mixColor(
                        BlazeColorPalette.TITLE_START,
                        BlazeColorPalette.TITLE_END,
                        if (label.length == 1) 0f else index.toFloat() / (label.length - 1).toFloat()
                    ) and 0x00FFFFFF
                )
            )
        }
        prefix.append(Component.literal(" ${BlazeMetadata.prefixSymbol} ").withColor(BlazeColorPalette.TEXT_MUTED and 0x00FFFFFF))
        return prefix
    }

    fun text(message: String, color: Int = BlazeColorPalette.TEXT_PRIMARY): MutableComponent {
        return Component.literal(message).withColor(color and 0x00FFFFFF)
    }

    fun prefixed(message: Component): MutableComponent {
        return Component.empty().append(prefix()).append(message)
    }

    fun info(source: FabricClientCommandSource, message: String) {
        source.sendFeedback(prefixed(text(message)))
    }

    fun error(source: FabricClientCommandSource, message: String) {
        source.sendError(prefixed(text(message, 0xFFFF6B6B.toInt())))
    }

    private fun mixColor(start: Int, end: Int, progress: Float): Int {
        val clamped = progress.coerceIn(0f, 1f)
        val startRed = start ushr 16 and 0xFF
        val startGreen = start ushr 8 and 0xFF
        val startBlue = start and 0xFF
        val endRed = end ushr 16 and 0xFF
        val endGreen = end ushr 8 and 0xFF
        val endBlue = end and 0xFF

        val red = (startRed + ((endRed - startRed) * clamped)).toInt()
        val green = (startGreen + ((endGreen - startGreen) * clamped)).toInt()
        val blue = (startBlue + ((endBlue - startBlue) * clamped)).toInt()
        return (red shl 16) or (green shl 8) or blue
    }
}
