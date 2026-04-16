package me.mrai.larpclient.features.impl.skyblock.general.storagegui

import me.mrai.larpclient.module.ModuleConfigManager
import me.mrai.larpclient.util.TextSanitizer
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.world.item.ItemStack
import net.minecraft.world.inventory.Slot
import org.lwjgl.glfw.GLFW
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import me.mrai.larpclient.render.blur.BlurManager

object StorageOverlayRenderer {
    private const val SELECTED_OUTLINE_COLOR = 0xFFF2D047.toInt()

    private data class Rect(val x: Int, val y: Int, val w: Int, val h: Int) {
        fun contains(mx: Double, my: Double): Boolean = mx >= x && mx < x + w && my >= y && my < y + h
    }

    private data class HoverPreview(val pageId: Int)

    private const val SIZE_X = 540
    private const val SEARCH_NOB_X = 18
    private val storageViewSize get() = StorageGuiModule.viewHeight.value.roundToInt().coerceIn(104, 320)
    private val sizeY get() = 100 + storageViewSize

    private val pageRects = mutableMapOf<Int, Rect>()
    private val pageSlotRects = mutableMapOf<Int, Rect>()
    private val selectorRects = mutableMapOf<Int, Rect>()
    private var hoveredPreview: HoverPreview? = null
    private val cachedBackpackPages = mutableSetOf<Int>()
    private var searchBox: EditBox? = null
    private var scroll = 0
    private var guiLeft = 0
    private var guiTop = 0

    fun shouldRender(screen: AbstractContainerScreen<*>): Boolean = StorageGuiManager.shouldRenderOverlay(screen)

    fun render(screen: AbstractContainerScreen<*>, graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {
        if (!shouldRender(screen)) return

        updateLayout(screen)

        pageRects.clear()
        pageSlotRects.clear()
        selectorRects.clear()
        hoveredPreview = null

        val pose = graphics.pose()
        pose.pushMatrix()
        pose.translate(guiLeft.toFloat(), guiTop.toFloat())

        BlurManager.drawBlurredRegion(graphics, guiLeft, guiTop, SIZE_X, sizeY)

        renderGuiBackground(graphics)
        renderScrollBar(graphics)
        renderStoragePages(screen, graphics, mouseX, mouseY)
        renderInventory(screen, graphics, mouseX, mouseY)
        renderSelectors(graphics, mouseX, mouseY)
        renderButtons(graphics, mouseX, mouseY)

        pose.popMatrix()

        searchBox?.apply {
            setX(guiLeft + 252)
            setY(guiTop + storageViewSize + 5)
            width = 88
            height = 10
            extractWidgetRenderState(graphics, mouseX, mouseY, 0f)
        }

        if (StorageGuiModule.hoverPreview.value) {
            renderHoverPreview(graphics, mouseX, mouseY, screen)
        }
    }

    fun tick(screen: AbstractContainerScreen<*>) {
        if (!shouldRender(screen)) return
        ensureSearchBox(screen)
    }

    fun onRemoved() {
        searchBox = null
        pageRects.clear()
        pageSlotRects.clear()
        selectorRects.clear()
        hoveredPreview = null
    }

    fun mouseClicked(screen: AbstractContainerScreen<*>, event: MouseButtonEvent): Boolean {
        if (!shouldRender(screen)) return false
        updateLayout(screen)

        searchBox?.let {
            val inside = event.x() >= guiLeft + 252 && event.x() <= guiLeft + 340 &&
                event.y() >= guiTop + storageViewSize + 5 && event.y() <= guiTop + storageViewSize + 15
            if (inside) {
                it.setFocused(true)
                it.onClick(event, false)
                return true
            }
            it.setFocused(false)
        }

        buttonIndexAt(event.x(), event.y())?.let { index ->
            if (event.button() == 0 || event.button() == 1) {
                handleButtonClick(index, event.button())
                return true
            }
        }

        if (isPlayerInventoryArea(event.x(), event.y())) {
            return event.hasShiftDown() && !StorageGuiManager.onStorageMenu && !StorageGuiManager.canInteractWithCurrentPage()
        }

        if (isCurrentPageSlotArea(screen, event.x(), event.y())) {
            return !StorageGuiManager.canInteractWithCurrentPage()
        }

        selectorRects.entries.firstOrNull { it.value.contains(event.x(), event.y()) }?.let { entry ->
            if (event.button() == 0) {
                StorageGuiManager.openOrLoadFromOverlay(entry.key)
            }
            return true
        }

        pageRects.entries.firstOrNull { it.value.contains(event.x(), event.y()) }?.let { entry ->
            if (event.button() == 0) {
                if (entry.key != StorageGuiManager.currentPageId) {
                    StorageGuiManager.openOrLoadFromOverlay(entry.key)
                }
            }
            return true
        }

        return false
    }

    fun mouseScrolled(screen: AbstractContainerScreen<*>, mouseX: Double, mouseY: Double, verticalAmount: Double): Boolean {
        if (!shouldRender(screen)) return false
        if (mouseY > guiTop + 3 && mouseY < guiTop + 3 + storageViewSize) {
            scroll = (scroll - verticalAmount.roundToInt() * 24).coerceIn(0, getMaximumScroll())
            return true
        }
        return false
    }

    fun keyPressed(screen: AbstractContainerScreen<*>, event: KeyEvent): Boolean {
        if (!shouldRender(screen)) return false
        ensureSearchBox(screen)
        val search = searchBox ?: return false
        if (!search.isFocused) {
            if (StorageGuiModule.searchAutofocus.value && isPlainSearchKey(event)) {
                search.setFocused(true)
            } else {
                return false
            }
        }
        if (!search.isFocused && client().options.keyInventory.matches(event)) return true
        if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
            search.setFocused(false)
            return true
        }
        if (search.keyPressed(event)) return true
        keyEventText(event)?.let {
            search.insertText(it)
            return true
        }
        return false
    }

