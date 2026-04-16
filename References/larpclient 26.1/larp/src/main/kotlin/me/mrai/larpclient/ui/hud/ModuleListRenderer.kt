package me.mrai.larpclient.ui.hud

import me.mrai.larpclient.features.impl.misc.ui.modulelist.ModuleListModule
import me.mrai.larpclient.module.ModuleManager
import me.mrai.larpclient.ui.font.HudTextRenderer
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import kotlin.math.max
import kotlin.math.roundToInt

object ModuleListRenderer {
    private val previewEntries = listOf(
        "True Splits",
        "Term GUI",
        "Kuudra Waypoints",
        "Velocity Buffer"
    )

    data class ModuleListLayout(
        val anchorRight: Int,
        val renderX: Int,
        val renderY: Int,
        val guiScaledWidth: Int,
        val guiScaledHeight: Int,
        val visibleEntries: Int
    )

    private data class EntryAnim(
        var visible: Boolean = false,
        var progress: Float = 0f
    )

    private val entryAnimations = hashMapOf<String, EntryAnim>()

    fun render(context: GuiGraphicsExtractor) {
        val client = Minecraft.getInstance()
        val window = client.window
        val colorStart = activeModuleListModule()?.gradientStartColor?.text?.let(::parseColor) ?: 0xFF48E4E7.toInt()
        val colorEnd = activeModuleListModule()?.gradientEndColor?.text?.let(::parseColor) ?: 0xFF8B5CF6.toInt()

        if (!isEnabled()) {
            updateAnimations(emptyList())
            return
        }

        val screenWidth = window.guiScaledWidth
        val screenHeight = window.guiScaledHeight

        val renderableModules = getRenderableModules()
        updateAnimations(renderableModules)

        val rendered = getRenderedEntries()
        if (rendered.isEmpty()) return

        ensureDefaultPosition(screenWidth)
        drawEntries(
            context = context,
            screenHeight = screenHeight,
            scale = HudPositionManager.config.moduleListScale,
            anchorRight = HudPositionManager.config.moduleList.x.roundToInt(),
            startY = HudPositionManager.config.moduleList.y.roundToInt(),
            entries = rendered.map { it.key to it.value.progress.coerceIn(0f, 1f) },
            colorStart = colorStart,
            colorEnd = colorEnd
        )
    }

    fun renderPreview(context: GuiGraphicsExtractor) {
        val client = Minecraft.getInstance()
        val window = client.window
        val colorStart = activeModuleListModule()?.gradientStartColor?.text?.let(::parseColor) ?: 0xFF48E4E7.toInt()
        val colorEnd = activeModuleListModule()?.gradientEndColor?.text?.let(::parseColor) ?: 0xFF8B5CF6.toInt()

        ensureDefaultPosition(window.guiScaledWidth)
        drawEntries(
            context = context,
            screenHeight = window.guiScaledHeight,
            scale = HudPositionManager.config.moduleListScale,
            anchorRight = HudPositionManager.config.moduleList.x.roundToInt(),
            startY = HudPositionManager.config.moduleList.y.roundToInt(),
            entries = previewEntryNames().map { it to 1f },
            colorStart = colorStart,
            colorEnd = colorEnd
        )
    }

