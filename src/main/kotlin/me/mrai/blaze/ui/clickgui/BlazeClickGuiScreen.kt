package me.mrai.blaze.ui.clickgui

import com.mojang.blaze3d.platform.InputConstants
import kotlin.math.abs
import kotlin.math.roundToInt
import me.mrai.blaze.data.BlazeDataStore
import me.mrai.blaze.feature.autoclicker.AutoclickerSide
import me.mrai.blaze.feature.autoclicker.BlazeInputBind
import me.mrai.blaze.feature.autoclicker.BlazeInputType
import me.mrai.blaze.feature.autoclicker.SideAutoclickerConfig
import me.mrai.blaze.meta.BlazeMetadata
import me.mrai.blaze.render.gui.BlazeColorPalette
import me.mrai.blaze.render.gui.GuiPrimitives
import me.mrai.blaze.render.gui.GuiRect
import me.mrai.blaze.ui.clickgui.model.BlazeCategory
import me.mrai.blaze.ui.clickgui.model.BlazeModule
import me.mrai.blaze.ui.clickgui.model.BlazeModuleIds
import me.mrai.blaze.ui.clickgui.model.ClickGuiLayout
import me.mrai.blaze.ui.font.BlazeText
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component

class BlazeClickGuiScreen : Screen(Component.literal("BLAZE ClickGUI")) {
    companion object {
        fun open() {
            BlazeText.prewarmClickGui()
            Minecraft.getInstance().setScreen(BlazeClickGuiScreen())
        }
    }

    private val animationValues = mutableMapOf<String, Float>()
    private val categoryRegions = mutableListOf<CategoryRegion>()
    private val moduleCardRegions = mutableListOf<ModuleCardRegion>()
    private val autoclickerPillRegions = mutableMapOf<AutoclickerSide, GuiRect>()
    private val autoclickerSliderRegions = mutableMapOf<AutoclickerSide, GuiRect>()
    private val autoclickerToggleRegions = mutableMapOf<AutoclickerSide, Pair<GuiRect, GuiRect>>()
    private val autoclickerModeRegions = mutableMapOf<AutoclickerSide, GuiRect>()
    private val autoclickerBindRegions = mutableMapOf<AutoclickerSide, GuiRect>()

    private var selectedCategory = BlazeCategory.GENERAL
    private var expandedModuleId: String? = null
    private var openedAtNanos = System.nanoTime()
    private var panelX: Int? = null
    private var panelY: Int? = null
    private var draggingPanel = false
    private var dragOffsetX = 0
    private var dragOffsetY = 0
    private var dragHeaderBounds: GuiRect? = null
    private var editHudsButtonBounds: GuiRect? = null
    private var moduleListBounds: GuiRect? = null
    private var moduleScroll = 0f
    private var moduleScrollMax = 0f
    private var activeSliderSide: AutoclickerSide? = null
    private var awaitingBindSide: AutoclickerSide? = null
    private var leftAutoclickerExpanded = true
    private var rightAutoclickerExpanded = false

    override fun init() {
        super.init()
        openedAtNanos = System.nanoTime()
        val layout = ClickGuiLayout.compute(width, height, panelX, panelY)
        panelX = layout.panel.x
        panelY = layout.panel.y
    }

    override fun render(graphics: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
        val openProgress = ((System.nanoTime() - openedAtNanos) / 120_000_000.0).toFloat().coerceIn(0f, 1f)
        val eased = easeOutCubic(openProgress)
        graphics.fill(0, 0, width, height, withAlpha(BlazeColorPalette.SCREEN_SCRIM, eased))

        val layout = ClickGuiLayout.compute(width, height, panelX, panelY)
        categoryRegions.clear()
        moduleCardRegions.clear()
        autoclickerPillRegions.clear()
        autoclickerSliderRegions.clear()
        autoclickerToggleRegions.clear()
        autoclickerModeRegions.clear()
        autoclickerBindRegions.clear()
        dragHeaderBounds = GuiRect(layout.panel.x, layout.panel.y, layout.panel.width, layout.headerHeight)
        editHudsButtonBounds = null
        moduleListBounds = null

        val centerX = layout.panel.x + layout.panel.width / 2f
        val centerY = layout.panel.y + layout.panel.height / 2f
        val panelScale = 0.92f + 0.08f * eased
        val pose = graphics.pose()
        pose.pushMatrix()
        pose.translate(centerX, centerY)
        pose.scale(panelScale, panelScale)
        pose.translate(-centerX, -centerY)
        renderPanelShell(graphics, layout)
        renderHeader(graphics, layout)
        renderCategoryRail(graphics, layout)
        renderModulePane(graphics, layout)
        pose.popMatrix()
    }