    fun charTyped(screen: AbstractContainerScreen<*>, event: CharacterEvent): Boolean {
        if (!shouldRender(screen)) return false
        ensureSearchBox(screen)
        return searchBox?.charTyped(event) == true
    }

    fun overrideSlotHover(screen: AbstractContainerScreen<*>, slot: Slot, mouseX: Double, mouseY: Double): Boolean? {
        if (!shouldRender(screen)) return null
        updateLayout(screen)
        return slotRect(screen, slot)?.contains(mouseX, mouseY) ?: false
    }

    fun overrideClickedOutside(screen: AbstractContainerScreen<*>, mouseX: Double, mouseY: Double): Boolean? {
        if (!shouldRender(screen)) return null
        updateLayout(screen)
        return !Rect(guiLeft, guiTop, SIZE_X, sizeY).contains(mouseX, mouseY)
    }

    private fun ensureSearchBox(screen: AbstractContainerScreen<*>) {
        if (searchBox != null) return
        searchBox = EditBox(screen.font, 0, 0, 88, 10, Component.literal("Search")).apply {
            setHint(Component.literal("Search"))
            setBordered(false)
            setTextColor(0xFFE6E6E6.toInt())
            setResponder(StorageGuiManager::setSearch)
            setValue(StorageGuiManager.searchQuery)
        }
    }

    private fun updateLayout(screen: AbstractContainerScreen<*>) {
        guiLeft = ((screen.width / 2f) - ((SIZE_X - SEARCH_NOB_X) / 2f)).roundToInt()
        guiTop = ((screen.height / 2f) - (sizeY / 2f)).roundToInt()
        ensureSearchBox(screen)
    }

    private fun renderGuiBackground(graphics: GuiGraphicsExtractor) {
        graphics.blit(RenderPipelines.GUI_TEXTURED, texture(), 0, 0, 0f, 0f, SIZE_X, 10, 600, 400)
        graphics.blit(
            RenderPipelines.GUI_TEXTURED,
            texture(),
            0,
            10,
            0f,
            10f,
            SIZE_X,
            storageViewSize - 20,
            SIZE_X,
            84,
            600,
            400
        )
        graphics.blit(RenderPipelines.GUI_TEXTURED, texture(), 0, storageViewSize - 10, 0f, 94f, SIZE_X, 110, 600, 400)
    }

