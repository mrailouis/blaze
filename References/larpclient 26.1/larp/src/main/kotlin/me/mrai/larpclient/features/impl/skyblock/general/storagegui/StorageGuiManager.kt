package me.mrai.larpclient.features.impl.skyblock.general.storagegui

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.mojang.serialization.JsonOps
import me.mrai.larpclient.util.TextSanitizer
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.multiplayer.MultiPlayerGameMode
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.game.ClientboundContainerClosePacket
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.ChestMenu
import net.minecraft.world.inventory.ContainerInput
import net.minecraft.world.inventory.ShulkerBoxMenu
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import java.nio.file.Files
import java.nio.file.Path

object StorageGuiManager {
    const val MAX_ENDER_CHEST_PAGES = 9
    private const val TOTAL_PAGES = 27
    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val configPath: Path = FabricLoader.getInstance().configDir.resolve("larpclient/storage_cache.json")

    data class StoragePage(
        var rows: Int = -1,
        var title: String? = null,
        var displayStack: ItemStack? = null,
        val items: MutableList<ItemStack?> = MutableList(45) { null },
        var matchesSearch: Boolean = true
    )

    private val storageRegex = Regex("""^Storage(?: \((\d+)/(\d+)\))?$""")
    private val enderChestRegex = Regex("""^Ender Chest \((\d+)/(\d+)\)$""")
    private val backpackRegex = Regex("""^.+ Backpack (?:✦ )?\(Slot #(\d+)\)$""")

    private val pages = Array(TOTAL_PAGES) { StoragePage() }
    private val storagePresent = BooleanArray(TOTAL_PAGES)
    private val displayToStorageId = linkedMapOf<Int, Int>()
    private val displayToStorageIdRender = linkedMapOf<Int, Int>()
    private var requestedOverlayPageId = -1
    private var interactiveOverlayPageId = -1

    var currentWindowId = -1
        private set
    var currentPageId = -1
        private set
    var onStorageMenu = false
        private set
    var searchQuery = ""
        private set

    init {
        loadCache()
    }

    fun status(): String {
        val page = if (currentPageId >= 0) currentPageId + 1 else "-"
        return "Style ${StorageGuiModule.style.selected} | Page $page"
    }

    fun allPages(): List<Int> = (0 until TOTAL_PAGES).toList()

    fun displayOrder(): List<Int> = displayToStorageIdRender.values.toList()

    fun displayStorageId(displayId: Int): Int = displayToStorageIdRender[displayId] ?: -1

    fun displayIdForStorageIdRender(storageId: Int): Int =
        displayToStorageIdRender.entries.firstOrNull { it.value == storageId }?.key ?: -1

    fun page(id: Int): StoragePage? = pages.getOrNull(id)

    fun styleId(): Int = StorageGuiModule.style.selected.toIntOrNull()?.coerceIn(0, 3) ?: 0

    fun shouldRenderOverlay(screen: Screen): Boolean {
        if (!StorageGuiModule.enabled) return false
        if (screen !is AbstractContainerScreen<*>) return false
        val menu = screen.menu
        if (menu !is ChestMenu && menu !is ShulkerBoxMenu) return false
        return isStorageTitle(screen.title.string)
    }

    fun shouldReplaceScreen(screen: Screen): Boolean = shouldRenderOverlay(screen)

    fun createReplacementScreen(screen: Screen): Screen? {
        if (!shouldRenderOverlay(screen)) return null
        return null
    }

    fun shouldKeepScreenOpen(): Boolean {
        if (!StorageGuiModule.enabled) return false
        val player = Minecraft.getInstance().player ?: return false
        val menu = player.containerMenu
        if (menu !is ChestMenu && menu !is ShulkerBoxMenu) return false
        if (currentWindowId == -1) return false
        return menu.containerId == currentWindowId && (onStorageMenu || currentPageId in 0 until TOTAL_PAGES)
    }

    fun setSearch(query: String) {
        searchQuery = query.trim()
        rebuildRenderMap()
    }

    fun openPage(id: Int) {
        if (id !in 0 until TOTAL_PAGES) return
        Minecraft.getInstance().connection ?: return
        if (id == currentPageId && !onStorageMenu) return
        snapshotCurrentPageFromOpenMenu()
        if (id < MAX_ENDER_CHEST_PAGES) {
            Minecraft.getInstance().connection?.sendCommand("enderchest ${id + 1}")
        } else {
            Minecraft.getInstance().connection?.sendCommand("backpack ${id + 1 - MAX_ENDER_CHEST_PAGES}")
        }
    }