    override fun mouseClicked(mouseButtonEvent: MouseButtonEvent, doubleClick: Boolean): Boolean {
        if (((System.nanoTime() - openedAtNanos) / 120_000_000.0).toFloat() < 0.92f) {
            return true
        }

        if (handleBindButtonClick(mouseButtonEvent)) {
            return true
        }

        if (mouseButtonEvent.button() == 0) {
            if (handleModuleControlClick(mouseButtonEvent.x(), mouseButtonEvent.y())) {
                return true
            }
            dragHeaderBounds?.takeIf { it.contains(mouseButtonEvent.x(), mouseButtonEvent.y()) }?.let {
                draggingPanel = true
                dragOffsetX = mouseButtonEvent.x().toInt() - (panelX ?: it.x)
                dragOffsetY = mouseButtonEvent.y().toInt() - (panelY ?: it.y)
                return true
            }
            editHudsButtonBounds?.takeIf { it.contains(mouseButtonEvent.x(), mouseButtonEvent.y()) }?.let {
                minecraft.setScreen(BlazeEditHudsScreen())
                return true
            }
            categoryRegions.firstOrNull { it.bounds.contains(mouseButtonEvent.x(), mouseButtonEvent.y()) }?.let { region ->
                selectedCategory = region.category
                expandedModuleId = null
                moduleScroll = 0f
                activeSliderSide = null
                awaitingBindSide = null
                return true
            }
            moduleCardRegions.firstOrNull { it.headerBounds.contains(mouseButtonEvent.x(), mouseButtonEvent.y()) }?.let { region ->
                BlazeDataStore.toggleModule(region.module.id)
                return true
            }
        }

        if (mouseButtonEvent.button() == 1) {
            moduleCardRegions.firstOrNull { it.headerBounds.contains(mouseButtonEvent.x(), mouseButtonEvent.y()) }?.let { region ->
                if (region.module.expandable) {
                    expandedModuleId = if (expandedModuleId == region.module.id) null else region.module.id
                    activeSliderSide = null
                    awaitingBindSide = null
                    return true
                }
            }
        }

        return super.mouseClicked(mouseButtonEvent, doubleClick)
    }

    override fun mouseDragged(mouseButtonEvent: MouseButtonEvent, dragX: Double, dragY: Double): Boolean {
        activeSliderSide?.let { side ->
            updateSlider(side, mouseButtonEvent.x())
            return true
        }

        if (draggingPanel && mouseButtonEvent.button() == 0) {
            val layout = ClickGuiLayout.compute(width, height, panelX, panelY)
            val maxX = (width - layout.panel.width).coerceAtLeast(0)
            val maxY = (height - layout.panel.height).coerceAtLeast(0)
            panelX = (mouseButtonEvent.x().toInt() - dragOffsetX).coerceIn(0, maxX)
            panelY = (mouseButtonEvent.y().toInt() - dragOffsetY).coerceIn(0, maxY)
            return true
        }
        return super.mouseDragged(mouseButtonEvent, dragX, dragY)
    }

    override fun mouseReleased(mouseButtonEvent: MouseButtonEvent): Boolean {
        activeSliderSide = null
        if (mouseButtonEvent.button() == 0 && draggingPanel) {
            draggingPanel = false
            return true
        }
        return super.mouseReleased(mouseButtonEvent)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        val bounds = moduleListBounds
        if (bounds != null && bounds.contains(mouseX, mouseY) && moduleScrollMax > 0f) {
            moduleScroll = (moduleScroll - scrollY.toFloat() * 18f).coerceIn(0f, moduleScrollMax)
            return true
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)
    }

