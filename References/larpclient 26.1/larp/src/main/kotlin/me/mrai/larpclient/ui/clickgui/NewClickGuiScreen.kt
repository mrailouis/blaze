package me.mrai.larpclient.ui.clickgui

import me.mrai.larpclient.features.impl.dungeons.f7.general.positionalmessages.PositionalMessagesModule
import me.mrai.larpclient.features.impl.skyblock.general.commandkeybinds.CommandKeybindsModule
import me.mrai.larpclient.module.ActionSetting
import me.mrai.larpclient.module.BoolSetting
import me.mrai.larpclient.module.ComponentSetting
import me.mrai.larpclient.module.InfoSetting
import me.mrai.larpclient.module.KeybindSetting
import me.mrai.larpclient.module.ModeSetting
import me.mrai.larpclient.module.Module
import me.mrai.larpclient.module.ModuleCategory
import me.mrai.larpclient.module.ModuleConfigManager
import me.mrai.larpclient.module.ModuleManager
import me.mrai.larpclient.module.Setting
import me.mrai.larpclient.module.SliderSetting
import me.mrai.larpclient.render.blur.BlurManager
import me.mrai.larpclient.ui.clickgui.config.ClickGuiColorConfigManager
import me.mrai.larpclient.ui.clickgui.config.ClickGuiConfigManager
import me.mrai.larpclient.ui.clickgui.render.RoundedRenderer
import me.mrai.larpclient.ui.font.ModTextRenderer
import me.mrai.larpclient.ui.hud.EditHudsScreen
import me.mrai.larpclient.util.VersionInfo
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.resources.Identifier
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import org.lwjgl.glfw.GLFW
import java.awt.Desktop
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import kotlin.math.round
import kotlin.math.roundToInt

class NewClickGuiScreen : Screen(Component.literal("LarpClient New GUI")) {
    companion object {
        private val COG_ICON_TEXTURE = Identifier.fromNamespaceAndPath("larpclient", "textures/gui/cog_gray.png")
        private val DISCORD_ICON_TEXTURE = Identifier.fromNamespaceAndPath("larpclient", "textures/gui/discord_gray.png")
        private val LINK_ICON_TEXTURE = Identifier.fromNamespaceAndPath("larpclient", "textures/gui/link_gray.png")

        fun open() {
            Minecraft.getInstance().setScreen(NewClickGuiScreen())
        }
    }

    private var uiScale = ClickGuiConfigManager.config.uiScale
    private fun s(value: Float): Float = value * uiScale

    private val colors get() = ClickGuiColorConfigManager.config
    private val shellTint get() = composeGuiAlpha(colors.backgroundColor, 0.16f * colors.backgroundAlpha.coerceIn(0.1f, 1f))
    private val panelTint get() = composeGuiAlpha(colors.topBarColor, 0.22f * colors.moduleAlpha.coerceIn(0.1f, 1f))
    private val raisedTint get() = composeGuiAlpha(colors.cardColor, 0.24f * colors.moduleAlpha.coerceIn(0.1f, 1f))
    private val hoverTint get() = composeGuiAlpha(colors.cardHoverColor, 0.28f * colors.moduleAlpha.coerceIn(0.1f, 1f))
    private val textPrimary get() = colors.textColor
    private val textSecondary get() = colors.softComponentColor
    private val textMuted get() = composeGuiAlpha(colors.softComponentColor, 0.76f)
    private val categoryPrimary get() = colors.moduleEnabledColor
    private val categorySecondary get() = colors.accentColor
    private val categoryTertiary get() = colors.accentOutlineColor
    private val toggleKnobColor get() = colors.accentOutlineStrongColor
    private val sliderActiveColor get() = colors.moduleListGradientStartColor

    private var selectedCategory = loadInitialCategory()
    private var searchText = ""
    private var searchFocused = false
    private val expandedModules = linkedSetOf<String>()
    private val expansionProgress = mutableMapOf<String, Float>()
    private var categoryScroll = 0f
    private var categoryScrollTarget = 0f

    private var panelX = Float.NaN
    private var panelY = Float.NaN
    private var moduleScroll = 0f
    private var moduleScrollTarget = 0f
    private val toggleAnimations = mutableMapOf<String, Float>()
    private var guiCustomizationExpanded = false
    private var draggingPanel = false
    private var panelDragOffsetX = 0f
    private var panelDragOffsetY = 0f
    private var draggingSlider: SliderSetting? = null
    private var draggingColorChannel: ColorSliderRegion? = null
    private var draggingSpacingSlider = false

    private var listeningForModuleBind = false
    private var listeningBindModule: Module? = null
    private var listeningForSettingBind = false
    private var listeningBindSetting: Pair<Module, KeybindSetting>? = null
    private var focusedComponentSetting: Pair<Module, ComponentSetting>? = null
    private var focusedPositionalMessageIndex: Pair<Module, Int>? = null
    private var focusedCommandKeybindIndex: Pair<Module, Int>? = null
    private var listeningCustomCommandKeybindIndex: Pair<Module, Int>? = null

    private var activeColorTarget: ColorTarget? = null
    private var activeComponentColorSetting: Pair<Module, ComponentSetting>? = null
    private var activeSliderColorGroup: Pair<Module, SliderColorGroup>? = null
    private var pickerState: PickerState? = null

    private val clickActions = mutableListOf<ClickAction>()
    private val sliderRegions = mutableListOf<SliderRegion>()
    private val colorSliderRegions = mutableListOf<ColorSliderRegion>()
    private val moduleCardRegions = mutableListOf<ModuleCardRegion>()
    private var spacingSliderRegion: Aabb? = null
    private var screenAnimProgress = 0f
    private var closing = false
    private var activeCategoryMarkerY = Float.NaN
    private var activeCategoryMarkerHeight = 0f

    private var searchAabb: Aabb? = null
    private var lastMouseX = 0f
    private var lastMouseY = 0f
    private var pickerPaletteRegion: Aabb? = null
    private var pickerHueRegion: Aabb? = null
    private var pickerAlphaRegion: Aabb? = null
    private var draggingPickerMode: PickerDragMode? = null
    private var guiCustomizationHeaderRegion: Aabb? = null
    private var panelDragRegion: Aabb? = null

    private val panelWidth get() = s(984f)
    private val panelHeight get() = s(632f)
    private val shellRadius get() = s(18f)
    private val leftPanelWidth get() = s(220f)
    private val topBarHeight get() = s(64f)
    private val cardRadius get() = s(9f)
    private val pillRadius get() = s(11f)
    private val elementSpacing get() = ClickGuiConfigManager.config.elementSpacing.coerceIn(8f, 24f)
    private val moduleCardGap get() = elementSpacing + s(2f)

    private enum class ColorTarget(val label: String) {
        PRIMARY_PANEL("Primary Panel Color"),
        SECONDARY_PANEL("Secondary Panel Color"),
        TERTIARY_PANEL("Tertiary Panel Color"),
        MODULE_HOVER("Module Hover Color"),
        DIVIDER("Divider Color"),
        MODULE_NAME("Module Name Color"),
        DESCRIPTION("Description Color"),
        CATEGORY("Category Color"),
        SUBCATEGORY("Subcategory Color"),
        SUB_SUBCATEGORY("Sub Sub Category Color"),
        TOGGLE_ON("Toggle On Color"),
        TOGGLE_OFF("Toggle Off Color"),
        TOGGLE_BUTTON("Toggle Button Color"),
        SLIDER_INACTIVE("Slider Inactive Color"),
        SLIDER_ACTIVE("Slider Active Color")
    }

    private enum class ColorChannel {
        RED, GREEN, BLUE, ALPHA
    }

    private enum class PickerDragMode {
        PALETTE, HUE, ALPHA
    }

    private data class NavEntry(
        val label: String,
        val level: Int,
        val category: ModuleCategory? = null
    )

    private sealed interface ModuleSettingEntry {
        data class Plain(val setting: Setting) : ModuleSettingEntry
        data class SliderColor(val group: SliderColorGroup) : ModuleSettingEntry
    }

    private data class SliderColorGroup(
        val label: String,
        val red: SliderSetting,
        val green: SliderSetting,
        val blue: SliderSetting,
        val alpha: SliderSetting?
    )

    private data class PickerState(
        var hue: Float,
        var saturation: Float,
        var value: Float,
        var alpha: Int
    )

    private data class Aabb(val x1: Float, val y1: Float, val x2: Float, val y2: Float) {
        fun contains(x: Double, y: Double): Boolean = x >= x1 && x <= x2 && y >= y1 && y <= y2
        fun contains(x: Float, y: Float): Boolean = x >= x1 && x <= x2 && y >= y1 && y <= y2
    }

    private data class ClickAction(val box: Aabb, val action: () -> Unit)
    private data class SliderRegion(val box: Aabb, val module: Module, val setting: SliderSetting)
    private data class ColorSliderRegion(val box: Aabb, val channel: ColorChannel)
    private data class ModuleCardRegion(val module: Module, val box: Aabb)
    override fun init() {
        val config = ClickGuiConfigManager.config
        val maxX = (width.toFloat() - panelWidth).coerceAtLeast(0f)
        val maxY = (height.toFloat() - panelHeight).coerceAtLeast(0f)
        val centeredX = ((width.toFloat() - panelWidth) / 2f).coerceIn(0f, maxX)
        val centeredY = ((height.toFloat() - panelHeight) / 2f).coerceIn(0f, maxY)

        if (panelX.isNaN() || panelY.isNaN() || !config.panelManuallyMoved) {
            panelX = centeredX
            panelY = if (config.panelY.isNaN() || !config.panelManuallyMoved) centeredY else config.panelY.coerceIn(0f, maxY)
        } else {
            panelX = config.panelX.coerceIn(0f, maxX)
            panelY = config.panelY.coerceIn(0f, maxY)
        }
        screenAnimProgress = 0f
        closing = false
    }

    override fun onClose() {
        ClickGuiConfigManager.config.panelX = panelX
        ClickGuiConfigManager.config.panelY = panelY
        ClickGuiConfigManager.config.uiScale = uiScale
        ClickGuiConfigManager.config.selectedCategory = selectedCategory.name
        ClickGuiConfigManager.save()
        ClickGuiColorConfigManager.save()
        super.onClose()
    }

    override fun isPauseScreen(): Boolean = false

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        super.extractRenderState(graphics, mouseX, mouseY, delta)

        val targetAnim = if (closing) 0f else 1f
        screenAnimProgress = animateValue(screenAnimProgress, targetAnim, if (closing) 0.28f else 0.22f)
        if (closing && screenAnimProgress <= 0.015f) {
            onClose()
            minecraft.setScreen(null)
            return
        }

        clickActions.clear()
        sliderRegions.clear()
        colorSliderRegions.clear()
        moduleCardRegions.clear()
        spacingSliderRegion = null
        pickerPaletteRegion = null
        pickerHueRegion = null
        pickerAlphaRegion = null
        guiCustomizationHeaderRegion = null
        panelDragRegion = null

        lastMouseX = mouseX.toFloat()
        lastMouseY = mouseY.toFloat()

        BlurManager.captureScreen()

        val anim = easeOutCubic(screenAnimProgress)
        val scaleAnim = (0.03f + 0.97f * anim).coerceIn(0.03f, 1f)

