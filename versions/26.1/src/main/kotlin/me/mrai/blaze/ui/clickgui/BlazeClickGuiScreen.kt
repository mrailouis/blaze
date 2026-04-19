package me.mrai.blaze.ui.clickgui

import com.mojang.blaze3d.platform.InputConstants
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin
import me.mrai.blaze.config.BlazeDataStore
import me.mrai.blaze.feature.autoclicker.AutoclickerActivationMode
import me.mrai.blaze.feature.autoclicker.AutoclickerSide
import me.mrai.blaze.feature.autoclicker.BlazeInputBind
import me.mrai.blaze.feature.autoclicker.BlazeInputType
import me.mrai.blaze.feature.autoclicker.SideAutoclickerConfig
import me.mrai.blaze.meta.BlazeMetadata
import me.mrai.blaze.render.gui.BlazeColorPalette
import me.mrai.blaze.render.gui.GuiPrimitives
import me.mrai.blaze.render.gui.GuiRect
import me.mrai.blaze.feature.module.BlazeCategory
import me.mrai.blaze.feature.module.BlazeModule
import me.mrai.blaze.feature.module.BlazeModuleIds
import me.mrai.blaze.ui.font.BlazeText
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
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
    private val wrappedDescriptionCache = mutableMapOf<WrappedTextKey, List<String>>()
    private val categoryRegions = mutableListOf<CategoryRegion>()
    private val moduleCardRegions = mutableListOf<ModuleCardRegion>()
    private val autoclickerHeaderRegions = mutableMapOf<AutoclickerSide, GuiRect>()
    private val autoclickerSliderRegions = mutableMapOf<AutoclickerSide, GuiRect>()
    private val autoclickerToggleRegions = mutableMapOf<AutoclickerSide, GuiRect>()
    private val autoclickerModeRegions = mutableMapOf<AutoclickerSide, GuiRect>()
    private val autoclickerModeOptionRegions = mutableListOf<ModeOptionRegion>()
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
    private var moduleScrollTarget = 0f
    private var moduleScrollVelocity = 0f
    private var moduleScrollMax = 0f
    private var activeSliderSide: AutoclickerSide? = null
    private var awaitingBindSide: AutoclickerSide? = null
    private var openModeDropdownSide: AutoclickerSide? = null
    private var leftAutoclickerExpanded = true
    private var rightAutoclickerExpanded = false

    override fun init() {
        super.init()
        openedAtNanos = System.nanoTime()
        val layout = ClickGuiLayout.compute(width, height, panelX, panelY)
        panelX = layout.panel.x
        panelY = layout.panel.y
    }

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        val openProgress = ((System.nanoTime() - openedAtNanos) / 120_000_000.0).toFloat().coerceIn(0f, 1f)
        val eased = easeOutCubic(openProgress)
        graphics.fill(0, 0, width, height, withAlpha(BlazeColorPalette.SCREEN_SCRIM, eased))

        val layout = ClickGuiLayout.compute(width, height, panelX, panelY)
        categoryRegions.clear()
        moduleCardRegions.clear()
        autoclickerHeaderRegions.clear()
        autoclickerSliderRegions.clear()
        autoclickerToggleRegions.clear()
        autoclickerModeRegions.clear()
        autoclickerModeOptionRegions.clear()
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
                moduleScrollTarget = 0f
                moduleScrollVelocity = 0f
                activeSliderSide = null
                awaitingBindSide = null
                openModeDropdownSide = null
                return true
            }
            moduleCardRegions.firstOrNull { it.headerBounds.contains(mouseButtonEvent.x(), mouseButtonEvent.y()) }?.let { region ->
                openModeDropdownSide = null
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
                    openModeDropdownSide = null
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
            moduleScrollVelocity -= scrollY.toFloat() * 9f
            moduleScrollTarget = (moduleScrollTarget - scrollY.toFloat() * 26f).coerceIn(0f, moduleScrollMax)
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

    private fun renderPanelShell(graphics: GuiGraphicsExtractor, layout: ClickGuiLayout) {
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

    private fun renderHeader(graphics: GuiGraphicsExtractor, layout: ClickGuiLayout) {
        fun s(value: Int): Int = (value * layout.uiScale).toInt()

        val titleX = layout.panel.x + s(24)
        val titleY = layout.panel.y + s(22)
        val titleScale = 2.35f * layout.uiScale
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
            y = titleY + s(14),
            color = BlazeColorPalette.TEXT_MUTED,
            scale = 0.82f * layout.uiScale,
            bold = true
        )
    }

    private fun renderCategoryRail(graphics: GuiGraphicsExtractor, layout: ClickGuiLayout) {
        fun s(value: Int): Int = (value * layout.uiScale).toInt()

        BlazeText.draw(
            graphics = graphics,
            font = font,
            text = "Categories",
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
            if (selected) {
                val glowColor = withAlpha(BlazeColorPalette.TITLE_END, 0.38f)
                BlazeText.draw(graphics, font, category.displayName, buttonBounds.x + s(5), buttonBounds.y + s(9), glowColor, bold = true)
                BlazeText.draw(graphics, font, category.displayName, buttonBounds.x + s(7), buttonBounds.y + s(9), glowColor, bold = true)
                BlazeText.draw(graphics, font, category.displayName, buttonBounds.x + s(6), buttonBounds.y + s(8), glowColor, bold = true)
                BlazeText.draw(graphics, font, category.displayName, buttonBounds.x + s(6), buttonBounds.y + s(10), glowColor, bold = true)
            }
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

        val editHudScale = 0.82f
        val editHudWidth = BlazeText.widthScaled(font, "Edit HUDs", editHudScale, bold = true)
        val editHudHeight = BlazeText.heightScaled(font, "Edit HUDs", editHudScale, bold = true)
        val buttonWidth = editHudWidth + s(14)
        val buttonHeight = editHudHeight + s(8)
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
        drawCenteredText(graphics, buttonBounds, "Edit HUDs", BlazeColorPalette.TEXT_PRIMARY, scale = editHudScale, bold = true)
    }

    private fun renderModulePane(graphics: GuiGraphicsExtractor, layout: ClickGuiLayout) {
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

        val moduleStates = mutableListOf<Pair<BlazeModule, ModuleRenderState>>()
        var contentHeight = 0
        selectedCategory.modules.forEach { module ->
            val state = measureModuleState(layout, module, cardArea.width)
            moduleStates += module to state
            contentHeight += state.totalHeight + s(12)
        }
        if (contentHeight > 0) {
            contentHeight -= s(12)
        }
        moduleScrollMax = (contentHeight - cardArea.height).coerceAtLeast(0).toFloat()
        updateModuleScrollPhysics()

        val glowMargin = s(8)
        val scissorLeft = (cardArea.x - glowMargin).coerceAtLeast(layout.separator.x + s(6))
        val scissorTop = (cardArea.y - glowMargin).coerceAtLeast(layout.rightPane.y)
        val scissorRight = (cardArea.right + glowMargin).coerceAtMost(layout.panel.right - s(8))
        val scissorBottom = (cardArea.bottom + glowMargin).coerceAtMost(layout.panel.bottom - s(8))

        graphics.enableScissor(scissorLeft, scissorTop, scissorRight, scissorBottom)
        var currentOffset = 0
        val startY = cardArea.y - moduleScroll.roundToInt()
        moduleStates.forEach { (module, state) ->
            val cardRect = GuiRect(cardArea.x, startY + currentOffset, cardArea.width, state.totalHeight)
            if (cardRect.bottom >= scissorTop - s(16) && cardRect.y <= scissorBottom + s(16)) {
                renderModuleCard(graphics, layout, module, cardRect, state)
            }
            currentOffset += state.totalHeight + s(12)
        }
        graphics.disableScissor()
    }

    private fun renderModuleCard(
        graphics: GuiGraphicsExtractor,
        layout: ClickGuiLayout,
        module: BlazeModule,
        rect: GuiRect,
        state: ModuleRenderState
    ) {
        fun s(value: Int): Int = (value * layout.uiScale).toInt()

        val glowProgress = easeOutCubic(state.glowProgress)
        if (glowProgress > 0.01f) {
            GuiPrimitives.drawGradientShadow(
                graphics,
                rect,
                layout.elementRadius,
                withAlpha(BlazeColorPalette.GLOW_START, glowProgress * 0.95f),
                withAlpha(BlazeColorPalette.GLOW_END, glowProgress * 0.95f),
                layers = 6
            )
        }

        val cardColor = withAlpha(BlazeColorPalette.CARD_RIM, 0.10f)
        GuiPrimitives.drawRoundedFrame(graphics, rect, layout.elementRadius, cardColor, cardColor)

        val headerHeight = state.headerHeight
        val headerBounds = GuiRect(rect.x, rect.y, rect.width, headerHeight)
        moduleCardRegions += ModuleCardRegion(module, headerBounds)

        val dividerX = rect.x + rect.width / 3
        val dividerRect = GuiRect(dividerX, headerBounds.y + s(6), 1, headerBounds.height - s(12))
        GuiPrimitives.fillRoundedRect(graphics, dividerRect, 1, BlazeColorPalette.SEPARATOR)

        val leftZone = GuiRect(rect.x + s(6), headerBounds.y, rect.width / 3 - s(12), headerBounds.height)
        val rightZone = GuiRect(dividerX + s(8), headerBounds.y, rect.right - dividerX - s(16), headerBounds.height)
        drawWrappedCenteredText(graphics, leftZone, state.titleLines, BlazeColorPalette.TEXT_PRIMARY, scale = 0.92f, bold = true)
        drawWrappedCenteredText(graphics, rightZone, state.descriptionLines, BlazeColorPalette.TEXT_SECONDARY, scale = 0.84f)

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

    private fun renderAutoclickerContent(graphics: GuiGraphicsExtractor, layout: ClickGuiLayout, rect: GuiRect, state: ModuleRenderState) {
        fun s(value: Int): Int = (value * layout.uiScale).toInt()

        val config = BlazeDataStore.state.autoclicker ?: return
        var currentY = rect.y + s(6)
        currentY = renderAutoclickerSide(
            graphics,
            layout,
            rect,
            currentY,
            AutoclickerSide.LEFT,
            config.left,
            leftAutoclickerExpanded,
            state.leftRevealProgress,
            state.leftDropdownProgress
        )
        currentY += s(8)
        renderAutoclickerSide(
            graphics,
            layout,
            rect,
            currentY,
            AutoclickerSide.RIGHT,
            config.right,
            rightAutoclickerExpanded,
            state.rightRevealProgress,
            state.rightDropdownProgress
        )
    }

    private fun renderAutoclickerSide(
        graphics: GuiGraphicsExtractor,
        layout: ClickGuiLayout,
        parentRect: GuiRect,
        startY: Int,
        side: AutoclickerSide,
        config: SideAutoclickerConfig,
        expanded: Boolean,
        revealProgress: Float,
        dropdownProgress: Float
    ): Int {
        fun s(value: Int): Int = (value * layout.uiScale).toInt()

        val rowRect = GuiRect(parentRect.x + s(4), startY, parentRect.width - s(8), s(20))
        val toggleRect = GuiRect(rowRect.right - s(30), rowRect.y + s(3), s(26), s(14))
        val headerRect = GuiRect(rowRect.x, rowRect.y, rowRect.width - s(36), rowRect.height)
        val toggleProgress = animateValue("autoclicker:toggle:${side.name}", if (config.enabled) 1f else 0f, 0.28f)
        autoclickerHeaderRegions[side] = headerRect
        autoclickerToggleRegions[side] = toggleRect

        BlazeText.draw(
            graphics = graphics,
            font = font,
            text = side.displayName,
            x = headerRect.x + s(2),
            y = headerRect.y + s(6),
            color = if (expanded) BlazeColorPalette.TEXT_PRIMARY else BlazeColorPalette.TEXT_SECONDARY,
            bold = expanded
        )
        drawToggleSwitch(graphics, layout, toggleRect, toggleProgress)

        var currentY = rowRect.bottom + s(6)
        if (revealProgress > 0.02f) {
            val sectionHeight = autoclickerSectionHeight(layout, dropdownProgress)
            val visibleHeight = (sectionHeight * revealProgress).roundToInt().coerceAtLeast(1)
            val visibleRect = GuiRect(parentRect.x + s(4), currentY, parentRect.width - s(8), visibleHeight)
            val fullRect = GuiRect(parentRect.x + s(4), currentY, parentRect.width - s(8), sectionHeight)
            renderAutoclickerSection(graphics, layout, fullRect, visibleRect, side, config, revealProgress, dropdownProgress)
            currentY += visibleHeight
        }
        return currentY + s(4)
    }

    private fun renderAutoclickerSection(
        graphics: GuiGraphicsExtractor,
        layout: ClickGuiLayout,
        fullRect: GuiRect,
        visibleRect: GuiRect,
        side: AutoclickerSide,
        config: SideAutoclickerConfig,
        revealProgress: Float,
        dropdownProgress: Float
    ) {
        fun s(value: Int): Int = (value * layout.uiScale).toInt()

        graphics.enableScissor(visibleRect.x, visibleRect.y, visibleRect.right, visibleRect.bottom)
        val accentColor = BlazeColorPalette.TITLE_START
        val controlHeight = s(18)
        val rowGap = s(6)
        val controlRight = fullRect.right - s(4)
        val sliderWidth = s(92)
        val modeWidth = s(66)
        val bindWidth = s(100)
        val dropdownOptionHeight = s(16)
        val dropdownGap = s(3)
        var currentY = fullRect.y + s(2)

        val sliderHitRect = GuiRect(controlRight - sliderWidth, currentY + s(2), sliderWidth, s(14))
        val cpsValue = config.cps.toString()
        val cpsLabelY = currentY + s(5)

        if (revealProgress > 0.95f) {
            autoclickerSliderRegions[side] = sliderHitRect
        }

        BlazeText.draw(graphics, font, "CPS", fullRect.x + s(2), cpsLabelY, BlazeColorPalette.TEXT_SECONDARY)
        BlazeText.draw(
            graphics,
            font,
            cpsValue,
            sliderHitRect.x - BlazeText.width(font, cpsValue, bold = true) - s(6),
            cpsLabelY,
            accentColor,
            bold = true
        )
        drawSlider(graphics, layout, sliderHitRect, config.cps)
        currentY += controlHeight + rowGap

        val modeButton = GuiRect(controlRight - modeWidth, currentY, modeWidth, controlHeight)
        if (revealProgress > 0.95f) {
            autoclickerModeRegions[side] = modeButton
        }
        BlazeText.draw(graphics, font, "Mode", fullRect.x + s(2), currentY + s(5), BlazeColorPalette.TEXT_SECONDARY)
        drawDropdownButton(graphics, layout, modeButton, config.activationMode.displayName, dropdownProgress)
        currentY += controlHeight

        if (dropdownProgress > 0.02f) {
            currentY += s(4)
            val dropdownHeight = dropdownOptionHeight * 2 + dropdownGap + s(4)
            val visibleDropdownHeight = (dropdownHeight * dropdownProgress).roundToInt().coerceAtLeast(1)
            val dropdownRect = GuiRect(modeButton.x, currentY, modeButton.width, dropdownHeight)
            val dropdownVisibleBottom = (dropdownRect.y + visibleDropdownHeight).coerceAtMost(dropdownRect.bottom)
            graphics.enableScissor(dropdownRect.x, dropdownRect.y, dropdownRect.right, dropdownVisibleBottom)
            GuiPrimitives.drawRoundedFrame(
                graphics,
                dropdownRect,
                (dropdownRect.height / 2).coerceAtMost(layout.elementRadius),
                withAlpha(accentColor, 0.35f),
                withAlpha(BlazeColorPalette.CARD_RIM, 0.18f)
            )
            val toggleOption = GuiRect(dropdownRect.x + s(2), dropdownRect.y + s(2), dropdownRect.width - s(4), dropdownOptionHeight)
            val holdOption = GuiRect(toggleOption.x, toggleOption.bottom + dropdownGap, toggleOption.width, dropdownOptionHeight)
            if (revealProgress > 0.95f && dropdownProgress > 0.9f) {
                autoclickerModeOptionRegions += ModeOptionRegion(side, AutoclickerActivationMode.TOGGLE, toggleOption)
                autoclickerModeOptionRegions += ModeOptionRegion(side, AutoclickerActivationMode.HOLD, holdOption)
            }
            drawDropdownOption(graphics, layout, toggleOption, "Toggle", config.activationMode == AutoclickerActivationMode.TOGGLE)
            drawDropdownOption(graphics, layout, holdOption, "Hold", config.activationMode == AutoclickerActivationMode.HOLD)
            graphics.disableScissor()
            graphics.enableScissor(visibleRect.x, visibleRect.y, visibleRect.right, visibleRect.bottom)
            currentY = dropdownRect.bottom + rowGap
        } else {
            currentY += rowGap
        }

        val bindButton = GuiRect(controlRight - bindWidth, currentY, bindWidth, controlHeight)
        if (revealProgress > 0.95f) {
            autoclickerBindRegions[side] = bindButton
        }
        BlazeText.draw(graphics, font, "Bind", fullRect.x + s(2), currentY + s(5), BlazeColorPalette.TEXT_SECONDARY)
        val bindText = if (awaitingBindSide == side) "Press / Click" else bindLabel(config.bind)
        drawCompactControl(graphics, layout, bindButton, bindText, if (awaitingBindSide == side) 1f else 0f)
        graphics.disableScissor()
    }

    private fun renderModuleChildren(graphics: GuiGraphicsExtractor, layout: ClickGuiLayout, rect: GuiRect, module: BlazeModule) {
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

    private fun drawToggleSwitch(
        graphics: GuiGraphicsExtractor,
        layout: ClickGuiLayout,
        rect: GuiRect,
        progress: Float
    ) {
        val accentColor = BlazeColorPalette.TITLE_START
        val radius = (rect.height / 2).coerceAtMost(layout.elementRadius)
        val clampedProgress = progress.coerceIn(0f, 1f)
        GuiPrimitives.drawRoundedFrame(
            graphics,
            rect,
            radius,
            GuiPrimitives.mixColor(withAlpha(BlazeColorPalette.CARD_RIM, 0.95f), withAlpha(accentColor, 0.58f), clampedProgress),
            GuiPrimitives.mixColor(withAlpha(BlazeColorPalette.CARD_RIM, 0.14f), withAlpha(accentColor, 0.20f), clampedProgress)
        )
        val knobSize = (rect.height - 4).coerceAtLeast(2)
        val knobTravel = (rect.width - knobSize - 4).coerceAtLeast(0)
        val knobRect = GuiRect(
            x = rect.x + 2 + (knobTravel * clampedProgress).roundToInt(),
            y = rect.y + 2,
            width = knobSize,
            height = knobSize
        )
        GuiPrimitives.fillRoundedRect(
            graphics,
            knobRect,
            knobRect.height / 2,
            0xFFFFFFFF.toInt()
        )
    }

    private fun drawCompactControl(graphics: GuiGraphicsExtractor, layout: ClickGuiLayout, rect: GuiRect, text: String, highlightProgress: Float) {
        val accentColor = BlazeColorPalette.TITLE_START
        val radius = (rect.height / 2).coerceAtMost(layout.elementRadius)
        val progress = highlightProgress.coerceIn(0f, 1f)
        GuiPrimitives.drawRoundedFrame(
            graphics,
            rect,
            radius,
            GuiPrimitives.mixColor(withAlpha(BlazeColorPalette.CARD_RIM, 0.95f), withAlpha(accentColor, 0.6f), progress),
            GuiPrimitives.mixColor(withAlpha(BlazeColorPalette.CARD_RIM, 0.10f), withAlpha(accentColor, 0.12f), progress)
        )
        drawCenteredText(
            graphics,
            rect,
            text,
            GuiPrimitives.mixColor(BlazeColorPalette.TEXT_PRIMARY, accentColor, progress),
            scale = 0.84f,
            bold = true
        )
    }

    private fun drawDropdownButton(graphics: GuiGraphicsExtractor, layout: ClickGuiLayout, rect: GuiRect, text: String, progress: Float) {
        drawCompactControl(graphics, layout, rect, text, progress)
    }

    private fun drawDropdownOption(graphics: GuiGraphicsExtractor, layout: ClickGuiLayout, rect: GuiRect, text: String, selected: Boolean) {
        drawCompactControl(graphics, layout, rect, text, if (selected) 1f else 0f)
    }

    private fun drawSlider(graphics: GuiGraphicsExtractor, layout: ClickGuiLayout, rect: GuiRect, value: Int) {
        val accentColor = BlazeColorPalette.TITLE_START
        val track = GuiRect(rect.x, rect.y + rect.height / 2 - 1, rect.width, 3)
        GuiPrimitives.fillRoundedRect(graphics, track, 2, withAlpha(BlazeColorPalette.CARD_RIM, 0.85f))

        val progress = value.coerceIn(0, 30) / 30f
        val fillWidth = (track.width * progress).roundToInt().coerceAtLeast(3)
        GuiPrimitives.fillRoundedRect(graphics, GuiRect(track.x, track.y, fillWidth, track.height), 2, accentColor)
        val knobSize = 6
        val knobX = (track.x + (track.width * progress).roundToInt()).coerceIn(track.x, track.right)
        GuiPrimitives.fillRoundedRect(
            graphics,
            GuiRect(knobX - knobSize / 2, track.y - 1, knobSize, knobSize),
            knobSize / 2,
            accentColor
        )
    }

    private fun drawCenteredText(
        graphics: GuiGraphicsExtractor,
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

    private fun drawWrappedCenteredText(
        graphics: GuiGraphicsExtractor,
        rect: GuiRect,
        lines: List<String>,
        color: Int,
        scale: Float = 1f,
        bold: Boolean = false
    ) {
        if (lines.isEmpty()) {
            return
        }
        val lineHeight = BlazeText.heightScaled(font, "Ag", scale, bold)
        val totalHeight = lineHeight * lines.size
        var y = rect.y + ((rect.height - totalHeight) / 2).coerceAtLeast(0)
        lines.forEach { line ->
            val width = BlazeText.widthScaled(font, line, scale, bold)
            val x = rect.x + ((rect.width - width) / 2).coerceAtLeast(0)
            BlazeText.drawScaled(graphics, font, line, x, y, color, scale, bold = bold)
            y += lineHeight
        }
    }

    private fun handleModuleControlClick(mouseX: Double, mouseY: Double): Boolean {
        autoclickerModeOptionRegions.firstOrNull { it.bounds.contains(mouseX, mouseY) }?.let { region ->
            setAutoclickerSide(region.side) { config -> config.copy(activationMode = region.mode) }
            openModeDropdownSide = null
            return true
        }
        autoclickerSliderRegions.entries.firstOrNull { it.value.contains(mouseX, mouseY) }?.let { (side, _) ->
            openModeDropdownSide = null
            activeSliderSide = side
            updateSlider(side, mouseX)
            return true
        }
        autoclickerToggleRegions.entries.firstOrNull { it.value.contains(mouseX, mouseY) }?.let { (side, _) ->
            openModeDropdownSide = null
            setAutoclickerSide(side) { config ->
                config.copy(enabled = !config.enabled)
            }
            return true
        }
        autoclickerModeRegions.entries.firstOrNull { it.value.contains(mouseX, mouseY) }?.let { (side, _) ->
            openModeDropdownSide = if (openModeDropdownSide == side) null else side
            return true
        }
        autoclickerHeaderRegions.entries.firstOrNull { it.value.contains(mouseX, mouseY) }?.let { (side, _) ->
            openModeDropdownSide = null
            when (side) {
                AutoclickerSide.LEFT -> leftAutoclickerExpanded = !leftAutoclickerExpanded
                AutoclickerSide.RIGHT -> rightAutoclickerExpanded = !rightAutoclickerExpanded
            }
            return true
        }
        if (openModeDropdownSide != null) {
            openModeDropdownSide = null
        }
        return false
    }

    private fun handleBindButtonClick(mouseButtonEvent: MouseButtonEvent): Boolean {
        autoclickerBindRegions.entries.firstOrNull { it.value.contains(mouseButtonEvent.x(), mouseButtonEvent.y()) }?.let { (side, _) ->
            awaitingBindSide = side
            activeSliderSide = null
            openModeDropdownSide = null
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
        if (bind.type == BlazeInputType.MOUSE) {
            return when (bind.value) {
                0 -> "Left Click"
                1 -> "Right Click"
                2 -> "Middle Click"
                else -> "Mouse ${bind.value + 1}"
            }
        }
        val key = when (bind.type) {
            BlazeInputType.KEYSYM -> InputConstants.Type.KEYSYM.getOrCreate(bind.value)
            BlazeInputType.MOUSE -> InputConstants.Type.MOUSE.getOrCreate(bind.value)
        }
        return key.displayName.string
    }

    private fun measureModuleState(layout: ClickGuiLayout, module: BlazeModule, cardWidth: Int): ModuleRenderState {
        fun s(value: Int): Int = (value * layout.uiScale).toInt()

        val enabledTarget = if (BlazeDataStore.isModuleEnabled(module.id)) 1f else 0f
        val enabledProgress = animateValue("enabled:${module.id}", enabledTarget, 0.34f)
        val glowProgress = animateValue("glow:${module.id}", enabledTarget, 0.22f)
        val expandedTarget = if (expandedModuleId == module.id && module.expandable) 1f else 0f
        val expandedProgress = animateValue("expanded:${module.id}", expandedTarget, 0.58f)
        val titleWidth = (cardWidth / 3 - s(12)).coerceAtLeast(s(72))
        val descriptionWidth = ((cardWidth * 2) / 3 - s(26)).coerceAtLeast(s(96))
        val titleLines = wrapTextCached(module.name, titleWidth, 0.92f, bold = true, maxLines = 3)
        val descriptionLines = wrapTextCached(module.description, descriptionWidth, 0.84f, maxLines = 4)
        val titleLineHeight = BlazeText.heightScaled(font, "Ag", 0.92f, bold = true)
        val descriptionLineHeight = BlazeText.heightScaled(font, "Ag", 0.84f)
        val headerTextHeight = maxOf(titleLines.size * titleLineHeight, descriptionLines.size * descriptionLineHeight)
        val headerHeight = maxOf(s(44), s(14) + headerTextHeight)

        if (module.id == BlazeModuleIds.AUTOCLICKER) {
            val leftReveal = animateValue("autoclicker:left", if (expandedTarget > 0f && leftAutoclickerExpanded) 1f else 0f, 0.42f)
            val rightReveal = animateValue("autoclicker:right", if (expandedTarget > 0f && rightAutoclickerExpanded) 1f else 0f, 0.42f)
            val leftDropdown = animateValue("autoclicker:dropdown:${AutoclickerSide.LEFT.name}", if (openModeDropdownSide == AutoclickerSide.LEFT) 1f else 0f, 0.30f)
            val rightDropdown = animateValue("autoclicker:dropdown:${AutoclickerSide.RIGHT.name}", if (openModeDropdownSide == AutoclickerSide.RIGHT) 1f else 0f, 0.30f)
            val baseExpanded = (autoclickerCollapsedContentHeight(layout) * expandedProgress).roundToInt()
            val expandedHeight = baseExpanded +
                (autoclickerSectionHeight(layout, leftDropdown) * leftReveal).roundToInt() +
                (autoclickerSectionHeight(layout, rightDropdown) * rightReveal).roundToInt()
            return ModuleRenderState(enabledProgress, glowProgress, expandedProgress, leftReveal, rightReveal, leftDropdown, rightDropdown, headerHeight, titleLines, descriptionLines, headerHeight + expandedHeight)
        }

        val childrenHeight = if (module.children.isNotEmpty()) {
            (s(12) + module.children.size * s(18) + s(8)) * expandedProgress
        } else {
            0f
        }
        return ModuleRenderState(enabledProgress, glowProgress, expandedProgress, 0f, 0f, 0f, 0f, headerHeight, titleLines, descriptionLines, headerHeight + childrenHeight.roundToInt())
    }

    private fun animateValue(key: String, target: Float, speed: Float): Float {
        val current = animationValues[key] ?: target
        val adjustedSpeed = if (target < current) speed * 1.28f else speed
        val next = if (abs(target - current) < 0.018f) target else current + (target - current) * adjustedSpeed
        animationValues[key] = next
        return next
    }

    private fun autoclickerCollapsedContentHeight(layout: ClickGuiLayout): Int {
        fun s(value: Int): Int = (value * layout.uiScale).toInt()
        return s(74)
    }

    private fun autoclickerSectionHeight(layout: ClickGuiLayout, dropdownProgress: Float): Int {
        fun s(value: Int): Int = (value * layout.uiScale).toInt()
        val baseHeight = s(70)
        val dropdownHeight = (s(39) * dropdownProgress.coerceIn(0f, 1f)).roundToInt()
        return baseHeight + dropdownHeight
    }

    private fun updateModuleScrollPhysics() {
        moduleScrollTarget = moduleScrollTarget.coerceIn(0f, moduleScrollMax)
        moduleScroll = moduleScroll.coerceIn(0f, moduleScrollMax)
        moduleScrollVelocity *= 0.84f
        if (abs(moduleScrollVelocity) < 0.05f) {
            moduleScrollVelocity = 0f
        }
        moduleScrollTarget = (moduleScrollTarget + moduleScrollVelocity).coerceIn(0f, moduleScrollMax)
        moduleScroll += (moduleScrollTarget - moduleScroll) * 0.22f
        if (abs(moduleScrollTarget - moduleScroll) < 0.12f && abs(moduleScrollVelocity) < 0.08f) {
            moduleScroll = moduleScrollTarget
        }
    }

    private fun animateRect(key: String, target: GuiRect, speed: Float): GuiRect {
        return GuiRect(
            x = animateValue("$key:x", target.x.toFloat(), speed).roundToInt(),
            y = animateValue("$key:y", target.y.toFloat(), speed).roundToInt(),
            width = animateValue("$key:width", target.width.toFloat(), speed).roundToInt(),
            height = animateValue("$key:height", target.height.toFloat(), speed).roundToInt()
        )
    }

    private fun wrapTextCached(text: String, maxWidth: Int, scale: Float, bold: Boolean = false, maxLines: Int = Int.MAX_VALUE): List<String> {
        val key = WrappedTextKey(text, maxWidth, (scale * 1000f).roundToInt(), bold, maxLines)
        return wrappedDescriptionCache.getOrPut(key) {
            wrapText(text, maxWidth, scale, bold, maxLines)
        }
    }

    private fun wrapText(text: String, maxWidth: Int, scale: Float, bold: Boolean = false, maxLines: Int = Int.MAX_VALUE): List<String> {
        if (text.isBlank()) {
            return listOf("")
        }
        val words = text.split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) {
            return listOf(text)
        }

        val lines = mutableListOf<String>()
        var current = if (BlazeText.widthScaled(font, words.first(), scale, bold) <= maxWidth) {
            words.first()
        } else {
            val split = splitLongWord(words.first(), maxWidth, scale, bold)
            lines += split.dropLast(1)
            split.last()
        }
        for (word in words.drop(1)) {
            if (BlazeText.widthScaled(font, word, scale, bold) > maxWidth) {
                if (current.isNotEmpty()) {
                    lines += current
                    current = ""
                }
                val split = splitLongWord(word, maxWidth, scale, bold)
                lines += split.dropLast(1)
                current = split.lastOrNull().orEmpty()
                continue
            }
            val candidate = "$current $word"
            if (BlazeText.widthScaled(font, candidate, scale, bold) <= maxWidth) {
                current = candidate
            } else {
                lines += current
                current = word
            }
        }
        lines += current

        if (lines.size <= maxLines) {
            return lines
        }

        val trimmed = lines.take(maxLines).toMutableList()
        var lastLine = trimmed.last()
        while (BlazeText.widthScaled(font, "$lastLine...", scale, bold) > maxWidth && lastLine.isNotEmpty()) {
            lastLine = lastLine.dropLast(1)
        }
        trimmed[trimmed.lastIndex] = "$lastLine..."
        return trimmed
    }

    private fun splitLongWord(text: String, maxWidth: Int, scale: Float, bold: Boolean): List<String> {
        val lines = mutableListOf<String>()
        var current = ""
        text.forEach { char ->
            val candidate = current + char
            if (current.isNotEmpty() && BlazeText.widthScaled(font, candidate, scale, bold) > maxWidth) {
                lines += current
                current = char.toString()
            } else {
                current = candidate
            }
        }
        if (current.isNotEmpty()) {
            lines += current
        }
        return if (lines.isEmpty()) listOf(text) else lines
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
        val glowProgress: Float,
        val expandedProgress: Float,
        val leftRevealProgress: Float,
        val rightRevealProgress: Float,
        val leftDropdownProgress: Float,
        val rightDropdownProgress: Float,
        val headerHeight: Int,
        val titleLines: List<String>,
        val descriptionLines: List<String>,
        val totalHeight: Int
    )

    private data class ModeOptionRegion(
        val side: AutoclickerSide,
        val mode: AutoclickerActivationMode,
        val bounds: GuiRect
    )

    private data class WrappedTextKey(
        val text: String,
        val maxWidth: Int,
        val scaleMilli: Int,
        val bold: Boolean,
        val maxLines: Int
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