    override fun keyPressed(keyEvent: KeyEvent): Boolean {
        val waitingSide = awaitingBindSide
        if (waitingSide != null) {
            if (keyEvent.key() == InputConstants.KEY_ESCAPE) {
                awaitingBindSide = null
                return true
            }
            if (keyEvent.key() != InputConstants.UNKNOWN.value) {
                setAutoclickerSide(waitingSide) { side ->
                    side.copy(bind = BlazeInputBind(BlazeInputType.KEYSYM, keyEvent.key()))
                }
                awaitingBindSide = null
            }
            return true
        }
        return super.keyPressed(keyEvent)
    }

    override fun isPauseScreen(): Boolean = false

    private fun renderPanelShell(graphics: GuiGraphics, layout: ClickGuiLayout) {
        GuiPrimitives.drawGradientShadow(
            graphics = graphics,
            rect = layout.panel,
            radius = layout.panelRadius,
            innerColor = BlazeColorPalette.GLOW_START,
            outerColor = BlazeColorPalette.GLOW_END
        )
        GuiPrimitives.fillRoundedRect(graphics, layout.panel, layout.panelRadius, BlazeColorPalette.PANEL_BASE)
        GuiPrimitives.fillRoundedRect(
            graphics,
            GuiRect(layout.separator.x, layout.separator.y, 2, layout.separator.height),
            1,
            BlazeColorPalette.SEPARATOR
        )
    }

    private fun renderHeader(graphics: GuiGraphics, layout: ClickGuiLayout) {
        fun s(value: Int): Int = (value * layout.uiScale).toInt()

        val titleX = layout.panel.x + s(24)
        val titleY = layout.panel.y + s(22)
        val titleScale = 2.0f * layout.uiScale
        BlazeText.drawGradientTitle(
            graphics = graphics,
            font = font,
            text = "BLAZE",
            x = titleX,
            y = titleY,
            startColor = BlazeColorPalette.TITLE_START,
            endColor = BlazeColorPalette.TITLE_END,
            scale = titleScale
        )
        BlazeText.drawScaled(
            graphics = graphics,
            font = font,
            text = BlazeMetadata.version,
            x = titleX + BlazeText.titleWidth("BLAZE", titleScale) + s(10),
            y = titleY + s(11),
            color = BlazeColorPalette.TEXT_MUTED,
            scale = 0.82f * layout.uiScale,
            bold = true
        )
    }

    private fun renderCategoryRail(graphics: GuiGraphics, layout: ClickGuiLayout) {
        fun s(value: Int): Int = (value * layout.uiScale).toInt()

        BlazeText.draw(
            graphics = graphics,
            font = font,
            text = "Category",
            x = layout.leftPane.x + s(6),
            y = layout.leftPane.y + s(8),
            color = BlazeColorPalette.TEXT_PRIMARY,
            bold = true,
            shadow = true
        )

        var currentY = layout.leftPane.y + s(34)
        BlazeCategory.entries.forEach { category ->
            val buttonBounds = GuiRect(layout.leftPane.x + s(4), currentY, layout.leftPane.width - s(8), s(28))
            val selected = category == selectedCategory
            BlazeText.draw(
                graphics = graphics,
                font = font,
                text = category.displayName,
                x = buttonBounds.x + s(6),
                y = buttonBounds.y + s(9),
                color = if (selected) BlazeColorPalette.TITLE_END else BlazeColorPalette.TEXT_SECONDARY,
                bold = selected
            )
            categoryRegions += CategoryRegion(category, buttonBounds)
            currentY += s(34)
        }

        val editHudWidth = BlazeText.width(font, "Edit HUDs", bold = true)
        val editHudHeight = BlazeText.height(font, "Edit HUDs", bold = true)
        val buttonWidth = editHudWidth + s(18)
        val buttonHeight = editHudHeight + s(12)
        val buttonX = layout.leftPane.x + ((layout.leftPane.width - buttonWidth) / 2)
        val buttonY = layout.leftPane.bottom - buttonHeight - s(6)
        val buttonBounds = GuiRect(buttonX, buttonY, buttonWidth, buttonHeight)
        editHudsButtonBounds = buttonBounds

        GuiPrimitives.drawGradientShadow(
            graphics,
            buttonBounds,
            layout.elementRadius,
            BlazeColorPalette.GLOW_START,
            BlazeColorPalette.GLOW_END,
            layers = 5
        )
        GuiPrimitives.fillRoundedRect(graphics, buttonBounds, layout.elementRadius, BlazeColorPalette.PANEL_BASE)
        drawCenteredText(graphics, buttonBounds, "Edit HUDs", BlazeColorPalette.TEXT_PRIMARY, bold = true)
    }