    fun measureLayout(client: Minecraft = Minecraft.getInstance()): ModuleListLayout? {
        val screenWidth = client.window.guiScaledWidth

        if (!isEnabled()) {
            updateAnimations(emptyList())
            return null
        }

        updateAnimations(getRenderableModules())
        val rendered = getRenderedEntries()
        if (rendered.isEmpty()) return null

        ensureDefaultPosition(screenWidth)

        val padX = 4
        val lineSpacing = 1
        val scale = HudPositionManager.config.moduleListScale
        val anchorRight = HudPositionManager.config.moduleList.x.roundToInt()
        val startY = HudPositionManager.config.moduleList.y.roundToInt()

        var maxWidth = 0
        var totalHeight = 0
        for (entry in rendered) {
            val textWidth = HudTextRenderer.width(entry.key, scale)
            val rowWidth = textWidth + (padX * 2 * scale).roundToInt()
            maxWidth = max(maxWidth, rowWidth)
            totalHeight += max(1, ((HudTextRenderer.lineHeight(scale) + lineSpacing * scale) * entry.value.progress.coerceIn(0f, 1f)).roundToInt())
        }

        return ModuleListLayout(
            anchorRight = anchorRight,
            renderX = anchorRight - maxWidth,
            renderY = startY,
            guiScaledWidth = maxWidth,
            guiScaledHeight = totalHeight.coerceAtLeast(HudTextRenderer.lineHeight(scale) + 1),
            visibleEntries = rendered.size
        )
    }

    fun measurePreviewLayout(client: Minecraft = Minecraft.getInstance()): ModuleListLayout {
        val screenWidth = client.window.guiScaledWidth
        ensureDefaultPosition(screenWidth)

        val scale = HudPositionManager.config.moduleListScale
        val anchorRight = HudPositionManager.config.moduleList.x.roundToInt()
        val startY = HudPositionManager.config.moduleList.y.roundToInt()
        val entries = previewEntryNames()

        var maxWidth = 0
        var totalHeight = 0
        for (entry in entries) {
            val rowWidth = HudTextRenderer.width(entry, scale) + (4 * 2 * scale).roundToInt()
            maxWidth = max(maxWidth, rowWidth)
            totalHeight += max(1, (HudTextRenderer.lineHeight(scale) + 1f * scale).roundToInt())
        }

        return ModuleListLayout(
            anchorRight = anchorRight,
            renderX = anchorRight - maxWidth,
            renderY = startY,
            guiScaledWidth = maxWidth,
            guiScaledHeight = totalHeight.coerceAtLeast(HudTextRenderer.lineHeight(scale) + 1),
            visibleEntries = entries.size
        )
    }

    private fun isEnabled(): Boolean {
        return ModuleManager.modules.any { it is ModuleListModule && it.enabled }
    }

    private fun activeModuleListModule(): ModuleListModule? {
        return ModuleManager.modules.firstOrNull { it is ModuleListModule } as? ModuleListModule
    }

    private fun getRenderableModules() = ModuleManager.modules.filter {
        it.enabled && it !is ModuleListModule && !it.name.equals("Module List", ignoreCase = true)
    }

    private fun updateAnimations(modules: List<me.mrai.larpclient.module.Module>) {
        val visibleNames = modules.map { it.name }.toSet()

        for ((name, anim) in entryAnimations) {
            anim.visible = name in visibleNames
        }
        for (module in modules) {
            val anim = entryAnimations.getOrPut(module.name) { EntryAnim() }
            anim.visible = true
        }

        val toRemove = mutableListOf<String>()
        for ((name, anim) in entryAnimations) {
            val target = if (anim.visible) 1f else 0f
            anim.progress = animate(anim.progress, target, 0.22f)
            if (!anim.visible && anim.progress < 0.01f) {
                toRemove += name
            }
        }
        toRemove.forEach(entryAnimations::remove)
    }

    private fun getRenderedEntries(): List<Map.Entry<String, EntryAnim>> {
        return entryAnimations.entries
            .filter { it.value.progress > 0.01f }
            .sortedWith(
                compareByDescending<Map.Entry<String, EntryAnim>> { HudTextRenderer.width(it.key) }
                    .thenBy { it.key.lowercase() }
            )
    }

    private fun ensureDefaultPosition(screenWidth: Int) {
        if (HudPositionManager.config.moduleList.x == 0f && HudPositionManager.config.moduleList.y == 0f) {
            HudPositionManager.config.moduleList.x = (screenWidth - 2).toFloat()
            HudPositionManager.config.moduleList.y = 2f
            HudPositionManager.save()
        }
    }

