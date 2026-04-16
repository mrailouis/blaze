package me.mrai.larpclient.features.impl.dungeons.f7.general.termgui

import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import org.lwjgl.glfw.GLFW
import kotlin.math.roundToInt

class TermGuiScreen : Screen(Component.literal("Term GUI")) {
    private data class SlotBox(
        val slot: Int,
        val x: Int,
        val y: Int,
        val size: Int
    ) {
        fun contains(mouseX: Double, mouseY: Double): Boolean {
            return mouseX >= x && mouseX <= x + size && mouseY >= y && mouseY <= y + size
        }
    }

    private val slotBoxes = mutableListOf<SlotBox>()

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        super.extractRenderState(graphics, mouseX, mouseY, delta)

        val view = TermGuiModule.buildView() ?: run {
            minecraft.setScreen(null)
            return
        }

        val layout = TermGuiModule.layout(width, height, view.columns, view.rows)
        val showSlots = TermGuiModule.showSlots()

        graphics.fill(0, 0, width, height, TermGuiModule.dimColor())
        graphics.fill(
            layout.originX,
            layout.originY,
            layout.originX + layout.totalWidth,
            layout.originY + layout.totalHeight,
            TermGuiModule.frameColor()
        )
        graphics.fill(
            layout.originX + 2,
            layout.originY + 2,
            layout.originX + layout.totalWidth - 2,
            layout.originY + layout.totalHeight - 2,
            TermGuiModule.backgroundColor()
        )

        val headerY = layout.originY + (4f * layout.scale).roundToInt()
        val leftX = layout.originX + (6f * layout.scale).roundToInt()
        val rightWidth = (minecraft.font.width(view.brandText) * layout.scale).roundToInt()
        val rightX = layout.originX + layout.totalWidth - (6f * layout.scale).roundToInt() - rightWidth

        drawScaledPlainText(graphics, view.terminalName, leftX.toFloat(), headerY.toFloat(), layout.scale, TermGuiModule.promptColor())
        drawScaledComponent(graphics, view.brandText, rightX.toFloat(), headerY.toFloat(), layout.scale)
        val queuedText = "Queued: ${view.queuedCount}"
        val queuedWidth = (minecraft.font.width(queuedText) * layout.scale).roundToInt().toFloat()
        val queuedX = layout.originX + (layout.totalWidth - queuedWidth) / 2f
        drawScaledPlainText(graphics, queuedText, queuedX, headerY.toFloat(), layout.scale, 0xFFFFFFFF.toInt())

        slotBoxes.clear()
        var hoveredAction: Pair<Int, Int>? = null
        view.visibleSlots.forEachIndexed { index, slot ->
            val col = index % view.columns
            val row = index / view.columns
            val x = layout.contentLeft + col * layout.slotSize
            val y = layout.contentTop + row * layout.slotSize

            slotBoxes += SlotBox(slot, x, y, layout.slotSize)

            if (showSlots) {
                graphics.fill(x, y, x + layout.slotSize - 1, y + layout.slotSize - 1, TermGuiModule.slotColor())
                graphics.outline(x, y, layout.slotSize - 1, layout.slotSize - 1, TermGuiModule.slotBorderColor())
            }

            val overlay = view.overlays[slot] ?: return@forEachIndexed
            overlay.fillColor?.let { fill ->
                val inset = if (showSlots) 1 else 0
                graphics.fill(
                    x + inset,
                    y + inset,
                    x + layout.slotSize - 1 - inset,
                    y + layout.slotSize - 1 - inset,
                    fill
                )
            }
            overlay.outlineColor?.let { outline ->
                val inset = if (showSlots) 1 else 0
                graphics.outline(
                    x + inset,
                    y + inset,
                    layout.slotSize - 1 - inset * 2,
                    layout.slotSize - 1 - inset * 2,
                    outline
                )
            }

            overlay.label?.let { label ->
                val drawScale = overlay.labelScale * layout.scale
                val textWidth = (minecraft.font.width(label) * drawScale).roundToInt().toFloat()
                val textY = y + (layout.slotSize - (minecraft.font.lineHeight * drawScale).roundToInt().coerceAtLeast(8)) / 2f
                val textX = x + (layout.slotSize - textWidth) / 2f
                drawScaledPlainText(graphics, label, textX, textY, drawScale, overlay.labelColor)
            }

            if (slotBoxes.last().contains(mouseX.toDouble(), mouseY.toDouble())) {
                hoveredAction = view.clicks[slot]?.let { slot to it }
            }
        }

        if (TermGuiModule.hoverTermsEnabled()) {
            hoveredAction?.let { (slot, button) -> TermGuiModule.onHoverSlot(slot, button) } ?: TermGuiModule.clearHoverState()
        } else {
            TermGuiModule.clearHoverState()
        }
    }

    override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        val view = TermGuiModule.buildView() ?: return true
        val button = when (event.button()) {
            0 -> 0
            1 -> 1
            else -> return true
        }

        val hit = slotBoxes.firstOrNull { it.contains(event.x(), event.y()) } ?: return true
        val expected = view.clicks[hit.slot] ?: return true
        if (expected != button) return true

        TermGuiModule.clickSlot(hit.slot, button)
        return true
    }

    override fun keyPressed(event: KeyEvent): Boolean {
        if (event.key() == GLFW.GLFW_KEY_ESCAPE || minecraft.options.keyInventory.matches(event)) {
            onClose()
            minecraft.setScreen(null)
            return true
        }
        return true
    }

    override fun onClose() {
        TermGuiModule.closeActiveFromUser()
        super.onClose()
    }

    override fun shouldCloseOnEsc(): Boolean = false

    override fun isPauseScreen(): Boolean = false

    private fun drawScaledComponent(
        graphics: GuiGraphicsExtractor,
        component: Component,
        x: Float,
        y: Float,
        scale: Float
    ) {
        val pose = graphics.pose()
        pose.pushMatrix()
        pose.translate(x, y)
        pose.scale(scale, scale)
        graphics.text(minecraft.font, component, 0, 0, 0xFFFFFFFF.toInt(), false)
        pose.popMatrix()
    }

    private fun drawScaledPlainText(
        graphics: GuiGraphicsExtractor,
        text: String,
        x: Float,
        y: Float,
        scale: Float,
        color: Int
    ) {
        val pose = graphics.pose()
        pose.pushMatrix()
        pose.translate(x, y)
        pose.scale(scale, scale)
        graphics.text(minecraft.font, Component.literal(text), 0, 0, color, false)
        pose.popMatrix()
    }
}