    private fun renderModulePane(graphics: GuiGraphics, layout: ClickGuiLayout) {
        fun s(value: Int): Int = (value * layout.uiScale).toInt()

        BlazeText.draw(
            graphics = graphics,
            font = font,
            text = selectedCategory.displayName,
            x = layout.rightPane.x + s(4),
            y = layout.rightPane.y + s(8),
            color = BlazeColorPalette.TEXT_PRIMARY,
            bold = true,
            shadow = true
        )
        BlazeText.draw(
            graphics = graphics,
            font = font,
            text = selectedCategory.description,
            x = layout.rightPane.x + s(4),
            y = layout.rightPane.y + s(28),
            color = BlazeColorPalette.TEXT_SECONDARY
        )

        val cardArea = GuiRect(
            x = layout.rightPane.x + s(10),
            y = layout.rightPane.y + s(58),
            width = layout.rightPane.width - s(20),
            height = (layout.rightPane.bottom - (layout.rightPane.y + s(58)) - s(8)).coerceAtLeast(s(24))
        )
        moduleListBounds = cardArea
        moduleScroll = moduleScroll.coerceIn(0f, moduleScrollMax)

        val glowMargin = s(8)
        val scissorLeft = (cardArea.x - glowMargin).coerceAtLeast(layout.separator.x + s(6))
        val scissorTop = (cardArea.y - glowMargin).coerceAtLeast(layout.rightPane.y)
        val scissorRight = (cardArea.right + glowMargin).coerceAtMost(layout.panel.right - s(8))
        val scissorBottom = (cardArea.bottom + glowMargin).coerceAtMost(layout.panel.bottom - s(8))

        graphics.enableScissor(scissorLeft, scissorTop, scissorRight, scissorBottom)
        var contentHeight = 0
        val startY = cardArea.y - moduleScroll.toInt()
        selectedCategory.modules.forEach { module ->
            val state = measureModuleState(layout, module)
            val cardRect = GuiRect(cardArea.x, startY + contentHeight, cardArea.width, state.totalHeight)
            if (cardRect.bottom >= scissorTop - s(16) && cardRect.y <= scissorBottom + s(16)) {
                renderModuleCard(graphics, layout, module, cardRect, state)
            }
            contentHeight += state.totalHeight + s(12)
        }
        graphics.disableScissor()
        moduleScrollMax = (contentHeight - cardArea.height).coerceAtLeast(0).toFloat()
        moduleScroll = moduleScroll.coerceIn(0f, moduleScrollMax)
    }

    private fun renderModuleCard(
        graphics: GuiGraphics,
        layout: ClickGuiLayout,
        module: BlazeModule,
        rect: GuiRect,
        state: ModuleRenderState
    ) {
        fun s(value: Int): Int = (value * layout.uiScale).toInt()

        if (state.enabledProgress > 0.02f) {
            GuiPrimitives.drawGradientShadow(
                graphics,
                rect,
                layout.elementRadius,
                withAlpha(BlazeColorPalette.GLOW_START, state.enabledProgress * 0.95f),
                withAlpha(BlazeColorPalette.GLOW_END, state.enabledProgress * 0.95f),
                layers = 6
            )
        }

        val fillColor = GuiPrimitives.mixColor(BlazeColorPalette.MODULE_CARD_FILL, BlazeColorPalette.MODULE_CARD_ENABLED_FILL, state.enabledProgress)
        val borderColor = GuiPrimitives.mixColor(
            BlazeColorPalette.MODULE_CARD_OUTLINE,
            withAlpha(BlazeColorPalette.TEXT_ACCENT, 0.75f),
            state.enabledProgress
        )
        GuiPrimitives.drawRoundedFrame(graphics, rect, layout.elementRadius, borderColor, fillColor)

        val headerHeight = s(40)
        val headerBounds = GuiRect(rect.x, rect.y, rect.width, headerHeight)
        moduleCardRegions += ModuleCardRegion(module, headerBounds)

        val dividerX = rect.x + rect.width / 3
        val dividerRect = GuiRect(dividerX, headerBounds.y + s(6), 1, headerBounds.height - s(12))
        GuiPrimitives.fillRoundedRect(graphics, dividerRect, 1, BlazeColorPalette.SEPARATOR)

        val leftZone = GuiRect(rect.x + s(6), headerBounds.y, rect.width / 3 - s(12), headerBounds.height)
        val rightZone = GuiRect(dividerX + s(8), headerBounds.y, rect.right - dividerX - s(16), headerBounds.height)
        drawCenteredText(graphics, leftZone, module.name, BlazeColorPalette.TEXT_PRIMARY, bold = true)
        drawCenteredText(graphics, rightZone, module.description, BlazeColorPalette.TEXT_SECONDARY, scale = 0.86f)

        if (state.expandedProgress <= 0.01f) {
            return
        }

        val contentRect = GuiRect(rect.x + s(8), headerBounds.bottom, rect.width - s(16), rect.height - headerBounds.height)
        graphics.enableScissor(contentRect.x, contentRect.y, contentRect.right, contentRect.bottom)
        if (module.id == BlazeModuleIds.AUTOCLICKER) {
            renderAutoclickerContent(graphics, layout, contentRect, state)
        } else if (module.children.isNotEmpty()) {
            renderModuleChildren(graphics, layout, contentRect, module)
        }
        graphics.disableScissor()
    }