    private fun previewEntryNames(): List<String> {
        val live = getRenderableModules().map { it.name }.sortedByDescending { HudTextRenderer.width(it) }
        return if (live.isNotEmpty()) live.take(6) else previewEntries
    }

    private fun drawEntries(
        context: GuiGraphicsExtractor,
        screenHeight: Int,
        scale: Float,
        anchorRight: Int,
        startY: Int,
        entries: List<Pair<String, Float>>,
        colorStart: Int,
        colorEnd: Int
    ) {
        val padX = 4
        val padY = 0
        val lineSpacing = 1
        var y = startY

        for ((moduleName, rawProgress) in entries) {
            val progress = rawProgress.coerceIn(0f, 1f)

            val textWidth = HudTextRenderer.width(moduleName, scale)
            val fontHeight = HudTextRenderer.lineHeight(scale)
            val boxWidth = textWidth + (padX * 2 * scale).roundToInt()
            val boxHeight = fontHeight + scale.roundToInt()
            val slideOffset = ((1f - easeOutCubic(progress)) * (boxWidth + 10f)).roundToInt()

            val boxY = y
            val textX = anchorRight - textWidth - (padX * scale).roundToInt() + slideOffset

            if (boxY + boxHeight >= 0 && boxY <= screenHeight) {
                drawGradientTextWithShadow(
                    context = context,
                    text = moduleName,
                    x = textX,
                    y = boxY + padY,
                    scale = scale,
                    leftColor = colorStart,
                    rightColor = colorEnd
                )
            }

            y += max(1, ((fontHeight + lineSpacing * scale) * progress).roundToInt())
        }
    }

    private fun drawGradientTextWithShadow(
        context: GuiGraphicsExtractor,
        text: String,
        x: Int,
        y: Int,
        scale: Float,
        leftColor: Int,
        rightColor: Int
    ) {
        var drawX = x
        val lastIndex = (text.length - 1).coerceAtLeast(1)

        text.forEachIndexed { index, char ->
            val s = char.toString()
            val t = index.toFloat() / lastIndex.toFloat()
            val color = mixColor(leftColor, rightColor, t)
            HudTextRenderer.drawScaledText(context, s, drawX.toFloat() + scale, y.toFloat() + scale, 0x99000000.toInt(), scale, false)
            HudTextRenderer.drawScaledText(context, s, drawX.toFloat(), y.toFloat(), color, scale, false)
            drawX += HudTextRenderer.width(s, scale)
        }
    }

    private fun animate(current: Float, target: Float, speed: Float): Float {
        return current + (target - current) * speed
    }

    private fun easeOutCubic(t: Float): Float {
        val clamped = t.coerceIn(0f, 1f)
        val inv = 1f - clamped
        return 1f - inv * inv * inv
    }

    private fun parseColor(text: String): Int? {
        val raw = text.trim().removePrefix("#")
        return try {
            when (raw.length) {
                6 -> (0xFF000000 or raw.toLong(16)).toInt()
                8 -> raw.toLong(16).toInt()
                else -> null
            }
        } catch (_: Throwable) {
            null
        }
    }

    private fun mixColor(from: Int, to: Int, t: Float): Int {
        val clamped = t.coerceIn(0f, 1f)

        val a1 = (from ushr 24) and 0xFF
        val r1 = (from ushr 16) and 0xFF
        val g1 = (from ushr 8) and 0xFF
        val b1 = from and 0xFF

        val a2 = (to ushr 24) and 0xFF
        val r2 = (to ushr 16) and 0xFF
        val g2 = (to ushr 8) and 0xFF
        val b2 = to and 0xFF

        val a = (a1 + ((a2 - a1) * clamped)).roundToInt()
        val r = (r1 + ((r2 - r1) * clamped)).roundToInt()
        val g = (g1 + ((g2 - g1) * clamped)).roundToInt()
        val b = (b1 + ((b2 - b1) * clamped)).roundToInt()

        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }
}