        val baseW = panelWidth
        val baseH = panelHeight
        val w = baseW * scaleAnim
        val h = baseH * scaleAnim
        val targetCenterX = panelX + baseW / 2f
        val targetCenterY = panelY + baseH / 2f
        val startCenterX = width / 2f
        val startCenterY = height / 2f
        val centerX = lerp(startCenterX, targetCenterX, anim)
        val centerY = lerp(startCenterY, targetCenterY, anim)
        val x = centerX - w / 2f
        val y = centerY - h / 2f
        val leftX = x + s(20f)
        val leftY = y + s(76f)
        val leftW = leftPanelWidth
        val leftH = h - s(96f)
        val contentX = leftX + leftW + s(18f)
        val contentY = leftY
        val contentW = w - (contentX - x) - s(20f)
        val contentH = leftH
        searchAabb = null
        categoryScroll = animateValue(categoryScroll, categoryScrollTarget, 0.22f)
        moduleScroll = animateValue(moduleScroll, moduleScrollTarget, 0.22f)

        blurPanel(graphics, x, y, w, h, shellRadius, shellTint)
        blurPanel(graphics, leftX, leftY, leftW, leftH, s(14f), panelTint)
        blurPanel(graphics, x + s(18f), y + s(16f), w - s(36f), topBarHeight - s(24f), s(14f), panelTint)
        blurPanel(graphics, contentX, contentY, contentW, contentH, s(16f), panelTint)

        val utilityLeftX = x + s(10f)
        val utilityLeftY = y - s(48f)
        val utilityLeftW = s(116f)
        val utilityLeftH = s(34f)
        blurPanel(graphics, utilityLeftX, utilityLeftY, utilityLeftW, utilityLeftH, s(12f), panelTint)

        val utilityRightH = s(34f)
        val utilityRightW = s(224f)
        val utilityRightX = x + w - utilityRightW - s(10f)
        val utilityRightY = y - s(48f)
        blurPanel(graphics, utilityRightX, utilityRightY, utilityRightW, utilityRightH, s(12f), panelTint)

        panelDragRegion = Aabb(x + s(18f), y + s(16f), x + w - s(18f), y + topBarHeight - s(8f))