    private fun renderAutoclickerContent(graphics: GuiGraphics, layout: ClickGuiLayout, rect: GuiRect, state: ModuleRenderState) {
        fun s(value: Int): Int = (value * layout.uiScale).toInt()

        val pillHeight = s(18)
        val gapAfterPill = s(6)
        val betweenSections = s(10)
        val pillWidth = s(44)
        val sectionFullHeight = s(134)
        var currentY = rect.y + s(8)

        val leftPill = GuiRect(rect.x + s(4), currentY, pillWidth, pillHeight)
        autoclickerPillRegions[AutoclickerSide.LEFT] = leftPill
        drawPill(
            graphics,
            layout,
            leftPill,
            "Left",
            leftAutoclickerExpanded,
            state.leftRevealProgress,
            BlazeDataStore.state.autoclicker?.left?.enabled == true
        )
        currentY += pillHeight + gapAfterPill
        if (state.leftRevealProgress > 0.02f) {
            val visibleHeight = (sectionFullHeight * state.leftRevealProgress).roundToInt().coerceAtLeast(1)
            val visibleRect = GuiRect(rect.x + s(4), currentY, rect.width - s(8), visibleHeight)
            val fullRect = GuiRect(rect.x + s(4), currentY, rect.width - s(8), sectionFullHeight)
            renderAutoclickerSection(graphics, layout, fullRect, visibleRect, AutoclickerSide.LEFT, BlazeDataStore.state.autoclicker!!.left, state.leftRevealProgress)
            currentY += visibleHeight + betweenSections
        } else {
            currentY += betweenSections
        }

        val rightPill = GuiRect(rect.x + s(4), currentY, pillWidth, pillHeight)
        autoclickerPillRegions[AutoclickerSide.RIGHT] = rightPill
        drawPill(
            graphics,
            layout,
            rightPill,
            "Right",
            rightAutoclickerExpanded,
            state.rightRevealProgress,
            BlazeDataStore.state.autoclicker?.right?.enabled == true
        )
        currentY += pillHeight + gapAfterPill
        if (state.rightRevealProgress > 0.02f) {
            val visibleHeight = (sectionFullHeight * state.rightRevealProgress).roundToInt().coerceAtLeast(1)
            val visibleRect = GuiRect(rect.x + s(4), currentY, rect.width - s(8), visibleHeight)
            val fullRect = GuiRect(rect.x + s(4), currentY, rect.width - s(8), sectionFullHeight)
            renderAutoclickerSection(graphics, layout, fullRect, visibleRect, AutoclickerSide.RIGHT, BlazeDataStore.state.autoclicker!!.right, state.rightRevealProgress)
        }
    }