    private fun renderScrollBar(graphics: GuiGraphicsExtractor) {
        val maxScroll = getMaximumScroll().coerceAtLeast(1)
        scroll = scroll.coerceIn(0, maxScroll)
        val scrollBarY = (getScrollBarHeight() * scroll / maxScroll.toFloat()).roundToInt()
        graphics.blit(RenderPipelines.GUI_TEXTURED, texture(), 520, 8 + scrollBarY, 0f, 250f, 12, 15, 600, 400)
    }

    private fun pageHasMatchingItems(page: StorageGuiManager.StoragePage?): Boolean {
        if (page == null || page.rows <= 0) return true
        val query = StorageGuiManager.searchQuery.lowercase()
        if (query.isBlank()) return true
        for (stack in page.items) {
            if (stack == null || stack.isEmpty) continue
            if (stack.displayName.string.lowercase().contains(query)) return true
        }
        return false
    }

    private fun renderStoragePages(screen: AbstractContainerScreen<*>, graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {
        val display = StorageGuiManager.displayOrder()
        graphics.enableScissor(0, 3, SIZE_X, 3 + storageViewSize)
        for ((displayIndex, pageId) in display.withIndex()) {
            val (storageX, storageY) = getPageCoords(displayIndex)
            if (storageY - 11 > 3 + storageViewSize || storageY + 90 < 3) continue

            val page = StorageGuiManager.page(pageId)
            if (StorageGuiManager.searchQuery.isNotBlank() && !pageHasMatchingItems(page)) continue
            val rows = page?.rows?.takeIf { it > 0 } ?: 3
            val displayRows = if (StorageGuiModule.masonryMode.value) {
                rows
            } else {
                var maxRows = 1
                val rowStart = (displayIndex / 3) * 3
                for (j in rowStart until min(rowStart + 3, display.size)) {
                    val p = StorageGuiManager.page(display[j])
                    maxRows = max(maxRows, if (p == null || p.rows <= 0) 3 else p.rows)
                }
                maxRows
            }
            val pageHeight = displayRows * 18
            pageRects[pageId] = Rect(guiLeft + storageX, guiTop + storageY, 162, pageHeight)

            graphics.blit(RenderPipelines.GUI_TEXTURED, texture(), storageX, storageY, 0f, 265f, 162, pageHeight, 600, 400)
            graphics.text(screen.font, Component.literal(pageTitle(pageId, page)), storageX + 1, storageY - 11, 0xFFE6E6E6.toInt(), false)

            when {
                page == null -> drawPagePlaceholder(graphics, screen, storageX, storageY, if (pageId < StorageGuiManager.MAX_ENDER_CHEST_PAGES) "Locked Page" else "Empty Backpack Slot")
                page.rows <= 0 && page.items.all { it == null } -> drawPagePlaceholder(graphics, screen, storageX, storageY, "Click to load items")
                else -> renderPageItems(screen, graphics, pageId, page, storageX, storageY, mouseX, mouseY)
            }

            if (pageId == StorageGuiManager.currentPageId) {
                drawOutline(graphics, storageX, storageY, 162, pageHeight, SELECTED_OUTLINE_COLOR)
            }
        }
        graphics.disableScissor()
    }

    private fun renderPageItems(
        screen: AbstractContainerScreen<*>,
        graphics: GuiGraphicsExtractor,
        pageId: Int,
        page: StorageGuiManager.StoragePage,
        storageX: Int,
        storageY: Int,
        mouseX: Int,
        mouseY: Int
    ) {
        val query = StorageGuiManager.searchQuery.lowercase()
        for (slot in 0 until page.rows * 9) {
            val stack = displayedPageStack(screen, pageId, page, slot) ?: continue
            if (stack.isEmpty) continue
            val itemX = storageX + 1 + 18 * (slot % 9)
            val itemY = storageY + 1 + 18 * (slot / 9)
            graphics.item(stack, itemX, itemY)
            graphics.itemDecorations(screen.font, stack, itemX, itemY)
            if (query.isNotBlank() && stack.displayName.string.lowercase().contains(query)) {
                graphics.fill(itemX, itemY, itemX + 16, itemY + 16, 0x80FFFF00.toInt())
            }
            if (pageId == StorageGuiManager.currentPageId) {
                pageSlotRects[slot] = Rect(guiLeft + itemX, guiTop + itemY, 16, 16)
            }
            if (isHovering(itemX, itemY, mouseX, mouseY)) {
                graphics.setTooltipForNextFrame(screen.font, stack, mouseX, mouseY)
            }
        }
    }

    private fun drawPagePlaceholder(
        graphics: GuiGraphicsExtractor,
        screen: AbstractContainerScreen<*>,
        x: Int,
        y: Int,
        text: String
    ) {
        val textWidth = screen.font.width(text)
        graphics.fill(x, y, x + 162, y + 54, 0x40000000)
        graphics.text(screen.font, Component.literal(text), x + (162 - textWidth) / 2, y + 26, 0xFFCCCCCC.toInt(), false)
    }

    private fun renderInventory(screen: AbstractContainerScreen<*>, graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {
        val player = Minecraft.getInstance().player ?: return
        graphics.text(screen.font, Component.literal("Inventory"), 180, storageViewSize + 6, 0xFFE6E6E6.toInt(), false)
        for (i in 9 until 36) {
            val stack = player.inventory.getItem(i)
            val x = 181 + 18 * ((i - 9) % 9)
            val y = storageViewSize + 18 + 18 * ((i - 9) / 9)
            renderInventoryStack(screen, graphics, stack, x, y, mouseX, mouseY)
        }
        for (i in 0 until 9) {
            val stack = player.inventory.getItem(i)
            val x = 181 + 18 * i
            val y = storageViewSize + 76
            renderInventoryStack(screen, graphics, stack, x, y, mouseX, mouseY)
        }
    }

    private fun renderInventoryStack(
        screen: AbstractContainerScreen<*>,
        graphics: GuiGraphicsExtractor,
        stack: ItemStack,
        x: Int,
        y: Int,
        mouseX: Int,
        mouseY: Int
    ) {
        if (!stack.isEmpty) {
            graphics.item(stack, x, y)
            graphics.itemDecorations(screen.font, stack, x, y)
            if (isHovering(x, y, mouseX, mouseY)) {
                graphics.setTooltipForNextFrame(screen.font, stack, mouseX, mouseY)
            }
        }
    }

    private fun renderSelectors(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {
        graphics.text(Minecraft.getInstance().font, Component.literal("Ender Chest Pages"), 9, storageViewSize + 12, 0xFFE6E6E6.toInt(), false)
        graphics.text(Minecraft.getInstance().font, Component.literal("Storage Pages"), 9, storageViewSize + 44, 0xFFE6E6E6.toInt(), false)
        for (pageId in 0 until StorageGuiManager.MAX_ENDER_CHEST_PAGES) {
            renderSelector(graphics, pageId, 10 + pageId * 18, storageViewSize + 24, mouseX, mouseY)
        }
        for (index in 0 until 18) {
            val pageId = index + StorageGuiManager.MAX_ENDER_CHEST_PAGES
            renderSelector(graphics, pageId, 10 + (index % 9) * 18, storageViewSize + 56 + (index / 9) * 18, mouseX, mouseY)
        }
    }

    private fun renderSelector(graphics: GuiGraphicsExtractor, pageId: Int, x: Int, y: Int, mouseX: Int, mouseY: Int) {
        val stack = StorageGuiManager.page(pageId)?.displayStack ?: StorageGuiManager.placeholderStack(pageId)
        val rect = Rect(guiLeft + x, guiTop + y, 16, 16)
        selectorRects[pageId] = rect
        graphics.item(stack, x, y)
        if (pageId == StorageGuiManager.currentPageId) {
            drawOutline(graphics, x - 1, y - 1, 18, 18, SELECTED_OUTLINE_COLOR)
        }
        graphics.itemDecorations(Minecraft.getInstance().font, stack, x, y, if (pageId < 9) "${pageId + 1}" else "${pageId - 8}")
        if (rect.contains(mouseX.toDouble(), mouseY.toDouble())) {
            if ((pageId < StorageGuiManager.MAX_ENDER_CHEST_PAGES && StorageGuiModule.enderChestPreview.value) ||
                (pageId >= StorageGuiManager.MAX_ENDER_CHEST_PAGES && StorageGuiModule.backpackPreview.value)) {
                hoveredPreview = HoverPreview(pageId)
            }
            cacheBackpackIfNeeded(pageId)
        }
    }

    private fun renderButtons(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {
        val iconTexture = Identifier.fromNamespaceAndPath("larpclient", "storage_gui/storage_icons.png")
        for (i in 0 until 10) {
            val x = 388 + (i % 5) * 18
            val y = storageViewSize + 35 + (i / 5) * 18
            val u = i * 16
            val v = when (i) {
                0 -> if (StorageGuiModule.enabled) 0 else 16
                1 -> ((storageViewSize - 104) / 54).coerceIn(0, 4) * 16
                2 -> StorageGuiManager.styleId() * 16
                3 -> if (StorageGuiModule.backpackPreview.value) 16 else 0
                4 -> if (StorageGuiModule.enderChestPreview.value) 16 else 0
                5 -> if (StorageGuiModule.masonryMode.value) 16 else 0
                6 -> if (StorageGuiModule.masonryMode.value) 16 else 0
                7 -> if (StorageGuiModule.searchAutofocus.value) 16 else 0
                8 -> if (StorageGuiModule.hoverPreview.value) 16 else 0
                9 -> if (StorageGuiManager.searchQuery.isNotBlank()) 16 else 0
                else -> 0
            }
            graphics.blit(RenderPipelines.GUI_TEXTURED, iconTexture, x, y, u.toFloat(), v.toFloat(), 16, 16, 256, 256)
        }
    }

    private fun renderHoverPreview(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, screen: AbstractContainerScreen<*>) {
        val preview = hoveredPreview ?: return
        val page = StorageGuiManager.page(preview.pageId) ?: return
        if (page.rows <= 0) return
        val rows = page.rows.coerceAtMost(5)
        val previewX = (mouseX + 10).coerceAtMost(screen.width - 176)
        val previewY = (mouseY + 10).coerceAtMost(screen.height - (14 + rows * 18))
        graphics.blit(RenderPipelines.GUI_TEXTURED, previewTexture(), previewX, previewY, 0f, 0f, 176, 7, 176, 32)
        repeat(rows) { row ->
            graphics.blit(RenderPipelines.GUI_TEXTURED, previewTexture(), previewX, previewY + 7 + 18 * row, 0f, 7f, 176, 18, 176, 32)
        }
        graphics.blit(RenderPipelines.GUI_TEXTURED, previewTexture(), previewX, previewY + 7 + 18 * rows, 0f, 25f, 176, 7, 176, 32)
        val pose = graphics.pose()
        pose.pushMatrix()
        pose.translate(previewX.toFloat(), previewY.toFloat())
        for (slot in 0 until rows * 9) {
            val stack = page.items.getOrNull(slot) ?: continue
            if (stack.isEmpty) continue
            val itemX = 8 + 18 * (slot % 9)
            val itemY = 8 + 18 * (slot / 9)
            graphics.item(stack, itemX, itemY)
            graphics.itemDecorations(screen.font, stack, itemX, itemY)
        }
        pose.popMatrix()
    }

    private fun cacheBackpackIfNeeded(pageId: Int) {
        if (pageId !in cachedBackpackPages && pageId >= StorageGuiManager.MAX_ENDER_CHEST_PAGES) {
            cachedBackpackPages.add(pageId)
            StorageGuiManager.silentOpenPage(pageId)
        }
    }

    private fun isMappedSlotArea(screen: AbstractContainerScreen<*>, mouseX: Double, mouseY: Double): Boolean {
        return isPlayerInventoryArea(mouseX, mouseY) || isCurrentPageSlotArea(screen, mouseX, mouseY)
    }

    private fun isPlayerInventoryArea(mouseX: Double, mouseY: Double): Boolean {
        for (slotIndex in 0 until 36) {
            if (slotRectForPlayerSlot(slotIndex)?.contains(mouseX, mouseY) == true) {
                return true
            }
        }
        return false
    }

    private fun isCurrentPageSlotArea(screen: AbstractContainerScreen<*>, mouseX: Double, mouseY: Double): Boolean {
        if (StorageGuiManager.onStorageMenu) return false
        val currentPage = StorageGuiManager.currentPageId
        val page = StorageGuiManager.page(currentPage) ?: return false
        if (page.rows <= 0) return false
        for (slotIndex in 0 until page.rows * 9) {
            if (slotRectForCurrentPage(screen, slotIndex)?.contains(mouseX, mouseY) == true) {
                return true
            }
        }
        return false
    }

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
        var y = -scroll + if (StorageGuiModule.masonryMode.value) 18 + 108 * (boundedId / 3) else 17 + 104 * (boundedId / 3)
        val display = StorageGuiManager.displayOrder()
        for (i in 0..<(boundedId - 2) step 3) {
            var maxRows = 1
            for (j in i until min(i + 3, display.size)) {
                if (StorageGuiModule.masonryMode.value && boundedId % 3 != j % 3) continue
                val page = StorageGuiManager.page(display[j])
                maxRows = max(maxRows, if (page == null || page.rows <= 0) 3 else page.rows)
            }
            y -= (5 - maxRows) * 18
        }
        return (8 + 172 * (boundedId % 3)) to y
    }

    private fun pageTitle(pageId: Int, page: StorageGuiManager.StoragePage?): String {
        val explicit = page?.title?.takeIf { it.isNotBlank() }?.let(TextSanitizer::stripFormatting)
        if (!explicit.isNullOrBlank()) return explicit
        return if (pageId < StorageGuiManager.MAX_ENDER_CHEST_PAGES) "Ender Chest ${pageId + 1}" else "Backpack ${pageId + 1 - StorageGuiManager.MAX_ENDER_CHEST_PAGES}"
    }

    private fun isHovering(x: Int, y: Int, mouseX: Int, mouseY: Int): Boolean {
        val sx = guiLeft + x
        val sy = guiTop + y
        return mouseX >= sx && mouseX < sx + 16 && mouseY >= sy && mouseY < sy + 16
    }

    private fun drawOutline(graphics: GuiGraphicsExtractor, x: Int, y: Int, width: Int, height: Int, color: Int) {
        graphics.fill(x, y, x + width, y + 1, color)
        graphics.fill(x, y + height - 1, x + width, y + height, color)
        graphics.fill(x, y, x + 1, y + height, color)
        graphics.fill(x + width - 1, y, x + width, y + height, color)
    }

    private fun slotRect(screen: AbstractContainerScreen<*>, slot: Slot): Rect? {
        val playerInventory = Minecraft.getInstance().player?.inventory
        return when {
            slot.container === playerInventory -> slotRectForPlayerSlot(slot.containerSlot)
            StorageGuiManager.onStorageMenu || !StorageGuiManager.canInteractWithCurrentPage() -> null
            else -> slotRectForCurrentPage(screen, slotIndexOnCurrentPage(slot))
        }
    }

    private fun displayedPageStack(
        screen: AbstractContainerScreen<*>,
        pageId: Int,
        page: StorageGuiManager.StoragePage,
        slot: Int
    ): ItemStack? {
        if (pageId == StorageGuiManager.currentPageId) {
            val liveSlot = screen.menu.slots.getOrNull(slot + 9)
            if (liveSlot != null) {
                return liveSlot.item
            }
        }
        return page.items.getOrNull(slot)
    }

    private fun slotRectForPlayerSlot(slotIndex: Int): Rect? {
        return when (slotIndex) {
            in 0..8 -> Rect(guiLeft + 181 + 18 * slotIndex, guiTop + storageViewSize + 76, 16, 16)
            in 9..35 -> Rect(
                guiLeft + 181 + 18 * ((slotIndex - 9) % 9),
                guiTop + storageViewSize + 18 + 18 * ((slotIndex - 9) / 9),
                16,
                16
            )
            else -> null
        }
    }

    private fun slotRectForCurrentPage(screen: AbstractContainerScreen<*>, slotIndex: Int): Rect? {
        if (slotIndex < 0) return null
        val currentPage = StorageGuiManager.currentPageId
        val page = StorageGuiManager.page(currentPage) ?: return null
        if (slotIndex >= page.rows * 9) return null
        val displayId = StorageGuiManager.displayIdForStorageIdRender(currentPage)
        if (displayId < 0) return null
        val (pageX, pageY) = getPageCoords(displayId)
        return Rect(
            guiLeft + pageX + 1 + 18 * (slotIndex % 9),
            guiTop + pageY + 1 + 18 * (slotIndex / 9),
            16,
            16
        )
    }

    private fun slotIndexOnCurrentPage(slot: Slot): Int {
        val currentPage = StorageGuiManager.currentPageId
        val page = StorageGuiManager.page(currentPage) ?: return -1
        val rows = page.rows.coerceAtLeast(0)
        return when (slot.index) {
            in 9 until 9 + rows * 9 -> slot.index - 9
            else -> -1
        }
    }

    private fun texture(): Identifier =
        Identifier.fromNamespaceAndPath("larpclient", "storage_gui/storage_gui_${StorageGuiManager.styleId()}.png")

    private fun previewTexture(): Identifier =
        Identifier.fromNamespaceAndPath("larpclient", "storage_gui/storage_preview_${StorageGuiManager.styleId()}.png")

    private fun buttonIndexAt(mouseX: Double, mouseY: Double): Int? {
        for (i in 0 until 10) {
            val x = guiLeft + 388 + (i % 5) * 18
            val y = guiTop + storageViewSize + 35 + (i / 5) * 18
            if (mouseX >= x && mouseX < x + 18 && mouseY >= y && mouseY < y + 18) {
                return i
            }
        }
        return null
    }

    private fun handleButtonClick(index: Int, button: Int) {
        when (index) {
            0 -> StorageGuiModule.toggle()
            1 -> {
                val values = listOf(104.0, 158.0, 212.0, 266.0, 320.0)
                val currentIndex = values.indexOfFirst { kotlin.math.abs(it - StorageGuiModule.viewHeight.value) < 0.5 }.let { if (it >= 0) it else 1 }
                val nextIndex = if (button == 0) (currentIndex - 1).mod(values.size) else (currentIndex + 1).mod(values.size)
                StorageGuiModule.viewHeight.value = values[nextIndex]
            }
            2 -> StorageGuiModule.style.selectedIndex = (StorageGuiModule.style.selectedIndex + if (button == 0) 1 else -1).mod(StorageGuiModule.style.modes.size)
            3 -> StorageGuiModule.backpackPreview.value = !StorageGuiModule.backpackPreview.value
            4 -> StorageGuiModule.enderChestPreview.value = !StorageGuiModule.enderChestPreview.value
            5 -> StorageGuiModule.masonryMode.value = !StorageGuiModule.masonryMode.value
            6 -> StorageGuiModule.masonryMode.value = !StorageGuiModule.masonryMode.value
            7 -> StorageGuiModule.searchAutofocus.value = !StorageGuiModule.searchAutofocus.value
            8 -> StorageGuiModule.hoverPreview.value = !StorageGuiModule.hoverPreview.value
            9 -> {
                searchBox?.setValue("")
                searchBox?.setFocused(false)
                StorageGuiManager.setSearch("")
            }
            else -> return
        }
        ModuleConfigManager.save()
    }

    private fun isPlainSearchKey(event: KeyEvent): Boolean {
        return keyEventText(event) != null
    }

    private fun keyEventText(event: KeyEvent): String? {
        if (event.modifiers() and (GLFW.GLFW_MOD_CONTROL or GLFW.GLFW_MOD_ALT or GLFW.GLFW_MOD_SUPER) != 0) return null
        if (event.key() == GLFW.GLFW_KEY_SPACE) return " "
        val raw = GLFW.glfwGetKeyName(event.key(), event.scancode()) ?: return null
        if (raw.isBlank()) return null
        val text = if ((event.modifiers() and GLFW.GLFW_MOD_SHIFT) != 0) raw.uppercase() else raw
        return text.takeIf { it.all { ch -> !ch.isISOControl() } }
    }

    private fun client(): Minecraft = Minecraft.getInstance()
}