        drawUtilityIcons(graphics, utilityLeftX, utilityLeftY, utilityLeftW, utilityLeftH)
        drawTopActions(graphics, utilityRightX, utilityRightY, utilityRightW, utilityRightH)
        drawTopBar(graphics, x + s(18f), y + s(16f), w - s(36f), topBarHeight - s(24f))
        drawLeftRail(graphics, leftX, leftY, leftW, leftH)
        drawModuleCards(graphics, contentX, contentY, contentW, contentH, mouseX.toFloat(), mouseY.toFloat())
    }

    private fun drawFloatingButtons(graphics: GuiGraphicsExtractor, x: Float, y: Float, w: Float, h: Float) {
        val labels = listOf("", "", "")
        val buttonW = s(26f)
        val gap = s(8f)
        val totalW = labels.size * buttonW + (labels.size - 1) * gap
        val startX = x + (w - totalW) / 2f
        labels.forEachIndexed { index, label ->
            val bx = startX + index * (buttonW + gap)
            val by = y + (h - s(24f)) / 2f
            drawPillButton(graphics, bx, by, buttonW, h - s(10f), label) {
                when (index) {
                    0 -> openPath(FabricLoader.getInstance().gameDir)
                    1 -> openUri("https://discord.com/app")
                    else -> openPath(FabricLoader.getInstance().gameDir.resolve("docs"))
                }
            }
        }
    }

    private fun drawTopActions(graphics: GuiGraphicsExtractor, x: Float, y: Float, w: Float, h: Float) {
        val buttonH = h - s(10f)
        val searchX = x + s(6f)
        val searchW = w - s(12f)
        val searchY = y + (h - buttonH) / 2f
        searchAabb = Aabb(searchX, searchY, searchX + searchW, searchY + buttonH)
        blurPanel(graphics, searchX, searchY, searchW, buttonH, pillRadius, if (searchFocused) hoverTint else raisedTint)
        val shown = if (searchText.isBlank()) "Search" else searchText
        val textColor = if (searchText.isBlank()) textMuted else textPrimary
        drawBodyText(graphics, shown, searchX + s(12f), centerTextY(searchY, buttonH, 0.84f), textColor, 0.84f)
        clickActions += ClickAction(searchAabb!!) {
            searchFocused = true
        }
    }

    private fun drawUtilityIcons(graphics: GuiGraphicsExtractor, x: Float, y: Float, w: Float, h: Float) {
        val iconInset = s(4f)
        val iconHeight = ((h - iconInset * 2f) * 0.74f).coerceAtLeast(s(14f))
        val iconY = y + (h - iconHeight) / 2f
        val gap = s(10f)
        var iconX = x + s(17f)

        iconX = drawUtilityIcon(
            graphics,
            iconX,
            iconY,
            iconHeight,
            COG_ICON_TEXTURE
        ) {
            openLarpClientConfigFolder()
        } + gap

        iconX = drawUtilityIcon(
            graphics,
            iconX,
            iconY + (iconHeight - iconHeight * 0.82f) / 2f,
            iconHeight * 0.82f,
            DISCORD_ICON_TEXTURE,
            widthScale = 1.08f
        ) {
            openUri("https://discord.gg/ZdEbpc4j49")
        } + gap

        drawUtilityIcon(
            graphics,
            iconX,
            iconY,
            iconHeight,
            LINK_ICON_TEXTURE
        ) {
            openUri("https://larpclient.pages.dev/")
        }
    }

    private fun drawTopBar(graphics: GuiGraphicsExtractor, x: Float, y: Float, w: Float, h: Float) {
        val label = "LarpClient"
        val suffix = " • v${VersionInfo.version}"
        val barMidY = y + h / 2f
        val textY = barMidY - ModTextRenderer.renderedTextCenterOffsetCustom(label, textPrimary, 1.02f)
        val titleX = x + s(14f)
        drawGradientTitle(graphics, label, titleX, textY, 1.02f)
        drawTitleText(
            graphics,
            suffix,
            titleX + textWidth(label, 1.02f, true),
            textY,
            textPrimary,
            1.02f
        )
    }

    private fun navigationEntries(): List<NavEntry> {
        return listOf(
            NavEntry("Skyblock", 0),
            NavEntry("General", 1, ModuleCategory.SKYBLOCK_GENERAL),
            NavEntry("Golems", 1, ModuleCategory.SKYBLOCK_GOLEMS),
            NavEntry("Dungeons", 0),
            NavEntry("General", 1, ModuleCategory.DUNGEONS_GENERAL),
            NavEntry("Floor 7", 1),
            NavEntry("General", 2, ModuleCategory.DUNGEONS_F7_GENERAL),
            NavEntry("Predev", 2, ModuleCategory.DUNGEONS_F7_PREDEV),
            NavEntry("Phase 1", 2, ModuleCategory.DUNGEONS_F7_P1),
            NavEntry("Phase 2", 2, ModuleCategory.DUNGEONS_F7_P2),
            NavEntry("Phase 3", 2, ModuleCategory.DUNGEONS_F7_P3),
            NavEntry("Phase 4", 2, ModuleCategory.DUNGEONS_F7_P4),
            NavEntry("Phase 5", 2, ModuleCategory.DUNGEONS_F7_P5),
            NavEntry("Kuudra", 0),
            NavEntry("General", 1, ModuleCategory.KUUDRA_GENERAL),
            NavEntry("Phase 1", 1, ModuleCategory.KUUDRA_P1),
            NavEntry("Phase 2", 1, ModuleCategory.KUUDRA_P2),
            NavEntry("Phase 3", 1, ModuleCategory.KUUDRA_P3),
            NavEntry("Phase 4", 1, ModuleCategory.KUUDRA_P4),
            NavEntry("Misc", 0),
            NavEntry("UI", 1, ModuleCategory.MISC_UI),
            NavEntry("Other", 1, ModuleCategory.MISC_OTHER)
        )
    }

    private fun drawLeftRail(graphics: GuiGraphicsExtractor, x: Float, y: Float, w: Float, h: Float) {
        val innerX = x + s(18f)
        val innerW = w - s(36f)
        var cy = y + s(18f)
        val rowH = s(18f)
        val rowGap = s(5f)
        val rowW = innerW
        val buttonCardH = s(34f)
        val buttonY = y + h - buttonCardH - s(14f)
        val scrollTop = cy
        val scrollBottom = buttonY - s(18f)
        val navEntries = navigationEntries()
        val totalHeight = navEntries.size * (rowH + rowGap) - rowGap
        val maxScroll = (totalHeight - (scrollBottom - scrollTop)).coerceAtLeast(0f)
        categoryScrollTarget = categoryScrollTarget.coerceIn(0f, maxScroll)
        categoryScroll = categoryScroll.coerceIn(0f, maxScroll)
        cy -= categoryScroll
        val clipX1 = (x + s(8f)).roundToInt()
        val clipY1 = scrollTop.roundToInt()
        val clipX2 = (x + w - s(8f)).roundToInt()
        val clipY2 = scrollBottom.roundToInt()
        graphics.enableScissor(clipX1, clipY1, clipX2, clipY2)
        navEntries.forEach { entry ->
            val rowY = cy
            val rowBottom = rowY + rowH
            if (rowBottom >= scrollTop && rowY <= scrollBottom) {
                val active = entry.category != null && selectedCategory == entry.category
                val textScale = when (entry.level) {
                    0 -> 0.9f
                    1 -> 0.82f
                    else -> 0.76f
                }
                val textColor = when {
                    entry.level == 0 -> categoryPrimary
                    entry.level == 1 -> if (active) mixColor(categorySecondary, categoryPrimary, 0.28f) else categorySecondary
                    else -> if (active) mixColor(categoryTertiary, categoryPrimary, 0.24f) else categoryTertiary
                }
                val textX = innerX + when (entry.level) {
                    0 -> 0f
                    1 -> s(10f)
                    else -> s(22f)
                }
                if (active) {
                    val targetY = rowY + s(2f)
                    val targetH = rowH - s(4f)
                    activeCategoryMarkerY = if (activeCategoryMarkerY.isNaN()) targetY else animateValue(activeCategoryMarkerY, targetY, 0.22f)
                    activeCategoryMarkerHeight = animateValue(activeCategoryMarkerHeight, targetH, 0.22f)
                    fillRect(graphics, textX - s(9f), activeCategoryMarkerY, s(3f), activeCategoryMarkerHeight, categoryPrimary, s(2f))
                }
                if (entry.level == 0) {
                    drawTitleText(graphics, "§l${entry.label}", textX, centerTextY(rowY, rowH, textScale), textColor, textScale)
                } else {
                    drawBodyText(graphics, entry.label, textX, centerTextY(rowY, rowH, textScale), textColor, textScale)
                }
                if (entry.category != null) {
                    clickActions += ClickAction(Aabb(innerX, rowY, innerX + rowW, rowY + rowH)) {
                        selectedCategory = entry.category
                        moduleScroll = 0f
                        searchFocused = false
                    }
                }
            }
            cy += rowH + rowGap
        }
        graphics.disableScissor()

        if (maxScroll > 0f) {
            val trackX = x + w - s(10f)
            val trackY = scrollTop
            val trackH = scrollBottom - scrollTop
            fillRect(graphics, trackX, trackY, s(2f), trackH, 0x14FFFFFF)
            val thumbH = (trackH * ((scrollBottom - scrollTop) / totalHeight)).coerceAtLeast(s(18f))
            val thumbY = trackY + (trackH - thumbH) * (categoryScroll / maxScroll)
            fillRect(graphics, trackX, thumbY, s(2f), thumbH, 0x55D7FBFF, s(2f))
        }

        fillRect(graphics, innerX, buttonY - s(12f), rowW, 1f, colors.dividerColor)
        blurPanel(graphics, innerX, buttonY, rowW, buttonCardH, s(12f), panelTint)
        drawTitleText(graphics, "Edit HUDs", innerX + (rowW - textWidth("Edit HUDs", 0.86f, true)) / 2f, centerTextY(buttonY, buttonCardH, 0.86f), textPrimary, 0.86f)
        clickActions += ClickAction(Aabb(innerX, buttonY, innerX + rowW, buttonY + buttonCardH)) {
            BlurManager.captureScreen()
            Minecraft.getInstance().setScreen(EditHudsScreen())
        }
    }

    private fun drawModuleCards(
        graphics: GuiGraphicsExtractor,
        x: Float,
        y: Float,
        w: Float,
        h: Float,
        mouseX: Float,
        mouseY: Float
    ) {
        val modules = filteredModules()
        val title = if (searchText.isBlank()) selectedCategory.display else searchText
        drawTitleText(graphics, title, x + s(18f), y + s(16f), categoryPrimary, 1.12f)
        drawBodyText(graphics, "${modules.size} modules", x + s(18f), y + s(36f), textSecondary, 0.8f)
        val viewportTop = y + s(64f)
        val viewportBottom = y + h - s(18f)
        val clipTop = viewportTop + s(2f)
        val clipBottom = viewportBottom - s(2f)
        val cardX = x + s(16f)
        val cardW = w - s(32f)
        val virtualHeight = if (selectedCategory == ModuleCategory.MISC_UI && searchText.isBlank()) guiCustomizationCardHeight(cardW) + moduleCardGap else 0f
        val contentHeight = computeModuleAreaHeight(modules, cardW) + virtualHeight
        val visibleHeight = (viewportBottom - viewportTop).coerceAtLeast(s(40f))
        moduleScrollTarget = moduleScrollTarget.coerceIn(0f, (contentHeight - visibleHeight).coerceAtLeast(0f))
        moduleScroll = moduleScroll.coerceIn(0f, (contentHeight - visibleHeight).coerceAtLeast(0f))
        var cy = y + s(66f) - moduleScroll

        graphics.enableScissor(cardX.toInt(), clipTop.toInt(), (cardX + cardW).toInt(), clipBottom.toInt())

        if (selectedCategory == ModuleCategory.MISC_UI && searchText.isBlank()) {
            val drawnHeight = drawGuiCustomizationCard(graphics, cardX, cy, cardW, true)
            cy += drawnHeight + moduleCardGap
        }

        modules.forEach { module ->
            val target = if (expandedModules.contains(module.name)) 1f else 0f
            val progress = animateValue(expansionProgress[module.name] ?: target, target, 0.16f).also {
                expansionProgress[module.name] = if (kotlin.math.abs(it - target) < 0.01f) target else it
            }
            val cardHeight = computeModuleCardHeight(module, cardW, progress)
            val box = Aabb(cardX, cy, cardX + cardW, cy + cardHeight)
            moduleCardRegions += ModuleCardRegion(module, box)

            if (cy + cardHeight >= clipTop && cy <= clipBottom) {
                val hovered = box.contains(mouseX, mouseY)
                drawModuleCard(graphics, module, cardX, cy, cardW, cardHeight, progress, hovered, clipTop, clipBottom)
            }

            cy += cardHeight + moduleCardGap
        }

        graphics.disableScissor()
    }

    private fun drawModuleCard(
        graphics: GuiGraphicsExtractor,
        module: Module,
        x: Float,
        y: Float,
        w: Float,
        h: Float,
        progress: Float,
        hovered: Boolean,
        viewportTop: Float,
        viewportBottom: Float
    ) {
        val expanded = progress > 0.01f
        val bg = if (hovered) hoverTint else raisedTint
        blurPanel(graphics, x, y, w, h, cardRadius, bg)

        val leftColW = w * 0.32f
        val dividerX = x + leftColW
        val titleMaxW = (leftColW - s(28f)).roundToInt().coerceAtLeast(40)
        val titleLines = ModTextRenderer.wrapWords(module.name, titleMaxW, 1f).ifEmpty { listOf(module.name) }.take(3)
        val titleLineH = textHeight(1f)
        val titleGap = s(2f)
        val titleBlockH = titleLines.size * titleLineH + (titleLines.size - 1).coerceAtLeast(0) * titleGap
        val cardToggleW = s(38f)
        val cardToggleH = s(20f)
        val topSectionH = maxOf(s(68f), titleBlockH + s(26f) + cardToggleH)
        if (y + topSectionH > viewportBottom) return
        val leftBlockH = titleBlockH + s(12f) + cardToggleH
        val leftBlockY = y + s(10f) + (topSectionH - leftBlockH).coerceAtLeast(0f) / 2f
        var titleDrawY = leftBlockY
        titleLines.forEach { line ->
            val drawX = x + (leftColW - textWidth(line, 1f)) / 2f
            drawTitleText(graphics, line, drawX, titleDrawY, textPrimary, 1.0f)
            titleDrawY += titleLineH + titleGap
        }

        val dividerH = topSectionH - s(18f)
        fillRect(graphics, dividerX, y + (topSectionH - dividerH) / 2f, 1f, dividerH, colors.dividerColor)

        val cardToggleX = x + (leftColW - cardToggleW) / 2f
        val cardToggleY = leftBlockY + titleBlockH + s(12f)
        if (cardToggleY >= viewportTop && cardToggleY + cardToggleH <= viewportBottom) {
            drawToggle(graphics, cardToggleX, cardToggleY, cardToggleW, cardToggleH, module.enabled, "module:${module.name}")
            clickActions += ClickAction(Aabb(cardToggleX, cardToggleY, cardToggleX + cardToggleW, cardToggleY + cardToggleH)) {
                module.toggle()
            }
        }

        val descX = dividerX + s(16f)
        val descY = y + s(18f)
        val descMaxW = (x + w - s(18f) - descX).toInt()
        val descLines = ModTextRenderer.wrapWords(module.description.take(160), descMaxW, 0.8f).take(2)
        var lineY = descY
        descLines.forEach { line ->
            if (lineY >= viewportTop && lineY + textHeight(0.8f) <= viewportBottom) {
                drawBodyText(graphics, line, descX, lineY, textSecondary, 0.8f)
            }
            lineY += s(15f)
        }

        if (!expanded) {
            return
        }

        val clipTop = maxOf(viewportTop, y + topSectionH + s(6f))
        val clipBottom = minOf(viewportBottom, y + h - s(12f))
        if (clipBottom <= clipTop) return
        graphics.enableScissor((x + s(8f)).toInt(), clipTop.toInt(), (x + w - s(8f)).toInt(), clipBottom.toInt())

        var cy = y + topSectionH + s(14f)
        val rowRight = x + w - s(16f)
        val bindRowH = s(24f)
        val bindAdvance = s(34f)
        if (rowIntersects(cy, bindAdvance, clipTop, clipBottom)) {
            drawTitleText(graphics, "Toggle Key", x + s(18f), cy, textPrimary, 0.92f)
            val bindText = if (listeningForModuleBind && listeningBindModule === module) {
                "Press key..."
            } else {
                keyName(module.bindKey)
            }
            val fieldW = maxOf(s(86f), textWidth(bindText, 0.84f) + s(18f))
            val fieldX = rowRight - fieldW
            val fieldY = cy - s(2f)
            drawPillButton(graphics, fieldX, fieldY, fieldW, bindRowH, bindText) {
                listeningForModuleBind = true
                listeningBindModule = module
                listeningForSettingBind = false
                listeningBindSetting = null
                focusedComponentSetting = null
                focusedPositionalMessageIndex = null
            }
        }
        cy += bindAdvance

        moduleSettingEntries(module).forEach { entry ->
            when (entry) {
                is ModuleSettingEntry.SliderColor -> {
                    val rowY = cy
                    val rowH = s(24f)
                    if (rowIntersects(rowY, rowH, clipTop, clipBottom)) {
                        val rowX = x + s(18f)
                        val rowW = w - s(36f)
                        val swatch = sliderColorGroupColor(entry.group)
                        blurPanel(graphics, rowX, rowY, rowW, rowH, s(8f), raisedTint)
                        drawBodyText(graphics, entry.group.label, rowX + s(10f), centerTextY(rowY, rowH, 0.84f) + s(3f), textPrimary, 0.84f)
                        val swatchSize = s(14f)
                        val swatchX = rowX + rowW - swatchSize - s(8f)
                        fillRect(graphics, swatchX, rowY + (rowH - swatchSize) / 2f + s(4f), swatchSize, swatchSize, swatch, s(5f))
                        clickActions += ClickAction(Aabb(rowX, rowY, rowX + rowW, rowY + rowH)) {
                            toggleSliderColorPicker(module, entry.group)
                        }
                    }
                    cy += rowH + s(6f)
                    if (isActiveSliderColorGroup(module, entry.group)) {
                        if (rowIntersects(cy, s(132f), clipTop, clipBottom)) {
                            cy = drawInlinePicker(graphics, x + s(18f), cy, w - s(36f), true) + s(8f)
                        } else {
                            cy += s(140f)
                        }
                    }
                }

                is ModuleSettingEntry.Plain -> {
                    when (val setting = entry.setting) {
                        is BoolSetting -> {
                            val rowH = s(24f)
                            if (rowIntersects(cy, rowH, clipTop, clipBottom)) {
                                drawTitleText(graphics, setting.name, x + s(18f), cy, textPrimary, 0.92f)
                                val tx = rowRight - s(38f)
                                val ty = cy - s(1f)
                                drawToggle(graphics, tx, ty, s(38f), s(20f), setting.value, "setting:${module.name}:${setting.name}")
                                clickActions += ClickAction(Aabb(tx, ty, tx + s(38f), ty + s(20f))) {
                                    setting.value = !setting.value
                                    ModuleConfigManager.save()
                                }
                            }
                            cy += s(30f)
                        }

                        is SliderSetting -> {
                            val rowH = s(46f)
                            if (rowIntersects(cy, rowH, clipTop, clipBottom)) {
                                drawTitleText(graphics, setting.name, x + s(18f), cy, textPrimary, 0.92f)
                                val valueText = format(setting.value)
                                drawBodyText(graphics, valueText, rowRight - textWidth(valueText, 0.84f), cy + s(1f), textSecondary, 0.84f)
                                val barX = x + s(18f)
                                val barY = cy + s(26f)
                                val barW = w - s(36f)
                                val barH = s(3f)
                                val pct = ((setting.value - setting.min) / (setting.max - setting.min)).toFloat().coerceIn(0f, 1f)
                                fillRect(graphics, barX, barY, barW, barH, colors.sliderInactiveColor, s(3f))
                                fillRect(graphics, barX, barY, barW * pct, barH, sliderActiveColor, s(3f))
                                fillRect(graphics, barX + barW * pct - s(4f), barY - s(2.5f), s(8f), s(8f), toggleKnobColor, s(4f))
                                sliderRegions += SliderRegion(Aabb(barX, barY - s(6f), barX + barW, barY + barH + s(7f)), module, setting)
                            }
                            cy += rowH
                        }

                        is InfoSetting -> {
                            val rowH = s(34f)
                            if (rowIntersects(cy, rowH, clipTop, clipBottom)) {
                                drawTitleText(graphics, setting.name, x + s(18f), cy, textPrimary, 0.92f)
                                drawBodyText(graphics, setting.value, x + s(18f), cy + s(14f), textSecondary, 0.82f)
                            }
                            cy += rowH
                        }

                        is ActionSetting -> {
                            val rowH = s(24f)
                            if (rowIntersects(cy, rowH, clipTop, clipBottom)) {
                                drawTitleText(graphics, setting.name, x + s(18f), cy, textPrimary, 0.92f)
                                val label = setting.label
                                val fieldW = maxOf(s(104f), textWidth(label, 0.84f) + s(18f))
                                val fieldX = rowRight - fieldW
                                val fieldY = cy - s(2f)
                                drawPillButton(graphics, fieldX, fieldY, fieldW, s(22f), label) {
                                    setting.click()
                                }
                            }
                            cy += s(32f)
                        }

                        is ModeSetting -> {
                            val rowH = s(24f)
                            if (rowIntersects(cy, rowH, clipTop, clipBottom)) {
                                drawTitleText(graphics, setting.name, x + s(18f), cy, textPrimary, 0.92f)
                                val modeText = setting.selected
                                val fieldW = maxOf(s(80f), textWidth(modeText, 0.9f) + s(18f))
                                val fieldX = rowRight - fieldW
                                val fieldY = cy - s(2f)
                                drawPillButton(graphics, fieldX, fieldY, fieldW, s(22f), modeText) {
                                    setting.selectedIndex = (setting.selectedIndex + 1) % setting.modes.size
                                    ModuleConfigManager.save()
                                }
                            }
                            cy += s(32f)
                        }

                        is KeybindSetting -> {
                            val rowH = s(24f)
                            if (rowIntersects(cy, rowH, clipTop, clipBottom)) {
                                drawTitleText(graphics, setting.name, x + s(18f), cy, textPrimary, 0.92f)
                                val bindText = if (listeningForSettingBind && listeningBindSetting?.first == module && listeningBindSetting?.second == setting) {
                                    "Press key..."
                                } else {
                                    keyName(setting.key)
                                }
                                val fieldW = maxOf(s(86f), textWidth(bindText, 0.84f) + s(18f))
                                val fieldX = rowRight - fieldW
                                val fieldY = cy - s(2f)
                                drawPillButton(graphics, fieldX, fieldY, fieldW, s(22f), bindText) {
                                    listeningForSettingBind = true
                                    listeningBindSetting = module to setting
                                }
                            }
                            cy += s(32f)
                        }

                        is ComponentSetting -> {
                            if (isColorSetting(setting)) {
                                val rowY = cy
                                val rowH = s(24f)
                                if (rowIntersects(rowY, rowH, clipTop, clipBottom)) {
                                    val rowX = x + s(18f)
                                    val rowW = w - s(36f)
                                    val swatch = parseHexColor(setting.text) ?: 0xFFFFFFFF.toInt()
                                    blurPanel(graphics, rowX, rowY, rowW, rowH, s(8f), raisedTint)
                                    drawBodyText(graphics, setting.name, rowX + s(10f), centerTextY(rowY, rowH, 0.84f) + s(3f), textPrimary, 0.84f)
                                    val swatchSize = s(14f)
                                    val swatchX = rowX + rowW - swatchSize - s(8f)
                                    fillRect(graphics, swatchX, rowY + (rowH - swatchSize) / 2f + s(4f), swatchSize, swatchSize, swatch, s(5f))
                                    clickActions += ClickAction(Aabb(rowX, rowY, rowX + rowW, rowY + rowH)) {
                                        toggleComponentPicker(module, setting)
                                    }
                                }
                                cy += rowH + s(6f)
                                if (activeComponentColorSetting?.first == module && activeComponentColorSetting?.second == setting) {
                                    if (rowIntersects(cy, s(132f), clipTop, clipBottom)) {
                                        cy = drawInlinePicker(graphics, x + s(18f), cy, w - s(36f), true) + s(8f)
                                    } else {
                                        cy += s(140f)
                                    }
                                }
                            } else {
                                val rowH = s(54f)
                                if (rowIntersects(cy, rowH, clipTop, clipBottom)) {
                                    drawTitleText(graphics, setting.name, x + s(18f), cy, textPrimary, 0.92f)
                                    val fieldY = cy + s(18f)
                                    val fieldH = s(24f)
                                    val fieldW = w - s(36f)
                                    val focused = focusedComponentSetting?.first == module && focusedComponentSetting?.second == setting
                                    blurPanel(graphics, x + s(18f), fieldY, fieldW, fieldH, s(8f), if (focused) hoverTint else raisedTint)
                                    val shown = if (setting.text.isBlank()) "Enter value..." else setting.text
                                    val textColor = if (setting.text.isBlank()) textMuted else textPrimary
                                    drawBodyText(graphics, shown, x + s(28f), centerTextY(fieldY, fieldH, 0.84f) + s(2f), textColor, 0.84f)
                                    clickActions += ClickAction(Aabb(x + s(18f), fieldY, x + s(18f) + fieldW, fieldY + fieldH)) {
                                        focusedComponentSetting = module to setting
                                        focusedPositionalMessageIndex = null
                                        activeComponentColorSetting = null
                                        activeSliderColorGroup = null
                                    }
                                }
                                cy += rowH
                            }
                        }
                    }
                }
            }
        }

        if (module === PositionalMessagesModule) {
            cy = drawPositionalMessages(graphics, module, x, cy, w)
        }
        if (module === CommandKeybindsModule) {
            cy = drawCommandKeybinds(graphics, module, x, cy, w)
        }
        graphics.disableScissor()

    }

    private fun guiCustomizationCardHeight(w: Float): Float {
        if (!guiCustomizationExpanded) return s(58f)
        var height = s(72f)
        ColorTarget.entries.forEach { target ->
            height += s(30f)
            if (activeColorTarget == target && pickerState != null) {
                height += s(128f)
            }
        }
        height += s(34f)
        height += s(34f)
        height += s(44f)
        return height
    }

    private fun drawGuiCustomizationCard(graphics: GuiGraphicsExtractor, x: Float, y: Float, w: Float, interactive: Boolean): Float {
        val h = guiCustomizationCardHeight(w)
        blurPanel(graphics, x, y, w, h, s(14f), panelTint)
        guiCustomizationHeaderRegion = Aabb(x, y, x + w, y + s(58f))
        drawTitleText(graphics, "ClickGUI Colors", x + s(18f), y + s(14f), textPrimary, 1f)
        drawBodyText(graphics, "Theme colors, world glow, toast toggle and layout spacing", x + s(18f), y + s(34f), textMuted, 0.8f)
        if (!guiCustomizationExpanded) {
            return h
        }
        var cy = y + s(56f)
        ColorTarget.entries.forEach { target ->
            val rowX = x + s(18f)
            val rowW = w - s(36f)
            val rowH = s(22f)
            blurPanel(graphics, rowX, cy, rowW, rowH, s(8f), raisedTint)
            drawBodyText(graphics, target.label, rowX + s(10f), centerTextY(cy, rowH, 0.8f) + s(3f), textPrimary, 0.8f)
            val swatchSize = s(14f)
            val swatchX = rowX + rowW - swatchSize - s(8f)
            fillRect(graphics, swatchX, cy + (rowH - swatchSize) / 2f + s(4f), swatchSize, swatchSize, getColor(target), s(5f))
            if (interactive) {
                clickActions += ClickAction(Aabb(rowX, cy, rowX + rowW, cy + rowH)) {
                    openInlinePicker(target)
                }
            }
            cy += s(30f)
            if (activeColorTarget == target && pickerState != null) {
                cy = drawInlinePicker(graphics, rowX, cy, rowW, interactive) + s(8f)
            }
        }

        val rowX = x + s(18f)
        val rowW = w - s(36f)
        val glowY = cy
        val glowH = s(24f)
        blurPanel(graphics, rowX, glowY, rowW, glowH, s(8f), raisedTint)
        drawBodyText(graphics, "World Glow", rowX + s(10f), centerTextY(glowY, glowH, 0.8f) + s(3f), textPrimary, 0.8f)
        val toggleW = s(38f)
        val toggleH = s(20f)
        val toggleX = rowX + rowW - toggleW - s(8f)
        val glowToggleY = glowY + (glowH - toggleH) / 2f
        drawToggle(graphics, toggleX, glowToggleY, toggleW, toggleH, ClickGuiConfigManager.config.worldGlow, "ui:world_glow")
        if (interactive) {
            clickActions += ClickAction(Aabb(rowX, glowY, rowX + rowW, glowY + glowH)) {
                ClickGuiConfigManager.config.worldGlow = !ClickGuiConfigManager.config.worldGlow
                ClickGuiConfigManager.save()
            }
        }
        cy += s(34f)

        val toastY = cy
        val toastH = s(24f)
        blurPanel(graphics, rowX, toastY, rowW, toastH, s(8f), raisedTint)
        drawBodyText(graphics, "Toasts", rowX + s(10f), centerTextY(toastY, toastH, 0.8f) + s(3f), textPrimary, 0.8f)
        val toastToggleY = toastY + (toastH - toggleH) / 2f
        drawToggle(graphics, toggleX, toastToggleY, toggleW, toggleH, ClickGuiConfigManager.config.showToasts, "ui:show_toasts")
        if (interactive) {
            clickActions += ClickAction(Aabb(rowX, toastY, rowX + rowW, toastY + toastH)) {
                ClickGuiConfigManager.config.showToasts = !ClickGuiConfigManager.config.showToasts
                ClickGuiConfigManager.save()
            }
        }
        cy += s(34f)

        val sliderY = cy + s(8f)
        drawBodyText(graphics, "Spacing", x + s(18f), sliderY - s(2f), textSecondary, 0.8f)
        val barX = x + s(74f)
        val barW = w - s(128f)
        val pct = ((ClickGuiConfigManager.config.elementSpacing - 8f) / 16f).coerceIn(0f, 1f)
        fillRect(graphics, barX, sliderY, barW, s(3f), colors.sliderInactiveColor, s(3f))
        fillRect(graphics, barX, sliderY, barW * pct, s(3f), sliderActiveColor, s(3f))
        fillRect(graphics, barX + barW * pct - s(4f), sliderY - s(3f), s(8f), s(8f), toggleKnobColor, s(4f))
        if (interactive) {
            spacingSliderRegion = Aabb(barX, sliderY - s(6f), barX + barW, sliderY + s(10f))
        }
        return h
    }

    private fun drawPositionalMessages(graphics: GuiGraphicsExtractor, module: Module, x: Float, startY: Float, w: Float): Float {
        var cy = startY + s(4f)
        val entries = PositionalMessagesModule.getEntries()

        if (entries.isEmpty()) {
            drawBodyText(
                graphics,
                "Use .larp posmsg add \"command\" radius to create entries.",
                x + s(16f),
                cy,
                textMuted,
                0.82f
            )
            return cy + s(28f)
        }

        entries.forEachIndexed { index, entry ->
            val rowY = cy
            val rowH = s(62f)
            blurPanel(graphics, x + s(16f), rowY, w - s(32f), rowH, s(9f), raisedTint)
            drawBodyText(
                graphics,
                "XYZ ${format(entry.x)}, ${format(entry.y)}, ${format(entry.z)}  R ${format(entry.radius)}",
                x + s(28f),
                rowY + s(8f),
                textMuted,
                0.8f
            )

            val fieldX = x + s(28f)
            val fieldY = rowY + s(28f)
            val fieldW = w - s(128f)
            val fieldH = s(22f)
            val focused = focusedPositionalMessageIndex == module to index
            blurPanel(graphics, fieldX, fieldY, fieldW, fieldH, s(8f), if (focused) hoverTint else panelTint)
            val shown = if (entry.command.isBlank()) "Enter command..." else entry.command
            drawBodyText(graphics, shown, fieldX + s(10f), centerTextY(fieldY, fieldH, 0.84f) + s(2f), if (entry.command.isBlank()) textMuted else textPrimary, 0.84f)
            clickActions += ClickAction(Aabb(fieldX, fieldY, fieldX + fieldW, fieldY + fieldH)) {
                focusedPositionalMessageIndex = module to index
                focusedComponentSetting = null
            }

            val deleteW = s(56f)
            val deleteX = x + w - deleteW - s(28f)
            drawPillButton(graphics, deleteX, fieldY, deleteW, fieldH, "Delete") {
                PositionalMessagesModule.remove(index)
            }

            cy += rowH + s(8f)
        }

        return cy
    }

    private fun drawCommandKeybinds(graphics: GuiGraphicsExtractor, module: Module, x: Float, startY: Float, w: Float): Float {
        var cy = startY + s(4f)
        val entries = CommandKeybindsModule.getEntries()

        val addW = s(82f)
        drawPillButton(graphics, x + w - addW - s(16f), cy, addW, s(22f), "Add Entry") {
            CommandKeybindsModule.addEntry()
        }
        drawBodyText(graphics, "Custom Commands", x + s(16f), cy + s(4f), textPrimary, 0.86f)
        cy += s(32f)

        if (entries.isEmpty()) {
            drawBodyText(
                graphics,
                "Add a row, enter a slash command, then bind a key.",
                x + s(16f),
                cy,
                textMuted,
                0.82f
            )
            return cy + s(28f)
        }

        entries.forEachIndexed { index, entry ->
            val rowY = cy
            val rowH = s(62f)
            blurPanel(graphics, x + s(16f), rowY, w - s(32f), rowH, s(9f), raisedTint)

            val fieldX = x + s(28f)
            val fieldY = rowY + s(20f)
            val fieldW = w - s(220f)
            val fieldH = s(22f)
            val focused = focusedCommandKeybindIndex == module to index
            blurPanel(graphics, fieldX, fieldY, fieldW, fieldH, s(8f), if (focused) hoverTint else panelTint)
            val shown = if (entry.command.isBlank()) "Enter /command..." else entry.command
            drawBodyText(
                graphics,
                shown,
                fieldX + s(10f),
                centerTextY(fieldY, fieldH, 0.84f) + s(2f),
                if (entry.command.isBlank()) textMuted else textPrimary,
                0.84f
            )
            clickActions += ClickAction(Aabb(fieldX, fieldY, fieldX + fieldW, fieldY + fieldH)) {
                focusedCommandKeybindIndex = module to index
                focusedComponentSetting = null
                focusedPositionalMessageIndex = null
                listeningCustomCommandKeybindIndex = null
            }

            val bindText = if (listeningCustomCommandKeybindIndex == module to index) "Press key..." else keyName(entry.key)
            val bindW = maxOf(s(86f), textWidth(bindText, 0.84f) + s(18f))
            val bindX = x + w - bindW - s(92f)
            drawPillButton(graphics, bindX, fieldY, bindW, fieldH, bindText) {
                listeningCustomCommandKeybindIndex = module to index
                focusedCommandKeybindIndex = null
            }

            val deleteW = s(56f)
            val deleteX = x + w - deleteW - s(28f)
            drawPillButton(graphics, deleteX, fieldY, deleteW, fieldH, "Delete") {
                CommandKeybindsModule.remove(index)
            }

            cy += rowH + s(8f)
        }

        return cy
    }

    override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        lastMouseX = event.x().toFloat()
        lastMouseY = event.y().toFloat()

        if (event.button() == 1) {
            guiCustomizationHeaderRegion?.takeIf { it.contains(event.x(), event.y()) }?.let {
                guiCustomizationExpanded = !guiCustomizationExpanded
                activeColorTarget = null
                activeComponentColorSetting = null
                activeSliderColorGroup = null
                pickerState = null
                return true
            }
            moduleCardRegions.firstOrNull { it.box.contains(event.x(), event.y()) }?.let { region ->
                if (!expandedModules.add(region.module.name)) {
                    expandedModules.remove(region.module.name)
                }
                return true
            }
        }

        searchFocused = searchAabb?.contains(event.x(), event.y()) == true

        panelDragRegion?.takeIf { event.button() == 0 && it.contains(event.x(), event.y()) }?.let {
            draggingPanel = true
            panelDragOffsetX = event.x().toFloat() - panelX
            panelDragOffsetY = event.y().toFloat() - panelY
            ClickGuiConfigManager.config.panelManuallyMoved = true
            return true
        }

        pickerPaletteRegion?.takeIf { it.contains(event.x(), event.y()) }?.let { region ->
            draggingPickerMode = PickerDragMode.PALETTE
            updatePickerPalette(region, event.x(), event.y())
            return true
        }
        pickerHueRegion?.takeIf { it.contains(event.x(), event.y()) }?.let { region ->
            draggingPickerMode = PickerDragMode.HUE
            updatePickerHue(region, event.x())
            return true
        }
        pickerAlphaRegion?.takeIf { it.contains(event.x(), event.y()) }?.let { region ->
            draggingPickerMode = PickerDragMode.ALPHA
            updatePickerAlpha(region, event.x())
            return true
        }

        spacingSliderRegion?.takeIf { it.contains(event.x(), event.y()) }?.let { region ->
            draggingSpacingSlider = true
            updateSpacing(region, event.x())
            return true
        }

        clickActions.firstOrNull { it.box.contains(event.x(), event.y()) }?.let {
            it.action()
            return true
        }

        focusedComponentSetting = null
        focusedPositionalMessageIndex = null

        sliderRegions.firstOrNull { it.box.contains(event.x(), event.y()) }?.let { region ->
            draggingSlider = region.setting
            updateSlider(region.setting, region.box, event.x())
            return true
        }

        colorSliderRegions.firstOrNull { it.box.contains(event.x(), event.y()) }?.let { region ->
            draggingColorChannel = region
            updateColorChannel(region, event.x())
            return true
        }

        return super.mouseClicked(event, doubleClick)
    }

    override fun mouseReleased(event: MouseButtonEvent): Boolean {
        draggingSlider = null
        draggingColorChannel = null
        draggingSpacingSlider = false
        draggingPickerMode = null
        draggingPanel = false
        return super.mouseReleased(event)
    }

    override fun mouseDragged(event: MouseButtonEvent, dx: Double, dy: Double): Boolean {
        if (draggingPanel) {
            panelX = (event.x().toFloat() - panelDragOffsetX).coerceIn(0f, (width.toFloat() - panelWidth).coerceAtLeast(0f))
            panelY = (event.y().toFloat() - panelDragOffsetY).coerceIn(0f, (height.toFloat() - panelHeight).coerceAtLeast(0f))
            return true
        }

        draggingSlider?.let { slider ->
            sliderRegions.firstOrNull { it.setting == slider }?.let { region ->
                updateSlider(region.setting, region.box, event.x())
                return true
            }
        }

        draggingColorChannel?.let { region ->
            updateColorChannel(region, event.x())
            return true
        }

        if (draggingSpacingSlider) {
            spacingSliderRegion?.let { updateSpacing(it, event.x()) }
            return true
        }

        when (draggingPickerMode) {
            PickerDragMode.PALETTE -> {
                pickerPaletteRegion?.let { updatePickerPalette(it, event.x(), event.y()) }
                return true
            }
            PickerDragMode.HUE -> {
                pickerHueRegion?.let { updatePickerHue(it, event.x()) }
                return true
            }
            PickerDragMode.ALPHA -> {
                pickerAlphaRegion?.let { updatePickerAlpha(it, event.x()) }
                return true
            }
            null -> {}
        }

        return super.mouseDragged(event, dx, dy)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, horizontalAmount: Double, verticalAmount: Double): Boolean {
        val leftX = panelX + s(20f)
        val leftY = panelY + s(76f)
        val leftW = leftPanelWidth
        val leftH = panelHeight - s(96f)
        if (Aabb(leftX, leftY, leftX + leftW, leftY + leftH).contains(mouseX, mouseY)) {
            val rowH = s(18f)
            val rowGap = s(5f)
            val buttonCardH = s(34f)
            val buttonY = leftY + leftH - buttonCardH - s(14f)
            val scrollTop = leftY + s(18f)
            val scrollBottom = buttonY - s(18f)
            val totalHeight = navigationEntries().size * (rowH + rowGap) - rowGap
            val maxScroll = (totalHeight - (scrollBottom - scrollTop)).coerceAtLeast(0f)
            categoryScrollTarget = (categoryScrollTarget - verticalAmount.toFloat() * s(26f)).coerceIn(0f, maxScroll)
            return true
        }

        val contentX = panelX + s(258f)
        val contentY = leftY
        val contentW = panelWidth - s(278f)
        val contentH = panelHeight - s(96f)
        if (Aabb(contentX, contentY, contentX + contentW, contentY + contentH).contains(mouseX, mouseY)) {
            val extra = if (selectedCategory == ModuleCategory.MISC_UI && searchText.isBlank()) guiCustomizationCardHeight(contentW - s(28f)) + moduleCardGap else 0f
            val visibleHeight = (contentH - s(82f)).coerceAtLeast(s(40f))
            val maxScroll = (computeModuleAreaHeight(filteredModules(), contentW - s(28f)) + extra - visibleHeight).coerceAtLeast(0f)
            moduleScrollTarget = (moduleScrollTarget - verticalAmount.toFloat() * s(34f)).coerceIn(0f, maxScroll)
            return true
        }

        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)
    }

    override fun charTyped(event: CharacterEvent): Boolean {
        val character = event.codepoint().toChar()
        if (character.isISOControl()) return super.charTyped(event)

        if (focusedComponentSetting != null) {
            val (_, setting) = focusedComponentSetting!!
            setting.text += character
            ModuleConfigManager.save()
            return true
        }

        if (focusedPositionalMessageIndex != null) {
            val (module, index) = focusedPositionalMessageIndex!!
            if (module === PositionalMessagesModule) {
                val entry = PositionalMessagesModule.getEntries().getOrNull(index) ?: return true
                PositionalMessagesModule.updateCommand(index, entry.command + character)
                return true
            }
        }

        if (focusedCommandKeybindIndex != null) {
            val (module, index) = focusedCommandKeybindIndex!!
            if (module === CommandKeybindsModule) {
                val entry = CommandKeybindsModule.getEntries().getOrNull(index) ?: return true
                CommandKeybindsModule.updateCommand(index, entry.command + character)
                return true
            }
        }

        if (searchFocused) {
            searchText += character
            return true
        }

        return super.charTyped(event)
    }

    override fun keyPressed(event: KeyEvent): Boolean {
        val key = event.key()

        if (listeningForModuleBind && listeningBindModule != null) {
            val module = listeningBindModule!!
            module.bindKey = if (key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_BACKSPACE || key == GLFW.GLFW_KEY_DELETE) {
                GLFW.GLFW_KEY_UNKNOWN
            } else {
                key
            }
            listeningForModuleBind = false
            listeningBindModule = null
            ModuleConfigManager.save()
            return true
        }

        if (listeningForSettingBind && listeningBindSetting != null) {
            val (_, setting) = listeningBindSetting!!
            setting.key = if (key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_BACKSPACE || key == GLFW.GLFW_KEY_DELETE) {
                GLFW.GLFW_KEY_UNKNOWN
            } else {
                key
            }
            listeningForSettingBind = false
            listeningBindSetting = null
            ModuleConfigManager.save()
            return true
        }

        if (key == GLFW.GLFW_KEY_ESCAPE || minecraft.options.keyInventory.matches(event) || key == GLFW.GLFW_KEY_RIGHT_SHIFT) {
            when {
                activeColorTarget != null -> {
                    activeColorTarget = null
                    pickerState = null
                }
                activeComponentColorSetting != null -> {
                    activeComponentColorSetting = null
                    pickerState = null
                }
                activeSliderColorGroup != null -> {
                    activeSliderColorGroup = null
                    pickerState = null
                }
                focusedComponentSetting != null -> focusedComponentSetting = null
                focusedPositionalMessageIndex != null -> focusedPositionalMessageIndex = null
                focusedCommandKeybindIndex != null -> focusedCommandKeybindIndex = null
                listeningCustomCommandKeybindIndex != null -> listeningCustomCommandKeybindIndex = null
                searchFocused -> searchFocused = false
                else -> {
                    requestClose()
                }
            }
            return true
        }

        if (searchFocused && key == GLFW.GLFW_KEY_BACKSPACE) {
            if (searchText.isNotEmpty()) {
                searchText = searchText.dropLast(1)
            }
            return true
        }

        if (focusedComponentSetting != null) {
            val (_, setting) = focusedComponentSetting!!
            when (key) {
                GLFW.GLFW_KEY_BACKSPACE -> {
                    setting.text = setting.text.dropLast(1)
                    ModuleConfigManager.save()
                    return true
                }

                GLFW.GLFW_KEY_DELETE -> {
                    setting.text = ""
                    ModuleConfigManager.save()
                    return true
                }

                GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                    focusedComponentSetting = null
                    return true
                }
            }
        }

        if (focusedPositionalMessageIndex != null) {
            val (module, index) = focusedPositionalMessageIndex!!
            if (module === PositionalMessagesModule) {
                val entry = PositionalMessagesModule.getEntries().getOrNull(index)
                when (key) {
                    GLFW.GLFW_KEY_BACKSPACE -> {
                        if (entry != null) PositionalMessagesModule.updateCommand(index, entry.command.dropLast(1))
                        return true
                    }

                    GLFW.GLFW_KEY_DELETE -> {
                        PositionalMessagesModule.updateCommand(index, "")
                        return true
                    }

                    GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                        focusedPositionalMessageIndex = null
                        return true
                    }
                }
            }
        }

        if (listeningCustomCommandKeybindIndex != null) {
            val (module, index) = listeningCustomCommandKeybindIndex!!
            if (module === CommandKeybindsModule) {
                val resolvedKey = if (key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_BACKSPACE || key == GLFW.GLFW_KEY_DELETE) {
                    GLFW.GLFW_KEY_UNKNOWN
                } else {
                    key
                }
                CommandKeybindsModule.updateKey(index, resolvedKey)
                listeningCustomCommandKeybindIndex = null
                return true
            }
        }

        if (focusedCommandKeybindIndex != null) {
            val (module, index) = focusedCommandKeybindIndex!!
            if (module === CommandKeybindsModule) {
                val entry = CommandKeybindsModule.getEntries().getOrNull(index)
                when (key) {
                    GLFW.GLFW_KEY_BACKSPACE -> {
                        if (entry != null) CommandKeybindsModule.updateCommand(index, entry.command.dropLast(1))
                        return true
                    }

                    GLFW.GLFW_KEY_DELETE -> {
                        CommandKeybindsModule.updateCommand(index, "")
                        return true
                    }

                    GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                        focusedCommandKeybindIndex = null
                        return true
                    }
                }
            }
        }

        return super.keyPressed(event)
    }

    private fun filteredModules(): List<Module> {
        if (searchText.isBlank()) {
            return ModuleManager.modulesIn(selectedCategory, "")
        }
        return ModuleManager.modules.filter {
            it.name.contains(searchText, true) || it.description.contains(searchText, true)
        }
    }

    private fun moduleSettingEntries(module: Module): List<ModuleSettingEntry> {
        val visibleSettings = module.settings.filter(Setting::isVisible)
        val out = mutableListOf<ModuleSettingEntry>()
        var index = 0
        while (index < visibleSettings.size) {
            val current = visibleSettings[index]
            val red = current as? SliderSetting
            val colorPrefix = red?.let { colorSliderPrefix(it.name, "Red") }
            if (red != null && colorPrefix != null && index + 2 < visibleSettings.size) {
                val green = visibleSettings[index + 1] as? SliderSetting
                val blue = visibleSettings[index + 2] as? SliderSetting
                val alpha = visibleSettings.getOrNull(index + 3) as? SliderSetting
                if (green != null && blue != null &&
                    colorSliderPrefix(green.name, "Green") == colorPrefix &&
                    colorSliderPrefix(blue.name, "Blue") == colorPrefix
                ) {
                    val alphaSetting = alpha?.takeIf { colorSliderPrefix(it.name, "Alpha") == colorPrefix }
                    out += ModuleSettingEntry.SliderColor(
                        SliderColorGroup(
                            label = colorGroupLabel(colorPrefix),
                            red = red,
                            green = green,
                            blue = blue,
                            alpha = alphaSetting
                        )
                    )
                    index += if (alphaSetting != null) 4 else 3
                    continue
                }
            }
            out += ModuleSettingEntry.Plain(current)
            index++
        }
        return out
    }

    private fun colorSliderPrefix(name: String, channel: String): String? {
        return when {
            name.equals(channel, true) -> ""
            name.endsWith(" $channel", true) -> name.dropLast(channel.length).trimEnd()
            else -> null
        }
    }

    private fun colorGroupLabel(prefix: String): String {
        return if (prefix.isBlank()) "Color" else "$prefix Color"
    }

    private fun sliderColorGroupColor(group: SliderColorGroup): Int {
        val red = group.red.value.roundToInt().coerceIn(0, 255)
        val green = group.green.value.roundToInt().coerceIn(0, 255)
        val blue = group.blue.value.roundToInt().coerceIn(0, 255)
        val alpha = group.alpha?.value?.roundToInt()?.coerceIn(0, 255) ?: 255
        return (alpha shl 24) or (red shl 16) or (green shl 8) or blue
    }

    private fun setSliderColorGroup(group: SliderColorGroup, color: Int) {
        group.red.value = ((color ushr 16) and 0xFF).toDouble()
        group.green.value = ((color ushr 8) and 0xFF).toDouble()
        group.blue.value = (color and 0xFF).toDouble()
        group.alpha?.value = ((color ushr 24) and 0xFF).toDouble()
        ModuleConfigManager.save()
    }

    private fun isActiveSliderColorGroup(module: Module, group: SliderColorGroup): Boolean {
        val active = activeSliderColorGroup ?: return false
        return active.first === module && active.second.red === group.red
    }

    private fun toggleSliderColorPicker(module: Module, group: SliderColorGroup) {
        if (isActiveSliderColorGroup(module, group) && pickerState != null) {
            activeSliderColorGroup = null
            pickerState = null
            return
        }
        val color = sliderColorGroupColor(group)
        val hsv = rgbToHsv((color ushr 16) and 0xFF, (color ushr 8) and 0xFF, color and 0xFF)
        pickerState = PickerState(
            hue = hsv[0],
            saturation = hsv[1],
            value = hsv[2],
            alpha = (color ushr 24) and 0xFF
        )
        activeSliderColorGroup = module to group
        activeColorTarget = null
        activeComponentColorSetting = null
        focusedComponentSetting = null
    }

    private fun rowIntersects(top: Float, height: Float, clipTop: Float, clipBottom: Float): Boolean {
        return top + height >= clipTop && top <= clipBottom
    }

    private fun computeModuleAreaHeight(modules: List<Module>, width: Float): Float {
        if (modules.isEmpty()) return s(40f)
        return modules.sumOf {
            val target = if (expandedModules.contains(it.name)) 1f else 0f
            val progress = expansionProgress[it.name] ?: target
            computeModuleCardHeight(it, width, progress).toDouble()
        }.toFloat() + moduleCardGap * (modules.size - 1) + s(24f)
    }

    private fun expandedContentHeight(module: Module): Float {
        var height = s(8f)
        height += s(34f)
        moduleSettingEntries(module).forEach { entry ->
            height += when (entry) {
                is ModuleSettingEntry.Plain -> when (val setting = entry.setting) {
                    is SliderSetting -> s(46f)
                    is InfoSetting -> s(34f)
                    is ActionSetting -> s(32f)
                    is ComponentSetting -> {
                        when {
                            isColorSetting(setting) && activeComponentColorSetting?.first == module && activeComponentColorSetting?.second == setting -> s(166f)
                            isColorSetting(setting) -> s(34f)
                            else -> s(54f)
                        }
                    }
                    else -> s(32f)
                }
                is ModuleSettingEntry.SliderColor -> {
                    if (isActiveSliderColorGroup(module, entry.group)) s(166f) else s(34f)
                }
            }
        }
        if (module === PositionalMessagesModule) {
            val entries = PositionalMessagesModule.getEntries()
            height += if (entries.isEmpty()) s(36f) else entries.size * s(66f)
        }
        if (module === CommandKeybindsModule) {
            val entries = CommandKeybindsModule.getEntries()
            height += s(40f)
            height += if (entries.isEmpty()) s(36f) else entries.size * s(70f)
        }
        return height + s(8f)
    }

    private fun computeModuleCardHeight(module: Module, width: Float, progress: Float): Float {
        val leftColW = width * 0.32f
        val titleMaxW = (leftColW - s(28f)).roundToInt().coerceAtLeast(40)
        val titleLines = ModTextRenderer.wrapWords(module.name, titleMaxW, 1f).ifEmpty { listOf(module.name) }.take(3)
        val titleBlockH = titleLines.size * textHeight(1f) + (titleLines.size - 1).coerceAtLeast(0) * s(2f)
        val cardToggleH = s(20f)
        val topSectionH = maxOf(s(68f), titleBlockH + s(26f) + cardToggleH)
        val collapsed = maxOf(s(96f), topSectionH + s(16f))
        return collapsed + expandedContentHeight(module) * progress.coerceIn(0f, 1f)
    }

    private fun updateSlider(setting: SliderSetting, box: Aabb, mouseX: Double) {
        val pct = ((mouseX - box.x1) / (box.x2 - box.x1)).coerceIn(0.0, 1.0)
        val raw = setting.min + (setting.max - setting.min) * pct
        setting.value = (round(raw / setting.step) * setting.step).coerceIn(setting.min, setting.max)
        ModuleConfigManager.save()
    }

    private fun updateColorChannel(region: ColorSliderRegion, mouseX: Double) {
        val current = activeEditedColor() ?: return
        val pct = ((mouseX - region.box.x1) / (region.box.x2 - region.box.x1)).coerceIn(0.0, 1.0)
        val value = (pct * 255.0).roundToInt().coerceIn(0, 255)

        val a = (current ushr 24) and 0xFF
        val r = (current ushr 16) and 0xFF
        val g = (current ushr 8) and 0xFF
        val b = current and 0xFF

        val newColor = when (region.channel) {
            ColorChannel.RED -> (a shl 24) or (value shl 16) or (g shl 8) or b
            ColorChannel.GREEN -> (a shl 24) or (r shl 16) or (value shl 8) or b
            ColorChannel.BLUE -> (a shl 24) or (r shl 16) or (g shl 8) or value
            ColorChannel.ALPHA -> (value shl 24) or (r shl 16) or (g shl 8) or b
        }

        when {
            activeColorTarget != null -> {
                setColor(activeColorTarget!!, newColor)
                ClickGuiColorConfigManager.save()
            }

            activeComponentColorSetting != null -> {
                val (_, setting) = activeComponentColorSetting!!
                setting.text = argbHex(newColor)
                ModuleConfigManager.save()
            }
        }
    }

    private fun updateSpacing(region: Aabb, mouseX: Double) {
        val pct = ((mouseX - region.x1) / (region.x2 - region.x1)).coerceIn(0.0, 1.0)
        ClickGuiConfigManager.config.elementSpacing = (8.0 + 16.0 * pct).toFloat()
        ClickGuiConfigManager.save()
    }

    private fun openInlinePicker(target: ColorTarget) {
        if (activeColorTarget == target && pickerState != null) {
            activeColorTarget = null
            pickerState = null
            return
        }
        val color = getColor(target)
        val hsv = rgbToHsv((color ushr 16) and 0xFF, (color ushr 8) and 0xFF, color and 0xFF)
        pickerState = PickerState(
            hue = hsv[0],
            saturation = hsv[1],
            value = hsv[2],
            alpha = (color ushr 24) and 0xFF
        )
        activeColorTarget = target
        activeComponentColorSetting = null
        activeSliderColorGroup = null
    }

    private fun toggleComponentPicker(module: Module, setting: ComponentSetting) {
        if (activeComponentColorSetting?.first == module && activeComponentColorSetting?.second == setting && pickerState != null) {
            activeComponentColorSetting = null
            pickerState = null
            return
        }
        val color = parseHexColor(setting.text) ?: return
        val hsv = rgbToHsv((color ushr 16) and 0xFF, (color ushr 8) and 0xFF, color and 0xFF)
        pickerState = PickerState(
            hue = hsv[0],
            saturation = hsv[1],
            value = hsv[2],
            alpha = (color ushr 24) and 0xFF
        )
        activeComponentColorSetting = module to setting
        activeColorTarget = null
        activeSliderColorGroup = null
        focusedComponentSetting = null
    }

    private fun updatePickerPalette(region: Aabb, mouseX: Double, mouseY: Double) {
        val picker = pickerState ?: return
        picker.saturation = ((mouseX - region.x1) / (region.x2 - region.x1)).toFloat().coerceIn(0f, 1f)
        picker.value = (1.0 - ((mouseY - region.y1) / (region.y2 - region.y1))).toFloat().coerceIn(0f, 1f)
        applyPickerColor(picker)
    }

    private fun updatePickerHue(region: Aabb, mouseX: Double) {
        val picker = pickerState ?: return
        picker.hue = (((mouseX - region.x1) / (region.x2 - region.x1)).toFloat() * 360f).coerceIn(0f, 360f)
        applyPickerColor(picker)
    }

    private fun updatePickerAlpha(region: Aabb, mouseX: Double) {
        val picker = pickerState ?: return
        picker.alpha = ((((mouseX - region.x1) / (region.x2 - region.x1)).toFloat()) * 255f).roundToInt().coerceIn(0, 255)
        applyPickerColor(picker)
    }

    private fun applyPickerColor(picker: PickerState) {
        val rgb = hsvToRgb(picker.hue, picker.saturation, picker.value)
        val color = (picker.alpha shl 24) or (rgb[0] shl 16) or (rgb[1] shl 8) or rgb[2]
        when {
            activeColorTarget != null -> {
                setColor(activeColorTarget!!, color)
                ClickGuiColorConfigManager.save()
            }

            activeComponentColorSetting != null -> {
                val (_, setting) = activeComponentColorSetting!!
                setting.text = argbHex(color)
                ModuleConfigManager.save()
            }

            activeSliderColorGroup != null -> {
                setSliderColorGroup(activeSliderColorGroup!!.second, color)
            }
        }
    }

    private fun drawInlinePicker(graphics: GuiGraphicsExtractor, x: Float, y: Float, width: Float, interactive: Boolean): Float {
        val picker = pickerState ?: return y
        val paletteSize = minOf(s(92f), width)
        val paletteX = x
        val paletteY = y
        drawColorSpectrum(graphics, paletteX, paletteY, paletteSize, paletteSize, picker)
        if (interactive) {
            pickerPaletteRegion = Aabb(paletteX, paletteY, paletteX + paletteSize, paletteY + paletteSize)
        }

        val barX = paletteX + paletteSize + s(12f)
        val barW = (width - paletteSize - s(12f)).coerceAtLeast(s(84f))
        val hueY = paletteY + s(18f)
        drawHueBar(graphics, barX, hueY, barW, s(10f), picker)
        if (interactive) {
            pickerHueRegion = Aabb(barX, hueY, barX + barW, hueY + s(10f))
        }

        val alphaY = hueY + s(28f)
        drawAlphaBar(graphics, barX, alphaY, barW, s(10f), picker)
        if (interactive) {
            pickerAlphaRegion = Aabb(barX, alphaY, barX + barW, alphaY + s(10f))
        }

        val previewY = alphaY + s(28f)
        val previewH = s(24f)
        blurPanel(graphics, barX, previewY, barW, previewH, s(8f), raisedTint)
        fillRect(graphics, barX + s(6f), previewY + s(5f), previewH - s(10f), previewH - s(10f), activeEditedColor() ?: 0xFFFFFFFF.toInt(), s(5f))
        return maxOf(paletteY + paletteSize, previewY + previewH)
    }

    private fun drawColorEditorWindow(graphics: GuiGraphicsExtractor) {
        val title = activeColorEditorTitle() ?: return
        val currentColor = activeEditedColor() ?: return

        val w = s(236f)
        val h = s(244f)
        val x = (panelX + panelWidth - w - s(14f)).coerceAtMost(width.toFloat() - w - s(8f))
        val y = (panelY + s(76f)).coerceAtMost(height.toFloat() - h - s(8f))

        blurPanel(graphics, x, y, w, h, s(12f), raisedTint)
        drawTitleText(graphics, title, x + s(14f), y + s(12f), textPrimary, 0.96f)
        drawPillButton(graphics, x + w - s(72f), y + s(8f), s(58f), s(22f), "Close") {
            activeColorTarget = null
            activeComponentColorSetting = null
        }

        val previewX = x + s(14f)
        val previewY = y + s(42f)
        blurPanel(graphics, previewX, previewY, w - s(28f), s(34f), s(8f), panelTint)
        fillRect(graphics, previewX + s(8f), previewY + s(7f), s(56f), s(20f), currentColor, s(6f))
        drawBodyText(graphics, argbHex(currentColor), previewX + s(74f), centerTextY(previewY, s(34f), 0.84f), textPrimary, 0.84f)

        var cy = previewY + s(48f)
        cy = drawColorChannel(graphics, x + s(14f), cy, w - s(28f), ColorChannel.RED)
        cy = drawColorChannel(graphics, x + s(14f), cy, w - s(28f), ColorChannel.GREEN)
        cy = drawColorChannel(graphics, x + s(14f), cy, w - s(28f), ColorChannel.BLUE)
        drawColorChannel(graphics, x + s(14f), cy, w - s(28f), ColorChannel.ALPHA)
    }

    private fun drawColorChannel(graphics: GuiGraphicsExtractor, x: Float, y: Float, width: Float, channel: ColorChannel): Float {
        val color = activeEditedColor() ?: return y
        val value = when (channel) {
            ColorChannel.RED -> (color ushr 16) and 0xFF
            ColorChannel.GREEN -> (color ushr 8) and 0xFF
            ColorChannel.BLUE -> color and 0xFF
            ColorChannel.ALPHA -> (color ushr 24) and 0xFF
        }
        val label = channel.name.lowercase().replaceFirstChar { it.uppercase() }
        drawTitleText(graphics, label, x, y, textPrimary, 0.88f)
        drawBodyText(graphics, value.toString(), x + width - textWidth(value.toString(), 0.84f), y + s(1f), textSecondary, 0.84f)
        val barY = y + s(16f)
        blurPanel(graphics, x, barY - s(2f), width, s(10f), s(5f), panelTint)
        fillRect(graphics, x, barY, width * (value / 255f), s(6f), 0xCCEEF3F8.toInt(), s(4f))
        fillRect(graphics, x + width * (value / 255f) - s(5f), barY - s(4f), s(10f), s(14f), 0xEAEFF6, s(5f))
        colorSliderRegions += ColorSliderRegion(Aabb(x, barY - s(6f), x + width, barY + s(12f)), channel)
        return y + s(38f)
    }

    private fun drawColorSpectrum(graphics: GuiGraphicsExtractor, x: Float, y: Float, w: Float, h: Float, picker: PickerState) {
        val stepX = 96
        val stepY = 96
        val cellW = w / stepX
        val cellH = h / stepY
        for (iy in 0 until stepY) {
            for (ix in 0 until stepX) {
                val sat = ix / (stepX - 1f)
                val value = 1f - iy / (stepY - 1f)
                val rgb = hsvToRgb(picker.hue, sat, value)
                val color = (picker.alpha shl 24) or (rgb[0] shl 16) or (rgb[1] shl 8) or rgb[2]
                fillRect(graphics, x + ix * cellW, y + iy * cellH, cellW, cellH, color)
            }
        }
        val markerX = x + w * picker.saturation
        val markerY = y + h * (1f - picker.value)
        fillRect(graphics, markerX - s(2f), markerY - s(2f), s(4f), s(4f), 0xFFF4F7FB.toInt(), s(2f))
    }

    private fun drawHueBar(graphics: GuiGraphicsExtractor, x: Float, y: Float, w: Float, h: Float, picker: PickerState) {
        val steps = 64
        val segmentW = w / steps
        for (i in 0 until steps) {
            val hue = 360f * i / steps
            val rgb = hsvToRgb(hue, 1f, 1f)
            val color = 0xFF000000.toInt() or (rgb[0] shl 16) or (rgb[1] shl 8) or rgb[2]
            fillRect(graphics, x + i * segmentW, y, segmentW, h, color)
        }
        val markerX = x + w * (picker.hue / 360f)
        fillRect(graphics, markerX - s(1f), y - s(2f), s(2f), h + s(4f), 0xFFF4F7FB.toInt())
    }

    private fun drawAlphaBar(graphics: GuiGraphicsExtractor, x: Float, y: Float, w: Float, h: Float, picker: PickerState) {
        val rgb = hsvToRgb(picker.hue, picker.saturation, picker.value)
        val solid = 0xFF000000.toInt() or (rgb[0] shl 16) or (rgb[1] shl 8) or rgb[2]
        val transparent = solid and 0x00FFFFFF
        fillRect(graphics, x, y, w, h, transparent, s(4f))
        val steps = 64
        val segmentW = w / steps
        for (i in 0 until steps) {
            val alpha = (255f * i / (steps - 1f)).roundToInt()
            fillRect(graphics, x + i * segmentW, y, segmentW, h, (alpha shl 24) or (solid and 0x00FFFFFF), s(4f))
        }
        val markerX = x + w * (picker.alpha / 255f)
        fillRect(graphics, markerX - s(1f), y - s(2f), s(2f), h + s(4f), 0xFFF4F7FB.toInt())
    }

    private fun blurPanel(graphics: GuiGraphicsExtractor, x: Float, y: Float, w: Float, h: Float, radius: Float, fillColor: Int) {
        BlurManager.drawBlurredRegion(graphics, x.toInt(), y.toInt(), w.toInt(), h.toInt())
        RoundedRenderer.roundedRect(graphics, x, y, w, h, radius, fillColor)
    }

    private fun drawPillButton(graphics: GuiGraphicsExtractor, x: Float, y: Float, w: Float, h: Float, label: String, action: () -> Unit) {
        blurPanel(graphics, x, y, w, h, pillRadius, raisedTint)
        if (label.isNotEmpty()) {
            drawTitleText(graphics, label, x + (w - textWidth(label, 0.88f, true)) / 2f, centerTextY(y, h, 0.88f), textPrimary, 0.88f)
        }
        clickActions += ClickAction(Aabb(x, y, x + w, y + h), action)
    }

    private fun drawUtilityIcon(
        graphics: GuiGraphicsExtractor,
        x: Float,
        y: Float,
        iconHeight: Float,
        texture: Identifier,
        widthScale: Float = 1f,
        action: () -> Unit
    ): Float {
        val iconWidth = iconHeight * widthScale
        val drawWidth = iconWidth.roundToInt().coerceAtLeast(1)
        val drawHeight = iconHeight.roundToInt().coerceAtLeast(1)
        val drawX = x.roundToInt()
        val drawY = y.roundToInt()
        graphics.blit(
            RenderPipelines.GUI_TEXTURED,
            texture,
            drawX,
            drawY,
            0f,
            0f,
            drawWidth,
            drawHeight,
            drawWidth,
            drawHeight
        )
        clickActions += ClickAction(Aabb(x - s(3f), y - s(3f), x + drawWidth + s(3f), y + drawHeight + s(3f)), action)
        return x + drawWidth
    }

    private fun drawMiniPill(graphics: GuiGraphicsExtractor, x: Float, y: Float, w: Float, h: Float, label: String, action: (() -> Unit)? = null) {
        blurPanel(graphics, x, y, w, h, s(8f), raisedTint)
        drawBodyText(graphics, label, x + (w - textWidth(label, 0.8f)) / 2f, centerTextY(y, h, 0.8f), textPrimary, 0.8f)
        if (action != null) {
            clickActions += ClickAction(Aabb(x, y, x + w, y + h), action)
        }
    }

    private fun drawToggle(graphics: GuiGraphicsExtractor, x: Float, y: Float, w: Float, h: Float, enabled: Boolean, key: String) {
        val target = if (enabled) 1f else 0f
        val anim = animateValue(toggleAnimations[key] ?: target, target, 0.22f).also {
            toggleAnimations[key] = if (kotlin.math.abs(it - target) < 0.01f) target else it
        }
        fillRect(graphics, x, y, w, h, mixColor(colors.toggleOffColor, colors.toggleOnColor, anim), h / 2f)
        val knob = h - s(4f)
        val knobX = x + s(2f) + (w - knob - s(4f)) * anim
        fillRect(graphics, knobX, y + s(2f), knob, knob, toggleKnobColor, knob / 2f)
    }

    private fun drawText(graphics: GuiGraphicsExtractor, text: String, x: Float, y: Float, color: Int) {
        ModTextRenderer.drawTextCustom(graphics, text, x, y, color, false)
    }

    private fun drawScaledText(graphics: GuiGraphicsExtractor, text: String, x: Float, y: Float, color: Int, scale: Float) {
        ModTextRenderer.drawScaledTextCustom(graphics, text, x, y, color, scale, false)
    }

    private fun drawTitleText(graphics: GuiGraphicsExtractor, text: String, x: Float, y: Float, color: Int, scale: Float = 1f) {
        drawScaledText(graphics, text, x, y, color, scale)
    }

    private fun drawBodyText(graphics: GuiGraphicsExtractor, text: String, x: Float, y: Float, color: Int, scale: Float = 1f) {
        drawScaledText(graphics, text, x, y, color, scale)
    }

    private fun fillRect(graphics: GuiGraphicsExtractor, x: Float, y: Float, w: Float, h: Float, color: Int, radius: Float = 0f) {
        if (radius > 0f) RoundedRenderer.roundedRect(graphics, x, y, w, h, radius, color) else graphics.fill(x.toInt(), y.toInt(), (x + w).toInt(), (y + h).toInt(), color)
    }

    private fun fillOutline(graphics: GuiGraphicsExtractor, x: Float, y: Float, w: Float, h: Float, color: Int, radius: Float) {
        return
    }

    private fun textWidth(text: String, scale: Float = 1f, bold: Boolean = false): Float {
        val rendered = if (bold) "§l$text" else text
        return ModTextRenderer.widthCustom(rendered, scale).toFloat()
    }

    private fun textHeight(scale: Float = 1f): Float = ModTextRenderer.lineHeightCustom(scale).toFloat()
    private fun centerTextY(y: Float, height: Float, scale: Float = 1f): Float = y + (height - textHeight(scale)) / 2f - s(0.5f)
    private fun animateValue(current: Float, target: Float, speed: Float): Float = current + (target - current) * speed
    private fun lerp(start: Float, end: Float, delta: Float): Float = start + (end - start) * delta
    private fun easeOutCubic(t: Float): Float {
        val c = t.coerceIn(0f, 1f)
        val inv = 1f - c
        return 1f - inv * inv * inv
    }

    fun requestClose() {
        closing = true
        searchFocused = false
    }

    private fun drawGradientTitle(graphics: GuiGraphicsExtractor, text: String, x: Float, y: Float, scale: Float) {
        var drawX = x
        val clean = text.replace("§l", "")
        val lastIndex = (clean.length - 1).coerceAtLeast(1)
        clean.forEachIndexed { index, ch ->
            val t = index.toFloat() / lastIndex.toFloat()
            val color = mixColor(0xFF48E4E7.toInt(), 0xFFFF71C8.toInt(), t)
            val value = if (index == 0) "§l$ch" else ch.toString()
            drawScaledText(graphics, value, drawX, y, color, scale)
            drawX += textWidth(value, scale)
        }
    }
    private fun composeGuiAlpha(color: Int, alpha: Float): Int {
        val a = ((color ushr 24 and 0xFF) * alpha.coerceIn(0f, 1f)).roundToInt().coerceIn(0, 255)
        return (a shl 24) or (color and 0x00FFFFFF)
    }

    private fun mixColor(from: Int, to: Int, t: Float): Int {
        val clamped = t.coerceIn(0f, 1f)
        fun channel(shift: Int): Int {
            val a = (from ushr shift) and 0xFF
            val b = (to ushr shift) and 0xFF
            return (a + ((b - a) * clamped)).roundToInt()
        }
        return (channel(24) shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }

    private fun keyName(key: Int): String {
        if (key == GLFW.GLFW_KEY_UNKNOWN) return "None"
        return GLFW.glfwGetKeyName(key, 0) ?: when (key) {
            GLFW.GLFW_KEY_RIGHT_SHIFT -> "RShift"
            GLFW.GLFW_KEY_LEFT_SHIFT -> "LShift"
            else -> "Key $key"
        }
    }

    private fun format(value: Double): String {
        val rounded = round(value * 10.0) / 10.0
        return if (rounded % 1.0 == 0.0) rounded.toInt().toString() else rounded.toString()
    }

    private fun argbHex(color: Int): String = "#%08X".format(color)

    private fun parseHexColor(text: String): Int? {
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

    private fun isColorSetting(setting: ComponentSetting): Boolean {
        return setting.name.contains("Color", ignoreCase = true) && parseHexColor(setting.text) != null
    }

    private fun rgbToHsv(red: Int, green: Int, blue: Int): FloatArray {
        val r = red / 255f
        val g = green / 255f
        val b = blue / 255f
        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        val delta = max - min
        val hue = when {
            delta == 0f -> 0f
            max == r -> 60f * (((g - b) / delta) % 6f)
            max == g -> 60f * (((b - r) / delta) + 2f)
            else -> 60f * (((r - g) / delta) + 4f)
        }.let { if (it < 0f) it + 360f else it }
        val saturation = if (max == 0f) 0f else delta / max
        return floatArrayOf(hue, saturation, max)
    }

    private fun hsvToRgb(hue: Float, saturation: Float, value: Float): IntArray {
        val c = value * saturation
        val x = c * (1 - kotlin.math.abs((hue / 60f) % 2 - 1))
        val m = value - c
        val (r1, g1, b1) = when {
            hue < 60f -> floatArrayOf(c, x, 0f)
            hue < 120f -> floatArrayOf(x, c, 0f)
            hue < 180f -> floatArrayOf(0f, c, x)
            hue < 240f -> floatArrayOf(0f, x, c)
            hue < 300f -> floatArrayOf(x, 0f, c)
            else -> floatArrayOf(c, 0f, x)
        }
        return intArrayOf(
            ((r1 + m) * 255f).roundToInt().coerceIn(0, 255),
            ((g1 + m) * 255f).roundToInt().coerceIn(0, 255),
            ((b1 + m) * 255f).roundToInt().coerceIn(0, 255)
        )
    }

    private fun activeColorEditorTitle(): String? {
        activeColorTarget?.let { return it.label }
        activeComponentColorSetting?.let { return it.second.name }
        return null
    }

    private fun activeEditedColor(): Int? {
        activeColorTarget?.let { return getColor(it) }
        activeComponentColorSetting?.let { return parseHexColor(it.second.text) }
        activeSliderColorGroup?.let { return sliderColorGroupColor(it.second) }
        return null
    }

    private fun getColor(target: ColorTarget): Int = when (target) {
        ColorTarget.PRIMARY_PANEL -> colors.backgroundColor
        ColorTarget.SECONDARY_PANEL -> colors.topBarColor
        ColorTarget.TERTIARY_PANEL -> colors.cardColor
        ColorTarget.MODULE_HOVER -> colors.cardHoverColor
        ColorTarget.DIVIDER -> colors.dividerColor
        ColorTarget.MODULE_NAME -> colors.textColor
        ColorTarget.DESCRIPTION -> colors.softComponentColor
        ColorTarget.CATEGORY -> colors.moduleEnabledColor
        ColorTarget.SUBCATEGORY -> colors.accentColor
        ColorTarget.SUB_SUBCATEGORY -> colors.accentOutlineColor
        ColorTarget.TOGGLE_ON -> colors.toggleOnColor
        ColorTarget.TOGGLE_OFF -> colors.toggleOffColor
        ColorTarget.TOGGLE_BUTTON -> colors.accentOutlineStrongColor
        ColorTarget.SLIDER_INACTIVE -> colors.sliderInactiveColor
        ColorTarget.SLIDER_ACTIVE -> colors.moduleListGradientStartColor
    }

    private fun setColor(target: ColorTarget, value: Int) {
        when (target) {
            ColorTarget.PRIMARY_PANEL -> colors.backgroundColor = value
            ColorTarget.SECONDARY_PANEL -> colors.topBarColor = value
            ColorTarget.TERTIARY_PANEL -> colors.cardColor = value
            ColorTarget.MODULE_HOVER -> colors.cardHoverColor = value
            ColorTarget.DIVIDER -> colors.dividerColor = value
            ColorTarget.MODULE_NAME -> colors.textColor = value
            ColorTarget.DESCRIPTION -> colors.softComponentColor = value
            ColorTarget.CATEGORY -> colors.moduleEnabledColor = value
            ColorTarget.SUBCATEGORY -> colors.accentColor = value
            ColorTarget.SUB_SUBCATEGORY -> colors.accentOutlineColor = value
            ColorTarget.TOGGLE_ON -> colors.toggleOnColor = value
            ColorTarget.TOGGLE_OFF -> colors.toggleOffColor = value
            ColorTarget.TOGGLE_BUTTON -> colors.accentOutlineStrongColor = value
            ColorTarget.SLIDER_INACTIVE -> colors.sliderInactiveColor = value
            ColorTarget.SLIDER_ACTIVE -> colors.moduleListGradientStartColor = value
        }
    }

    private fun loadInitialCategory(): ModuleCategory {
        val saved = ClickGuiConfigManager.config.selectedCategory
        return ModuleCategory.entries.firstOrNull { it.name == saved } ?: ModuleCategory.SKYBLOCK_GENERAL
    }

    private fun openUri(url: String) {
        runCatching {
            val uri = URI(url)
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(uri)
                return@runCatching
            }

            val command = when {
                System.getProperty("os.name").lowercase().contains("win") -> listOf("rundll32", "url.dll,FileProtocolHandler", url)
                System.getProperty("os.name").lowercase().contains("mac") -> listOf("open", url)
                else -> listOf("xdg-open", url)
            }
            ProcessBuilder(command).start()
        }
    }

    private fun openLarpClientConfigFolder() {
        val path = FabricLoader.getInstance().gameDir.resolve("config").resolve("larpclient")
        runCatching { Files.createDirectories(path) }
        openPath(path)
    }

    private fun openPath(path: Path) {
        val target = (if (path.toFile().exists()) path else path.parent)?.toAbsolutePath()?.normalize() ?: return
        runCatching {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                Desktop.getDesktop().open(target.toFile())
                return@runCatching
            }

            val targetPath = target.toString()
            val command = when {
                System.getProperty("os.name").lowercase().contains("win") -> listOf("explorer", targetPath)
                System.getProperty("os.name").lowercase().contains("mac") -> listOf("open", targetPath)
                else -> listOf("xdg-open", targetPath)
            }
            ProcessBuilder(command).start()
        }
    }
}