    private fun renderAutoclickerSection(
        graphics: GuiGraphics,
        layout: ClickGuiLayout,
        fullRect: GuiRect,
        visibleRect: GuiRect,
        side: AutoclickerSide,
        config: SideAutoclickerConfig,
        revealProgress: Float
    ) {
        fun s(value: Int): Int = (value * layout.uiScale).toInt()

        graphics.enableScissor(visibleRect.x, visibleRect.y, visibleRect.right, visibleRect.bottom)
        GuiPrimitives.drawRoundedFrame(
            graphics,
            fullRect,
            layout.elementRadius,
            GuiPrimitives.mixColor(BlazeColorPalette.MODULE_CARD_OUTLINE, withAlpha(BlazeColorPalette.TEXT_ACCENT, 0.65f), revealProgress),
            0xB0101010.toInt()
        )

        val labelY = fullRect.y + s(8)
        val sliderRect = GuiRect(fullRect.x + s(8), fullRect.y + s(24), fullRect.width - s(16), s(10))
        val toggleWidth = (fullRect.width - s(20)) / 2
        val onButton = GuiRect(fullRect.x + s(8), fullRect.y + s(56), toggleWidth, s(18))
        val offButton = GuiRect(onButton.right + s(4), fullRect.y + s(56), toggleWidth, s(18))
        val modeButton = GuiRect(fullRect.x + s(8), fullRect.y + s(86), fullRect.width - s(16), s(18))
        val bindButton = GuiRect(fullRect.x + s(8), fullRect.y + s(116), fullRect.width - s(16), s(18))

        if (revealProgress > 0.95f) {
            autoclickerSliderRegions[side] = sliderRect
            autoclickerToggleRegions[side] = onButton to offButton
            autoclickerModeRegions[side] = modeButton
            autoclickerBindRegions[side] = bindButton
        }

        val cpsLabel = "${side.displayName} CPS"
        val cpsValue = config.cps.toString()
        BlazeText.draw(graphics, font, cpsLabel, fullRect.x + s(8), labelY, BlazeColorPalette.TEXT_PRIMARY, bold = true)
        BlazeText.draw(
            graphics,
            font,
            cpsValue,
            fullRect.right - BlazeText.width(font, cpsValue, bold = true) - s(8),
            labelY,
            BlazeColorPalette.TEXT_ACCENT,
            bold = true
        )
        drawSlider(graphics, layout, sliderRect, config.cps)

        BlazeText.draw(graphics, font, "Enabled", fullRect.x + s(8), fullRect.y + s(44), BlazeColorPalette.TEXT_SECONDARY)
        drawButtonLike(graphics, layout, onButton, "On", highlight = config.enabled)
        drawButtonLike(graphics, layout, offButton, "Off", highlight = !config.enabled)

        BlazeText.draw(graphics, font, "Toggle/Hold", fullRect.x + s(8), fullRect.y + s(74), BlazeColorPalette.TEXT_SECONDARY)
        drawButtonLike(graphics, layout, modeButton, config.activationMode.displayName, highlight = true)

        BlazeText.draw(graphics, font, "Key input", fullRect.x + s(8), fullRect.y + s(104), BlazeColorPalette.TEXT_SECONDARY)
        val bindText = if (awaitingBindSide == side) "Listening..." else bindLabel(config.bind)
        drawButtonLike(graphics, layout, bindButton, bindText, highlight = awaitingBindSide == side)
        graphics.disableScissor()
    }

    private fun renderModuleChildren(graphics: GuiGraphics, layout: ClickGuiLayout, rect: GuiRect, module: BlazeModule) {
        fun s(value: Int): Int = (value * layout.uiScale).toInt()

        var currentY = rect.y + s(10)
        module.children.forEach { child ->
            BlazeText.draw(
                graphics = graphics,
                font = font,
                text = child,
                x = rect.x + s(4),
                y = currentY,
                color = BlazeColorPalette.TEXT_SECONDARY
            )
            currentY += s(18)
        }
    }

    private fun drawPill(
        graphics: GuiGraphics,
        layout: ClickGuiLayout,
        rect: GuiRect,
        text: String,
        expanded: Boolean,
        progress: Float,
        active: Boolean
    ) {
        GuiPrimitives.drawRoundedFrame(
            graphics,
            rect,
            layout.elementRadius,
            GuiPrimitives.mixColor(BlazeColorPalette.MODULE_CARD_OUTLINE, withAlpha(BlazeColorPalette.TEXT_ACCENT, 0.65f), progress),
            if (active) BlazeColorPalette.MODULE_CARD_PILL_ACTIVE else BlazeColorPalette.MODULE_CARD_PILL
        )
        drawCenteredText(graphics, rect, text, if (active) BlazeColorPalette.TEXT_ACCENT else BlazeColorPalette.TEXT_PRIMARY, bold = true)
    }

