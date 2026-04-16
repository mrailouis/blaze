package me.mrai.larpclient.features.impl.skyblock.general.wardrobekeybinds

import me.mrai.larpclient.integration.AddonAutomationAccess
import me.mrai.larpclient.module.BoolSetting
import me.mrai.larpclient.module.KeybindSetting
import me.mrai.larpclient.module.Module
import me.mrai.larpclient.module.ModuleCategory
import me.mrai.larpclient.module.shownWhen
import me.mrai.larpclient.util.LarpBranding
import me.mrai.larpclient.util.TextSanitizer
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.world.inventory.ContainerInput
import org.lwjgl.glfw.GLFW

object WardrobeKeybindsModule : Module(
    name = "Wardrobe Keybinds",
    description = "Lets you swap wardrobe slots with keybinds while the wardrobe GUI is open.",
    category = ModuleCategory.SKYBLOCK_GENERAL
) {
    private const val FIRST_WARDROBE_SLOT = 36
    private const val PREVIOUS_PAGE_SLOT = 45
    private const val NEXT_PAGE_SLOT = 53

    private val wardrobeTitlePattern = Regex("""Wardrobe \((?<current>\d+)/(?<total>\d+)\)""", RegexOption.IGNORE_CASE)
    private val equippedSlotPattern = Regex("""Slot \d+: Equipped""", RegexOption.IGNORE_CASE)

    private val nextPageKeybind = KeybindSetting("Next Page", GLFW.GLFW_KEY_RIGHT)
    private val previousPageKeybind = KeybindSetting("Previous Page", GLFW.GLFW_KEY_LEFT)
    private val unequipKeybind = KeybindSetting("Unequip", GLFW.GLFW_KEY_UNKNOWN)
    private val disableUnequip = BoolSetting("Disable Unequip", false)
    private val showAdvanced = BoolSetting("Show Advanced", false)
    private val autoCloseWardrobe = BoolSetting("Auto Close Wardrobe", false)
        .shownWhen { AddonAutomationAccess.hasAddonFeatures() }

    private val slotKeybinds = List(9) { index ->
        KeybindSetting("Wardrobe ${index + 1}", GLFW.GLFW_KEY_1 + index)
            .shownWhen { showAdvanced.value }
    }

    init {
        settings += nextPageKeybind
        settings += previousPageKeybind
        settings += unequipKeybind
        settings += disableUnequip
        settings += showAdvanced
        settings += slotKeybinds
        settings += autoCloseWardrobe
    }

    fun onScreenKeyPressed(screen: AbstractContainerScreen<*>, key: Int): Boolean {
        if (!enabled) return false
        val client = Minecraft.getInstance()
        val player = client.player ?: return false
        val gameMode = client.gameMode ?: return false

        val title = TextSanitizer.stripFormatting(screen.title.string).trim()
        val wardrobeMatch = wardrobeTitlePattern.find(title) ?: return false
        val currentPage = wardrobeMatch.groups["current"]?.value?.toIntOrNull() ?: return true
        val totalPages = wardrobeMatch.groups["total"]?.value?.toIntOrNull() ?: return true
        val equippedIndex = equippedSlotIndex(screen)
        val matchedKeybind = isWardrobeKeybind(key)
        if (!matchedKeybind) return false
        val action = resolveAction(key, equippedIndex)

        if (action == null) {
            return true
        }

        when (action.kind) {
            ActionKind.PREVIOUS_PAGE -> if (currentPage <= 1) return true
            ActionKind.NEXT_PAGE -> if (currentPage >= totalPages) return true
            ActionKind.UNEQUIP -> {
                if (disableUnequip.value) return true
                if (!isClickableWardrobeSlot(screen, action.slot)) return true
            }
            ActionKind.SLOT -> {
                if (!isClickableWardrobeSlot(screen, action.slot)) return true
                if (disableUnequip.value && equippedIndex == action.slot) {
                    player.sendSystemMessage(
                        LarpBranding.prefixed(LarpBranding.error("Armor already equipped."))
                    )
                    return true
                }
            }
        }

        gameMode.handleContainerInput(screen.menu.containerId, action.slot, 0, ContainerInput.PICKUP, player)
        if (autoCloseWardrobe.value && action.kind != ActionKind.PREVIOUS_PAGE && action.kind != ActionKind.NEXT_PAGE) {
            player.closeContainer()
        }
        return true
    }

    private fun isWardrobeKeybind(key: Int): Boolean {
        if (key == GLFW.GLFW_KEY_UNKNOWN) return false
        if (nextPageKeybind.key == key) return true
        if (previousPageKeybind.key == key) return true
        if (unequipKeybind.key == key) return true
        return slotKeybinds.any { it.key == key }
    }

    private fun resolveAction(key: Int, equippedIndex: Int?): Action? {
        return when {
            key == previousPageKeybind.key -> Action(ActionKind.PREVIOUS_PAGE, PREVIOUS_PAGE_SLOT)
            key == nextPageKeybind.key -> Action(ActionKind.NEXT_PAGE, NEXT_PAGE_SLOT)
            key == unequipKeybind.key -> equippedIndex?.let { Action(ActionKind.UNEQUIP, it) }
            else -> {
                val slotIndex = slotKeybinds.indexOfFirst { it.key == key }
                if (slotIndex == -1) null else Action(ActionKind.SLOT, FIRST_WARDROBE_SLOT + slotIndex)
            }
        }
    }

    private fun equippedSlotIndex(screen: AbstractContainerScreen<*>): Int? {
        return screen.menu.slots.firstOrNull { slot ->
            equippedSlotPattern.containsMatchIn(TextSanitizer.stripFormatting(slot.item.hoverName.string))
        }?.index
    }

    private fun isClickableWardrobeSlot(screen: AbstractContainerScreen<*>, slotIndex: Int): Boolean {
        val slot = screen.menu.slots.getOrNull(slotIndex) ?: return false
        return !slot.item.isEmpty
    }

    private data class Action(
        val kind: ActionKind,
        val slot: Int
    )

    private enum class ActionKind {
        PREVIOUS_PAGE,
        NEXT_PAGE,
        UNEQUIP,
        SLOT
    }
}