    fun openOrLoadFromOverlay(id: Int) {
        if (id !in 0 until TOTAL_PAGES) return
        requestedOverlayPageId = id
        if (id == currentPageId && hasLoadedPage(id)) {
            interactiveOverlayPageId = id
            return
        }
        interactiveOverlayPageId = -1
        if (id >= MAX_ENDER_CHEST_PAGES && !hasLoadedPage(id)) {
            silentOpenPage(id)
            return
        }
        openPage(id)
    }

    fun canInteractWithCurrentPage(): Boolean =
        currentPageId in 0 until TOTAL_PAGES && currentPageId == interactiveOverlayPageId

    fun clickCurrentPageSlot(slot: Int, button: Int): Boolean {
        if (slot < 0) return false
        val client = Minecraft.getInstance()
        val player = client.player ?: return false
        val gameMode = client.gameMode ?: return false
        val menu = player.containerMenu
        if (menu !is ChestMenu && menu !is ShulkerBoxMenu) return false
        if (menu.containerId != currentWindowId) return false
        val page = pages.getOrNull(currentPageId) ?: return false
        if (slot >= page.rows.coerceAtLeast(0) * 9) return false
        return sendClick(gameMode, player, menu.containerId, slot + 9, button)
    }

    fun onOpenScreen(packet: ClientboundOpenScreenPacket) {
        if (!StorageGuiModule.enabled) return
        val title = TextSanitizer.stripFormatting(packet.getTitle().string).trim()
        currentWindowId = packet.getContainerId()
        onStorageMenu = false
        currentPageId = -1

        when {
            title == "Storage" || storageRegex.matches(title) -> onStorageMenu = true
            enderChestRegex.matches(title) -> {
                val page = enderChestRegex.matchEntire(title)?.groupValues?.getOrNull(1)?.toIntOrNull()
                currentPageId = page?.minus(1) ?: -1
            }
            backpackRegex.matches(title) -> {
                val page = backpackRegex.matchEntire(title)?.groupValues?.getOrNull(1)?.toIntOrNull()
                currentPageId = (page?.minus(1) ?: -1).takeIf { it >= 0 }?.plus(MAX_ENDER_CHEST_PAGES) ?: -1
            }
        }

        interactiveOverlayPageId = if (currentPageId in 0 until TOTAL_PAGES && currentPageId == requestedOverlayPageId) {
            currentPageId
        } else {
            -1
        }

        if (currentPageId in 0 until TOTAL_PAGES) {
            storagePresent[currentPageId] = true
            pages[currentPageId].title = title
            rebuildDisplayMaps()
        }
    }

    fun onContainerClose(packet: ClientboundContainerClosePacket) {
        if (packet.getContainerId() != currentWindowId) return
        snapshotCurrentPageFromOpenMenu()
        currentWindowId = -1
        onStorageMenu = false
        currentPageId = -1
    }

    fun onContainerContent(packet: ClientboundContainerSetContentPacket) {
        if (!StorageGuiModule.enabled) return
        if (packet.containerId() != currentWindowId) return

        if (onStorageMenu) {
            updateStorageMenu(packet.items())
            return
        }

        if (currentPageId !in 0 until TOTAL_PAGES) return
        val topSize = (packet.items().size - 36).coerceAtLeast(0)
        val rows = ((topSize / 9) - 1).coerceIn(0, 5)
        val page = pages[currentPageId]
        page.rows = rows
        for (index in 0 until 45) {
            page.items[index] = if (index < rows * 9) packet.items().getOrNull(index + 9)?.copy() else null
        }
        if (page.displayStack == null) {
            page.displayStack = defaultSelectorStack(currentPageId)
        }
        storagePresent[currentPageId] = true
        rebuildDisplayMaps()
        saveCache()
    }

    fun onContainerSlot(packet: ClientboundContainerSetSlotPacket) {
        if (!StorageGuiModule.enabled) return
        if (packet.getContainerId() != currentWindowId) return

        if (onStorageMenu) {
            updateStorageSelectorSlot(packet.getSlot(), packet.getItem())
            return
        }

        if (currentPageId !in 0 until TOTAL_PAGES) return
        val page = pages[currentPageId]
        val slot = packet.getSlot()
        if (slot in 9 until 9 + (page.rows.coerceAtLeast(0) * 9)) {
            page.items[slot - 9] = packet.getItem().copy()
            rebuildRenderMap()
            saveCache()
        }
    }

    fun placeholderStack(id: Int): ItemStack {
        return when {
            id < MAX_ENDER_CHEST_PAGES -> ItemStack(Items.ENDER_CHEST)
            else -> ItemStack(Items.CHEST)
        }
    }