    private fun drawButtonLike(graphics: GuiGraphics, layout: ClickGuiLayout, rect: GuiRect, text: String, highlight: Boolean) {
        GuiPrimitives.drawRoundedFrame(
            graphics,
            rect,
            layout.elementRadius,
            if (highlight) withAlpha(BlazeColorPalette.TEXT_ACCENT, 0.65f) else BlazeColorPalette.MODULE_CARD_OUTLINE,
            BlazeColorPalette.MODULE_CARD_PILL
        )
        drawCenteredText(graphics, rect, text, if (highlight) BlazeColorPalette.TEXT_ACCENT else BlazeColorPalette.TEXT_PRIMARY, bold = true)
    }

    private fun drawSlider(graphics: GuiGraphics, layout: ClickGuiLayout, rect: GuiRect, value: Int) {
        val track = GuiRect(rect.x, rect.y + rect.height / 2 - 2, rect.width, 4)
        GuiPrimitives.fillRoundedRect(graphics, track, 2, BlazeColorPalette.MODULE_CARD_SLIDER)

        val progress = value.coerceIn(0, 30) / 30f
        val fillWidth = (track.width * progress).roundToInt().coerceAtLeast(4)
        GuiPrimitives.fillRoundedRect(graphics, GuiRect(track.x, track.y, fillWidth, track.height), 2, BlazeColorPalette.TEXT_ACCENT)
        val knobX = (track.x + (track.width * progress).roundToInt()).coerceIn(track.x, track.right)
        GuiPrimitives.fillRoundedRect(graphics, GuiRect(knobX - 4, rect.y + 1, 8, rect.height - 2), layout.elementRadius, BlazeColorPalette.MODULE_CARD_SLIDER_KNOB)
    }

    private fun drawCenteredText(
        graphics: GuiGraphics,
        rect: GuiRect,
        text: String,
        color: Int,
        scale: Float = 1f,
        bold: Boolean = false
    ) {
        val width = BlazeText.widthScaled(font, text, scale, bold)
        val centerOffset = BlazeText.centerOffset(font, text, bold) * scale
        val x = rect.x + ((rect.width - width) / 2).coerceAtLeast(0)
        val y = (rect.y + rect.height / 2f - centerOffset).toInt()
        BlazeText.drawScaled(graphics, font, text, x, y, color, scale, bold = bold)
    }

    private fun handleModuleControlClick(mouseX: Double, mouseY: Double): Boolean {
        autoclickerSliderRegions.entries.firstOrNull { it.value.contains(mouseX, mouseY) }?.let { (side, _) ->
            activeSliderSide = side
            updateSlider(side, mouseX)
            return true
        }
        autoclickerToggleRegions.entries.firstOrNull { (_, pair) ->
            pair.first.contains(mouseX, mouseY) || pair.second.contains(mouseX, mouseY)
        }?.let { (side, pair) ->
            setAutoclickerSide(side) { config ->
                config.copy(enabled = pair.first.contains(mouseX, mouseY))
            }
            return true
        }
        autoclickerModeRegions.entries.firstOrNull { it.value.contains(mouseX, mouseY) }?.let { (side, _) ->
            setAutoclickerSide(side) { config -> config.copy(activationMode = config.activationMode.cycle()) }
            return true
        }
        autoclickerPillRegions.entries.firstOrNull { it.value.contains(mouseX, mouseY) }?.let { (side, _) ->
            when (side) {
                AutoclickerSide.LEFT -> leftAutoclickerExpanded = !leftAutoclickerExpanded
                AutoclickerSide.RIGHT -> rightAutoclickerExpanded = !rightAutoclickerExpanded
            }
            return true
        }
        return false
    }

    private fun handleBindButtonClick(mouseButtonEvent: MouseButtonEvent): Boolean {
        autoclickerBindRegions.entries.firstOrNull { it.value.contains(mouseButtonEvent.x(), mouseButtonEvent.y()) }?.let { (side, _) ->
            awaitingBindSide = side
            activeSliderSide = null
            return true
        }

        val waitingSide = awaitingBindSide ?: return false
        setAutoclickerSide(waitingSide) { config ->
            config.copy(bind = BlazeInputBind(BlazeInputType.MOUSE, mouseButtonEvent.button()))
        }
        awaitingBindSide = null
        return true
    }

