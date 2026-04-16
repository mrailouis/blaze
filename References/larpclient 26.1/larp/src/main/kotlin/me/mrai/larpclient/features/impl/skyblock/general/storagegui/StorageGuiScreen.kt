package me.mrai.larpclient.features.impl.skyblock.general.storagegui

import me.mrai.larpclient.util.TextSanitizer
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.world.item.ItemStack
import org.lwjgl.glfw.GLFW
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class StorageGuiScreen : Screen(Component.literal("Storage GUI")) {
    private data class Rect(val x: Int, val y: Int, val w: Int, val h: Int) {
        fun contains(mx: Double, my: Double): Boolean = mx >= x && mx < x + w && my >= y && my < y + h
    }

    private data class HoverPreview(val pageId: Int, val rect: Rect)

    private val sizeX = 540
    private val searchNobX = 18
    private val storageViewSize = 188
    private val sizeY get() = 100 + storageViewSize

    private val pageRects = mutableMapOf<Int, Rect>()
    private val pageSlotRects = mutableMapOf<Int, Rect>()
    private val selectorRects = mutableMapOf<Int, Rect>()
    private val buttonRects = mutableMapOf<Int, Rect>()
    private var hoveredPreview: HoverPreview? = null
    private var searchBox: EditBox? = null
    private var scroll = 0

    private var guiLeft = 0
    private var guiTop = 0
    private val cachedBackpackPages = mutableSetOf<Int>()

    override fun init() {
        clearWidgets()
        searchBox = EditBox(font, 0, 0, 88, 10, Component.literal("Search")).apply {
            setHint(Component.literal("Search"))
            setBordered(false)
            setTextColor(0xFFE6E6E6.toInt())
            setResponder(StorageGuiManager::setSearch)
            setValue(StorageGuiManager.searchQuery)
        }
        addRenderableWidget(searchBox!!)
        super.init()
    }

    override fun tick() {
        if (!StorageGuiManager.shouldKeepScreenOpen()) {
            minecraft.setScreen(null)
        }
    }

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        guiLeft = ((width / 2f) - ((sizeX - searchNobX) / 2f)).roundToInt()
        guiTop = ((height / 2f) - (sizeY / 2f)).roundToInt()

        pageRects.clear()
        pageSlotRects.clear()
        selectorRects.clear()
        buttonRects.clear()
        hoveredPreview = null

        graphics.fill(0, 0, width, height, 0xD0101010.toInt())

        val pose = graphics.pose()
        pose.pushMatrix()
        pose.translate(guiLeft.toFloat(), guiTop.toFloat())

        renderGuiBackground(graphics)
        renderScrollBar(graphics)
        renderStoragePages(graphics, mouseX, mouseY)
        renderInventory(graphics, mouseX, mouseY)
        renderSelectors(graphics, mouseX, mouseY)
        renderButtons(graphics, mouseX, mouseY)

        pose.popMatrix()

        positionSearchBox()
        super.extractRenderState(graphics, mouseX, mouseY, delta)

        if (StorageGuiModule.hoverPreview.value) {
            renderHoverPreview(graphics, mouseX, mouseY)
        }
    }

    override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        if (super.mouseClicked(event, doubleClick)) return true

        val x = event.x()
        val y = event.y()
        val button = event.button()

        selectorRects.entries.firstOrNull { it.value.contains(x, y) }?.let { entry ->
            if (button == 0) {
                cacheBackpackIfNeeded(entry.key)
                StorageGuiManager.openPage(entry.key)
            }
            return true
        }

        pageSlotRects.entries.firstOrNull { it.value.contains(x, y) }?.let { entry ->
            if (button == 0 || button == 1) {
                StorageGuiManager.clickCurrentPageSlot(entry.key, button)
            }
            return true
        }

        pageRects.entries.firstOrNull { it.value.contains(x, y) }?.let { entry ->
            if (button == 0) {
                StorageGuiManager.openPage(entry.key)
            }
            return true
        }

        return true
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, horizontalAmount: Double, verticalAmount: Double): Boolean {
        val insideStorageViewport = mouseY > guiTop + 3 && mouseY < guiTop + 3 + storageViewSize
        if (insideStorageViewport) {
            scroll = (scroll - verticalAmount.roundToInt() * 24).coerceIn(0, getMaximumScroll())
            return true
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)
    }

    override fun keyPressed(event: KeyEvent): Boolean {
        if (super.keyPressed(event)) return true
        if (event.key() == GLFW.GLFW_KEY_ESCAPE || minecraft.options.keyInventory.matches(event)) {
            onClose()
            return true
        }
        return true
    }

    override fun charTyped(event: CharacterEvent): Boolean = super.charTyped(event)

    override fun onClose() {
        minecraft.player?.closeContainer()
        minecraft.setScreen(null)
    }

    override fun shouldCloseOnEsc(): Boolean = false

    override fun isPauseScreen(): Boolean = false

    private fun positionSearchBox() {
        searchBox?.apply {
            setX(guiLeft + 252)
            setY(guiTop + storageViewSize + 5)
            width = 88
            height = 10
        }
    }

    private fun renderGuiBackground(graphics: GuiGraphicsExtractor) {
        graphics.blit(
            RenderPipelines.GUI_TEXTURED,
            texture(),
            0, 0,
            0f, 0f,
            sizeX, 10,
            600, 400
        )
        graphics.blit(
            RenderPipelines.GUI_TEXTURED,
            texture(),
            0, 10,
            0f, 10f / 400f,
            sizeX, storageViewSize - 20,
            600, 400
        )
        graphics.blit(
            RenderPipelines.GUI_TEXTURED,
            texture(),
            0, storageViewSize - 10,
            0f, 94f / 400f,
            sizeX, 110,
            600, 400
        )
    }

    private fun renderScrollBar(graphics: GuiGraphicsExtractor) {
        val maxScroll = getMaximumScroll().coerceAtLeast(1)
        scroll = scroll.coerceIn(0, maxScroll)
        val scrollBarY = (getScrollBarHeight() * scroll / maxScroll.toFloat()).roundToInt()
        graphics.blit(
            RenderPipelines.GUI_TEXTURED,
            texture(),
            520,
            8 + scrollBarY,
            0f,
            250f / 400f,
            12,
            15,
            600,
            400
        )
    }

    private fun renderStoragePages(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {
        val display = StorageGuiManager.displayOrder()
        graphics.enableScissor(0, 3, sizeX, 3 + storageViewSize)

        for ((displayIndex, pageId) in display.withIndex()) {
            val coords = getPageCoords(displayIndex)
            val storageX = coords.first
            val storageY = coords.second
            if (storageY - 11 > 3 + storageViewSize || storageY + 90 < 3) continue

            val page = StorageGuiManager.page(pageId)
            val rows = page?.rows?.takeIf { it > 0 } ?: 3
            val pageHeight = rows * 18
            val pageRect = Rect(guiLeft + storageX, guiTop + storageY, 162, pageHeight)
            pageRects[pageId] = pageRect

            renderPageBackground(graphics, pageId, storageX, storageY, pageHeight)

            val titleY = storageY - 11
            graphics.text(font, Component.literal(pageTitle(pageId, page)), storageX + 1, titleY, 0xFFE6E6E6.toInt(), false)

            when {
                page == null -> drawPagePlaceholder(
                    graphics,
                    storageX,
                    storageY,
                    if (pageId < StorageGuiManager.MAX_ENDER_CHEST_PAGES) "Locked Page" else "Empty Backpack Slot"
                )
                page.rows <= 0 -> drawPagePlaceholder(graphics, storageX, storageY, "Click to load items")
                else -> renderPageItems(graphics, pageId, page, storageX, storageY, mouseX, mouseY)
            }

            if (pageId != StorageGuiManager.currentPageId) {
                renderInactivePageOverlay(graphics, page, storageX, storageY, pageHeight)
            }
        }

        graphics.disableScissor()
    }

    private fun renderPageBackground(graphics: GuiGraphicsExtractor, pageId: Int, x: Int, y: Int, height: Int) {
        graphics.blit(
            RenderPipelines.GUI_TEXTURED,
            texture(),
            x,
            y,
            0f,
            265f / 400f,
            162,
            height,
            600,
            400
        )

        if (pageId == StorageGuiManager.currentPageId) {
            graphics.outline(x - 1, y - 1, 164, height + 2, 0xFF96D0FF.toInt())
        }
    }

    private fun renderPageItems(
        graphics: GuiGraphicsExtractor,
        pageId: Int,
        page: StorageGuiManager.StoragePage,
        storageX: Int,
        storageY: Int,
        mouseX: Int,
        mouseY: Int
    ) {
        val visibleSlots = (page.rows.coerceAtMost(5) * 9).coerceAtLeast(0)
        for (slot in 0 until visibleSlots) {
            val stack = page.items.getOrNull(slot) ?: continue
            val itemX = storageX + 1 + 18 * (slot % 9)
            val itemY = storageY + 1 + 18 * (slot / 9)

            graphics.item(stack, itemX, itemY)
            graphics.itemDecorations(font, stack, itemX, itemY)

            if (pageId == StorageGuiManager.currentPageId) {
                pageSlotRects[slot] = Rect(guiLeft + itemX, guiTop + itemY, 16, 16)
            }

            if (isHoveringScreenRect(itemX, itemY, 16, 16, mouseX, mouseY)) {
                graphics.fill(itemX, itemY, itemX + 16, itemY + 16, 0x35FFFFFF)
                graphics.setTooltipForNextFrame(font, stack, mouseX, mouseY)
            }
        }
    }

    private fun drawPagePlaceholder(graphics: GuiGraphicsExtractor, storageX: Int, storageY: Int, text: String) {
        val textWidth = font.width(text)
        val x = storageX + (162 - textWidth) / 2
        graphics.text(font, Component.literal(text), x, storageY + 26, 0xFFB5B5B5.toInt(), false)
    }

    private fun renderInactivePageOverlay(
        graphics: GuiGraphicsExtractor,
        page: StorageGuiManager.StoragePage?,
        storageX: Int,
        storageY: Int,
        pageHeight: Int
    ) {
        if (page == null || page.rows <= 0) {
            graphics.fill(storageX, storageY, storageX + 162, storageY + pageHeight, 0x40000000)
            return
        }

        for (slot in 0 until page.rows * 9) {
            val x = storageX + 18 * (slot % 9)
            val y = storageY + 18 * (slot / 9)
            graphics.fill(x, y, x + 18, y + 18, 0x40000000)
        }
    }

    private fun renderInventory(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {
        val player = minecraft.player ?: return

        graphics.text(font, Component.literal("Inventory"), 180, storageViewSize + 6, 0xFFE6E6E6.toInt(), false)

        for (i in 9 until 36) {
            val stack = player.inventory.getItem(i)
            val slotX = 181 + 18 * ((i - 9) % 9)
            val slotY = storageViewSize + 18 + 18 * ((i - 9) / 9)
            renderInventoryStack(graphics, stack, slotX, slotY, mouseX, mouseY)
        }

        for (i in 0 until 9) {
            val stack = player.inventory.getItem(i)
            val slotX = 181 + 18 * i
            val slotY = storageViewSize + 76
            renderInventoryStack(graphics, stack, slotX, slotY, mouseX, mouseY)
        }
    }

    private fun renderInventoryStack(
        graphics: GuiGraphicsExtractor,
        stack: ItemStack,
        x: Int,
        y: Int,
        mouseX: Int,
        mouseY: Int
    ) {
        if (!stack.isEmpty) {
            graphics.item(stack, x, y)
            graphics.itemDecorations(font, stack, x, y)
        }
        if (!stack.isEmpty && isHoveringScreenRect(x, y, 16, 16, mouseX, mouseY)) {
            graphics.setTooltipForNextFrame(font, stack, mouseX, mouseY)
        }
    }

    private fun renderSelectors(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {
        val textColor = 0xFFE6E6E6.toInt()
        graphics.text(font, Component.literal("Ender Chest Pages"), 9, storageViewSize + 12, textColor, false)
        graphics.text(font, Component.literal("Storage Pages"), 9, storageViewSize + 44, textColor, false)

        for (pageId in 0 until StorageGuiManager.MAX_ENDER_CHEST_PAGES) {
            renderSelector(graphics, pageId, 10 + pageId * 18, storageViewSize + 24, mouseX, mouseY)
        }

        for (index in 0 until 18) {
            val pageId = index + StorageGuiManager.MAX_ENDER_CHEST_PAGES
            val x = 10 + (index % 9) * 18
            val y = storageViewSize + 56 + (index / 9) * 18
            renderSelector(graphics, pageId, x, y, mouseX, mouseY)
        }
    }

    private fun renderSelector(graphics: GuiGraphicsExtractor, pageId: Int, x: Int, y: Int, mouseX: Int, mouseY: Int) {
        val page = StorageGuiManager.page(pageId)
        val stack = page?.displayStack ?: StorageGuiManager.placeholderStack(pageId)
        val rect = Rect(guiLeft + x, guiTop + y, 16, 16)
        selectorRects[pageId] = rect

        graphics.item(stack, x, y)
        graphics.itemDecorations(font, stack, x, y, selectorLabel(pageId))

        val hovered = rect.contains(mouseX.toDouble(), mouseY.toDouble())
        if (hovered) {
            graphics.fill(x, y, x + 16, y + 16, 0x30FFFFFF)
            graphics.setTooltipForNextFrame(font, stack, mouseX, mouseY)
            hoveredPreview = HoverPreview(pageId, rect)
            cacheBackpackIfNeeded(pageId)
        }

        if (pageId == StorageGuiManager.currentPageId) {
            graphics.outline(x - 1, y - 1, 18, 18, 0xFF96D0FF.toInt())
        }
    }

    private fun renderButtons(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {
        val iconTexture = Identifier.fromNamespaceAndPath("larpclient", "storage_gui/storage_icons.png")
        for (i in 0 until 10) {
            val x = 388 + (i % 5) * 18
            val y = storageViewSize + 35 + (i / 5) * 18
            val rect = Rect(guiLeft + x, guiTop + y, 16, 16)
            buttonRects[i] = rect

            val u = i * 16
            val v = when (i) {
                2 -> StorageGuiManager.styleId() * 16
                3 -> if (StorageGuiModule.hoverPreview.value) 16 else 0
                else -> 0
            }
            graphics.blit(
                RenderPipelines.GUI_TEXTURED,
                iconTexture,
                x,
                y,
                u / 256f,
                v / 256f,
                16,
                16,
                256,
                256
            )
            if (rect.contains(mouseX.toDouble(), mouseY.toDouble())) {
                graphics.fill(x, y, x + 16, y + 16, 0x25FFFFFF)
            }
        }
    }

    private fun renderHoverPreview(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {
        val preview = hoveredPreview ?: return
        val page = StorageGuiManager.page(preview.pageId) ?: return
        if (page.rows <= 0) return

        val rows = page.rows.coerceAtMost(5)
        val previewX = (mouseX + 10).coerceAtMost(width - 176)
        val previewY = (mouseY + 10).coerceAtMost(height - (14 + rows * 18))

        graphics.blit(
            RenderPipelines.GUI_TEXTURED,
            previewTexture(),
            previewX,
            previewY,
            0f,
            0f,
            176,
            7,
            176,
            32
        )
        repeat(rows) { row ->
            graphics.blit(
                RenderPipelines.GUI_TEXTURED,
                previewTexture(),
                previewX,
                previewY + 7 + 18 * row,
                0f,
                7f / 32f,
                176,
                18,
                176,
                32
            )
        }
        graphics.blit(
            RenderPipelines.GUI_TEXTURED,
            previewTexture(),
            previewX,
            previewY + 7 + 18 * rows,
            0f,
            25f / 32f,
            176,
            7,
            176,
            32
        )

        val pose = graphics.pose()
        pose.pushMatrix()
        pose.translate(previewX.toFloat(), previewY.toFloat())
        for (slot in 0 until rows * 9) {
            val stack = page.items.getOrNull(slot) ?: continue
            val itemX = 8 + 18 * (slot % 9)
            val itemY = 8 + 18 * (slot / 9)
            graphics.item(stack, itemX, itemY)
            graphics.itemDecorations(font, stack, itemX, itemY)
        }
        pose.popMatrix()
    }

    private fun cacheBackpackIfNeeded(pageId: Int) {
        if (pageId !in cachedBackpackPages && pageId >= StorageGuiManager.MAX_ENDER_CHEST_PAGES) {
            cachedBackpackPages.add(pageId)
            StorageGuiManager.silentOpenPage(pageId)
        }
    }

    private fun selectorLabel(pageId: Int): String = if (pageId < 9) "${pageId + 1}" else "${pageId - 8}"

    private fun getMaximumScroll(): Int {
        val display = StorageGuiManager.displayOrder()
        if (display.isEmpty()) return 0
        val coords = ((display.size - 1) / 3) * 3 + 3
        val bottom = getPageCoords(coords).second + scroll - storageViewSize - 14
        return max(0, bottom)
    }

    private fun getScrollBarHeight(): Int = storageViewSize - 25

    private fun getPageCoords(displayId: Int): Pair<Int, Int> {
        val boundedId = max(0, displayId)
        var y = -scroll + 17 + 104 * (boundedId / 3)
        val display = StorageGuiManager.displayOrder()

        for (i in 0..<(boundedId - 2) step 3) {
            var maxRows = 1
            for (j in i until min(i + 3, display.size)) {
                val page = StorageGuiManager.page(display[j])
                maxRows = max(maxRows, if (page == null || page.rows <= 0) 3 else page.rows)
            }
            y -= (5 - maxRows) * 18
        }

        return (8 + 172 * (boundedId % 3)) to y
    }

    private fun pageTitle(pageId: Int, page: StorageGuiManager.StoragePage?): String {
        val explicit = page?.title
            ?.takeIf { it.isNotBlank() }
            ?.let(TextSanitizer::stripFormatting)
        if (!explicit.isNullOrBlank()) return explicit
        return if (pageId < StorageGuiManager.MAX_ENDER_CHEST_PAGES) {
            "Ender Chest ${pageId + 1}"
        } else {
            "Backpack ${pageId + 1 - StorageGuiManager.MAX_ENDER_CHEST_PAGES}"
        }
    }

    private fun isHoveringScreenRect(x: Int, y: Int, width: Int, height: Int, mouseX: Int, mouseY: Int): Boolean {
        val screenX = guiLeft + x
        val screenY = guiTop + y
        return mouseX >= screenX && mouseX < screenX + width && mouseY >= screenY && mouseY < screenY + height
    }

    private fun texture(): Identifier =
        Identifier.fromNamespaceAndPath("larpclient", "storage_gui/storage_gui_${StorageGuiManager.styleId()}.png")

    private fun previewTexture(): Identifier =
        Identifier.fromNamespaceAndPath("larpclient", "storage_gui/storage_preview_${StorageGuiManager.styleId()}.png")
}
