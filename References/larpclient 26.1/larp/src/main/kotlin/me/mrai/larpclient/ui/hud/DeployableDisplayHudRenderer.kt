package me.mrai.larpclient.ui.hud

import me.mrai.larpclient.features.impl.skyblock.general.deployabledisplay.DeployableDisplayModule
import me.mrai.larpclient.ui.font.HudTextRenderer
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor

object DeployableDisplayHudRenderer {
    fun render(graphics: GuiGraphicsExtractor) {
        if (!DeployableDisplayModule.enabled) return
        val active = DeployableDisplayModule.active() ?: return
        draw(graphics, active, preview = false)
    }

    fun renderPreview(graphics: GuiGraphicsExtractor) {
        val preview = DeployableDisplayModule.ActiveDeployable(
            type = DeployableDisplayModule.DeployableType.PLASMAFLUX,
            seconds = 98
        )
        draw(graphics, preview, preview = true)
    }

    fun getWidth(preview: Boolean = false): Float {
        return measure(if (preview) previewActive() else DeployableDisplayModule.active() ?: previewActive()).first *
            HudPositionManager.config.deployableDisplayScale
    }

    fun getHeight(preview: Boolean = false): Float {
        return measure(if (preview) previewActive() else DeployableDisplayModule.active() ?: previewActive()).second *
            HudPositionManager.config.deployableDisplayScale
    }

    private fun draw(graphics: GuiGraphicsExtractor, active: DeployableDisplayModule.ActiveDeployable, preview: Boolean) {
        val cfg = HudPositionManager.config
        val scale = cfg.deployableDisplayScale
        val x = cfg.deployableDisplay.x
        val y = cfg.deployableDisplay.y

        val pose = graphics.pose()
        pose.pushMatrix()
        pose.translate(x, y)
        pose.scale(scale, scale)
        val title = "${active.type.label()} [${active.seconds}s]:"
        HudTextRenderer.drawScaledText(graphics, "§l$title", 0f, 0f, active.type.titleColor, 1f, false)

        var lineY = Minecraft.getInstance().font.lineHeight + 2f
        for (stat in active.type.stats) {
            HudTextRenderer.drawScaledText(graphics, stat.renderText(), 0f, lineY, stat.color, 1f, false)
            lineY += Minecraft.getInstance().font.lineHeight + 1f
        }

        pose.popMatrix()
    }

    private fun measure(active: DeployableDisplayModule.ActiveDeployable): Pair<Int, Int> {
        val font = Minecraft.getInstance().font
        val titleWidth = font.width("${active.type.label()} [${active.seconds}s]:")
        val statsWidth = active.type.stats.maxOfOrNull { font.width(it.renderText()) } ?: 0
        val width = maxOf(titleWidth, statsWidth)
        val height = font.lineHeight + if (active.type.stats.isEmpty()) 0 else active.type.stats.size * (font.lineHeight + 1) + 1
        return Pair(width, height)
    }

    private fun previewActive(): DeployableDisplayModule.ActiveDeployable {
        return DeployableDisplayModule.ActiveDeployable(
            type = DeployableDisplayModule.DeployableType.PLASMAFLUX,
            seconds = 98
        )
    }
}
