package me.mrai.larpclient.ui.hud

import me.mrai.larpclient.features.impl.dungeons.f7.general.archerutils.ArcherUtilsModule
import me.mrai.larpclient.ui.font.HudTextRenderer
import net.minecraft.client.gui.GuiGraphicsExtractor

object ArcherUtilsHudRenderer {
    private const val DISPLAY_TICKS = 50
    private const val PREVIEW_COLOR = 0xFF55FFFF.toInt()

    private var text: String? = null
    private var color: Int = 0xFFFFFFFF.toInt()
    private var timer = 0

    fun showSpray(count: Int) {
        text = "$count sprayed!"
        color = ArcherUtilsModule.sprayColor()
        timer = DISPLAY_TICKS
    }

    fun showDeathBowKills(count: Int) {
        text = "Killed $count"
        color = ArcherUtilsModule.deathColor()
        timer = DISPLAY_TICKS
    }

    fun render(graphics: GuiGraphicsExtractor) {
        if (!ArcherUtilsModule.enabled) return
        if (timer <= 0) return
        val display = text ?: return

        val x = HudPositionManager.config.archerUtilsTitle.x
        val y = HudPositionManager.config.archerUtilsTitle.y
        val scale = HudPositionManager.config.archerUtilsTitleScale
        val rendered = if (ArcherUtilsModule.titleBold.value) "§l$display" else display

        HudTextRenderer.drawScaledText(graphics, rendered, x, y, color, scale, false)
        timer--
    }

    fun renderPreview(graphics: GuiGraphicsExtractor) {
        val x = HudPositionManager.config.archerUtilsTitle.x
        val y = HudPositionManager.config.archerUtilsTitle.y
        val scale = HudPositionManager.config.archerUtilsTitleScale
        HudTextRenderer.drawScaledText(graphics, getPreviewText(), x, y, PREVIEW_COLOR, scale, false)
    }

    fun getPreviewText(): String = if (ArcherUtilsModule.titleBold.value) "§l8 sprayed!" else "8 sprayed!"
    fun getWidth(): Float = HudTextRenderer.width(getPreviewText(), HudPositionManager.config.archerUtilsTitleScale).toFloat()
    fun getHeight(): Float = HudTextRenderer.lineHeight(HudPositionManager.config.archerUtilsTitleScale, 0f).toFloat()
}