    private fun updateSlider(side: AutoclickerSide, mouseX: Double) {
        val slider = autoclickerSliderRegions[side] ?: return
        val progress = ((mouseX - slider.x) / slider.width.toDouble()).coerceIn(0.0, 1.0)
        setAutoclickerSide(side) { config -> config.copy(cps = (progress * 30.0).roundToInt().coerceIn(0, 30)) }
    }

    private fun setAutoclickerSide(side: AutoclickerSide, transform: (SideAutoclickerConfig) -> SideAutoclickerConfig) {
        BlazeDataStore.updateAutoclicker { config ->
            when (side) {
                AutoclickerSide.LEFT -> config.copy(left = transform(config.left))
                AutoclickerSide.RIGHT -> config.copy(right = transform(config.right))
            }
        }
    }

    private fun bindLabel(bind: BlazeInputBind): String {
        if (!bind.isBound()) {
            return "Unbound"
        }
        val key = when (bind.type) {
            BlazeInputType.KEYSYM -> InputConstants.Type.KEYSYM.getOrCreate(bind.value)
            BlazeInputType.MOUSE -> InputConstants.Type.MOUSE.getOrCreate(bind.value)
        }
        return key.displayName.string
    }

    private fun measureModuleState(layout: ClickGuiLayout, module: BlazeModule): ModuleRenderState {
        fun s(value: Int): Int = (value * layout.uiScale).toInt()

        val enabledProgress = animateValue("enabled:${module.id}", if (BlazeDataStore.isModuleEnabled(module.id)) 1f else 0f, 0.34f)
        val expandedTarget = if (expandedModuleId == module.id && module.expandable) 1f else 0f
        val expandedProgress = animateValue("expanded:${module.id}", expandedTarget, 0.38f)
        val headerHeight = s(40)

        if (module.id == BlazeModuleIds.AUTOCLICKER) {
            val leftReveal = animateValue("autoclicker:left", if (expandedTarget > 0f && leftAutoclickerExpanded) 1f else 0f, 0.42f)
            val rightReveal = animateValue("autoclicker:right", if (expandedTarget > 0f && rightAutoclickerExpanded) 1f else 0f, 0.42f)
            val baseExpanded = (s(68) * expandedProgress).roundToInt()
            val sectionHeight = s(134)
            val expandedHeight = baseExpanded + (sectionHeight * leftReveal).roundToInt() + (sectionHeight * rightReveal).roundToInt()
            return ModuleRenderState(enabledProgress, expandedProgress, leftReveal, rightReveal, headerHeight + expandedHeight)
        }

        val childrenHeight = if (module.children.isNotEmpty()) {
            (s(12) + module.children.size * s(18) + s(8)) * expandedProgress
        } else {
            0f
        }
        return ModuleRenderState(enabledProgress, expandedProgress, 0f, 0f, headerHeight + childrenHeight.roundToInt())
    }

    private fun animateValue(key: String, target: Float, speed: Float): Float {
        val current = animationValues[key] ?: target
        val adjustedSpeed = if (target < current) speed * 1.28f else speed
        val next = if (abs(target - current) < 0.018f) target else current + (target - current) * adjustedSpeed
        animationValues[key] = next
        return next
    }

    private data class CategoryRegion(
        val category: BlazeCategory,
        val bounds: GuiRect
    )

    private data class ModuleCardRegion(
        val module: BlazeModule,
        val headerBounds: GuiRect
    )

    private data class ModuleRenderState(
        val enabledProgress: Float,
        val expandedProgress: Float,
        val leftRevealProgress: Float,
        val rightRevealProgress: Float,
        val totalHeight: Int
    )

    private fun easeOutCubic(value: Float): Float {
        val inverted = 1f - value.coerceIn(0f, 1f)
        return 1f - inverted * inverted * inverted
    }

    private fun withAlpha(color: Int, multiplier: Float): Int {
        val alpha = (color ushr 24) and 0xFF
        val scaled = (alpha * multiplier).toInt().coerceIn(0, 255)
        return (color and 0x00FFFFFF) or (scaled shl 24)
    }
}