    fun hasLoadedPage(id: Int): Boolean {
        val page = pages.getOrNull(id) ?: return false
        return page.rows > 0 || page.displayStack != null || page.items.any { it != null }
    }

    fun reset() {
        currentWindowId = -1
        currentPageId = -1
        onStorageMenu = false
        interactiveOverlayPageId = -1
        searchQuery = ""
        for (index in 0 until TOTAL_PAGES) {
            storagePresent[index] = false
            pages[index] = StoragePage()
        }
        displayToStorageId.clear()
        displayToStorageIdRender.clear()
        requestedOverlayPageId = -1
    }

    private fun isStorageTitle(title: String): Boolean {
        val clean = TextSanitizer.stripFormatting(title).trim()
        return clean == "Storage" ||
            storageRegex.matches(clean) ||
            enderChestRegex.matches(clean) ||
            backpackRegex.matches(clean)
    }

    private fun updateStorageMenu(items: List<ItemStack>) {
        for (slot in 9..17) {
            updateStorageSelectorSlot(slot, items.getOrNull(slot) ?: ItemStack.EMPTY)
        }
        for (slot in 27..44) {
            updateStorageSelectorSlot(slot, items.getOrNull(slot) ?: ItemStack.EMPTY)
        }
    }

    private fun updateStorageSelectorSlot(slot: Int, stack: ItemStack) {
        val pageId = when (slot) {
            in 9..17 -> slot - 9
            in 27..44 -> (slot - 27) + MAX_ENDER_CHEST_PAGES
            else -> return
        }

        val present = isUsableSelectorStack(stack)
        storagePresent[pageId] = present
        val page = pages[pageId]
        page.displayStack = if (present && !stack.isEmpty) stack.copy() else null
        if (page.title == null) {
            page.title = if (pageId < MAX_ENDER_CHEST_PAGES) {
                "Ender Chest ${pageId + 1}"
            } else {
                "Backpack ${pageId + 1 - MAX_ENDER_CHEST_PAGES}"
            }
        }
        if (currentPageId == pageId) {
            page.displayStack = page.displayStack ?: defaultSelectorStack(pageId)
        }
        rebuildDisplayMaps()
        saveCache()
    }

    private fun isUsableSelectorStack(stack: ItemStack): Boolean {
        if (stack.isEmpty) return false
        val item = stack.item
        return item != Items.GRAY_DYE && item != Items.RED_STAINED_GLASS_PANE
    }

    private fun defaultSelectorStack(pageId: Int): ItemStack = placeholderStack(pageId)

    private fun sendClick(
        gameMode: MultiPlayerGameMode,
        player: Player,
        containerId: Int,
        slot: Int,
        button: Int
    ): Boolean {
        return try {
            gameMode.handleContainerInput(containerId, slot, button, ContainerInput.PICKUP, player)
            true
        } catch (_: Throwable) {
            false
        }
    }

    private fun pageMatchesSearch(pageId: Int, query: String): Boolean {
        val tokens = query.lowercase().split(' ').map { it.trim() }.filter { it.isNotBlank() }
        if (tokens.isEmpty()) return true
        val haystack = buildString {
            val page = pages[pageId]
            append(TextSanitizer.stripFormatting(page.title.orEmpty()).lowercase())
            append(' ')
            append(TextSanitizer.stripFormatting(page.displayStack?.hoverName?.string.orEmpty()).lowercase())
            page.items.forEach { stack ->
                if (stack != null && !stack.isEmpty) {
                    append(' ')
                    append(TextSanitizer.stripFormatting(stack.hoverName.string).lowercase())
                }
            }
        }
        return tokens.all(haystack::contains)
    }

    private fun rebuildDisplayMaps() {
        displayToStorageId.clear()
        var displayIndex = 0
        for (i in 0 until TOTAL_PAGES) {
            if (storagePresent[i] || hasLoadedPage(i) || i == currentPageId) {
                displayToStorageId[displayIndex++] = i
            }
        }
        if (displayToStorageId.isEmpty() && currentPageId in 0 until TOTAL_PAGES) {
            displayToStorageId[0] = currentPageId
        }
        rebuildRenderMap()
    }

    private fun rebuildRenderMap() {
        displayToStorageIdRender.clear()
        if (displayToStorageId.isEmpty()) {
            var displayIndex = 0
            for (i in 0 until TOTAL_PAGES) {
                if (storagePresent[i] || hasLoadedPage(i) || i == currentPageId) {
                    displayToStorageId[displayIndex++] = i
                }
            }
        }
        var renderIndex = 0
        for ((_, storageId) in displayToStorageId) {
            val matches = searchQuery.isBlank() || pageMatchesSearch(storageId, searchQuery)
            pages[storageId].matchesSearch = matches
            if (matches) {
                displayToStorageIdRender[renderIndex++] = storageId
            }
        }
    }

    fun silentOpenPage(id: Int) {
        if (id !in 0 until TOTAL_PAGES) return
        Minecraft.getInstance().connection ?: return
        if (id == currentPageId) return
        if (hasLoadedPage(id)) return
        snapshotCurrentPageFromOpenMenu()

        if (id < MAX_ENDER_CHEST_PAGES) {
            Minecraft.getInstance().connection?.sendCommand("enderchest ${id + 1}")
        } else {
            Minecraft.getInstance().connection?.sendCommand("backpack ${id + 1 - MAX_ENDER_CHEST_PAGES}")
        }
    }

    private fun snapshotCurrentPageFromOpenMenu() {
        if (onStorageMenu) return
        val pageId = currentPageId
        if (pageId !in 0 until TOTAL_PAGES) return
        val client = Minecraft.getInstance()
        val player = client.player ?: return
        val menu = player.containerMenu
        if (menu !is ChestMenu && menu !is ShulkerBoxMenu) return
        if (menu.containerId != currentWindowId) return

        val topSize = (menu.slots.size - 36).coerceAtLeast(0)
        val rows = ((topSize / 9) - 1).coerceIn(0, 5)
        val page = pages[pageId]
        page.rows = rows
        for (index in 0 until 45) {
            page.items[index] = if (index < rows * 9) menu.slots.getOrNull(index + 9)?.item?.copy() else null
        }
        storagePresent[pageId] = true
        rebuildDisplayMaps()
        saveCache()
    }

    private fun loadCache() {
        runCatching {
            if (!Files.exists(configPath)) return
            val root = Files.newBufferedReader(configPath).use { gson.fromJson(it, JsonObject::class.java) } ?: return
            val pagesObject = root.getAsJsonObject("pages") ?: return
            for ((key, value) in pagesObject.entrySet()) {
                val id = key.toIntOrNull() ?: continue
                if (id !in 0 until TOTAL_PAGES || !value.isJsonObject) continue
                val obj = value.asJsonObject
                val page = pages[id]
                page.rows = obj.get("rows")?.asInt ?: -1
                page.title = obj.get("title")?.takeUnless { it.isJsonNull }?.asString
                page.displayStack = decodeStack(obj.get("displayStack"))
                val itemsArray = obj.getAsJsonArray("items") ?: JsonArray()
                for (index in 0 until 45) {
                    page.items[index] = decodeStack(itemsArray.getOrNull(index))
                }
                storagePresent[id] = page.displayStack != null || page.items.any { it != null } || page.rows > 0
            }
            rebuildDisplayMaps()
        }
    }

    private fun saveCache() {
        runCatching {
            Files.createDirectories(configPath.parent)
            val root = JsonObject()
            val pagesObject = JsonObject()
            for (id in 0 until TOTAL_PAGES) {
                val page = pages[id]
                if (!(storagePresent[id] || hasLoadedPage(id))) continue
                val obj = JsonObject()
                obj.addProperty("rows", page.rows)
                if (!page.title.isNullOrBlank()) {
                    obj.addProperty("title", page.title)
                }
                obj.add("displayStack", encodeStack(page.displayStack))
                val items = JsonArray()
                for (index in 0 until 45) {
                    items.add(encodeStack(page.items[index]))
                }
                obj.add("items", items)
                pagesObject.add(id.toString(), obj)
            }
            root.add("pages", pagesObject)
            Files.newBufferedWriter(configPath).use { gson.toJson(root, it) }
        }
    }

    private fun encodeStack(stack: ItemStack?): JsonElement {
        if (stack == null || stack.isEmpty) return JsonNull.INSTANCE
        return ItemStack.OPTIONAL_CODEC.encodeStart(JsonOps.INSTANCE, stack).result().orElse(JsonNull.INSTANCE)
    }

    private fun decodeStack(element: JsonElement?): ItemStack? {
        if (element == null || element.isJsonNull) return null
        return ItemStack.OPTIONAL_CODEC.parse(JsonOps.INSTANCE, element).result().orElse(ItemStack.EMPTY).takeUnless { it.isEmpty }
    }

    private fun JsonArray.getOrNull(index: Int): JsonElement? = if (index in 0 until size()) get(index) else null
}
