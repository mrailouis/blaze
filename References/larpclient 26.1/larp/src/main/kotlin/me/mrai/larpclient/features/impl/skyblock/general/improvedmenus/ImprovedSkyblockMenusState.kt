package me.mrai.larpclient.features.impl.skyblock.general.improvedmenus

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import me.mrai.larpclient.features.impl.skyblock.general.storagegui.StorageGuiManager
import me.mrai.larpclient.util.TextSanitizer
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.gui.screens.inventory.ContainerScreen
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.core.component.DataComponents
import me.mrai.larpclient.mixin.AbstractContainerScreenAccessor
import net.minecraft.resources.Identifier
import net.minecraft.world.inventory.ChestMenu
import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.Blocks
import java.io.IOException
import kotlin.math.max
import kotlin.math.min
import me.mrai.larpclient.render.blur.BlurManager

object ImprovedSkyblockMenusState {
    private val gson = GsonBuilder().create()
    private const val DEFAULT_TEXT_COLOR = 4210752
    private val toggleOn = Identifier.fromNamespaceAndPath("larpclient", "dynamic_54/toggle_on.png")
    private val toggleOff = Identifier.fromNamespaceAndPath("larpclient", "dynamic_54/toggle_off.png")

    fun status(): String {
        return "Style ${ImprovedSkyblockMenusModule.style.selected}"
    }

    fun shouldOverride(screen: AbstractContainerScreen<*>): Boolean {
        if (!ImprovedSkyblockMenusModule.enabled) return false
        if (StorageGuiManager.shouldReplaceScreen(screen)) return false
        if (screen !is ContainerScreen) return false
        val menu = screen.menu
        if (menu !is ChestMenu) return false
        if (isBlacklisted(screen.title.string)) return false
        return chestSlots(menu).any { slot -> isBlankStack(slot.item) }
    }

    fun textColor(screen: AbstractContainerScreen<*>): Int {
        if (!shouldOverride(screen)) return DEFAULT_TEXT_COLOR
        return loadTextColor(styleId())
    }

    fun shouldHideSlot(screen: AbstractContainerScreen<*>, slot: Slot): Boolean {
        if (!shouldOverride(screen)) return false
        if (!isChestSlot(screen, slot)) return false
        return isToggle(slot.item) || (ImprovedSkyblockMenusModule.hideEmptyPanes.value && isBlankStack(slot.item))
    }

    fun renderChestBackground(screen: ContainerScreen, graphics: GuiGraphicsExtractor) {
        if (!shouldOverride(screen)) return

        val menu = screen.menu as ChestMenu
        val accessor = screen as AbstractContainerScreenAccessor
        val imageWidth = accessor.getLarpclientImageWidth()
        val imageHeight = accessor.getLarpclientImageHeight()
        val left = (screen.width - imageWidth) / 2
        val top = (screen.height - imageHeight) / 2
        val rows = menu.rowCount
        val style = styleId()
        val base = baseTexture(style)

        BlurManager.drawBlurredRegion(graphics, left, top, imageWidth, imageHeight)

        graphics.blit(
            RenderPipelines.GUI_TEXTURED,
            base,
            left,
            top,
            0f,
            0f,
            imageWidth,
            rows * 18 + 17,
            256,
            256
        )
        graphics.blit(
            RenderPipelines.GUI_TEXTURED,
            base,
            left,
            top + rows * 18 + 17,
            0f,
            126f,
            imageWidth,
            96,
            256,
            256
        )

        val slots = Array(9) { BooleanArray(rows) }
        val buttons = Array(9) { BooleanArray(rows) }
        val slotTexture = slotTexture(style)
        val buttonTexture = buttonTexture(style)
        val ultrasequencer = screen.title.string.startsWith("Ultrasequencer") && !screen.title.string.contains("Stakes")
        val superpairs = screen.title.string.startsWith("Superpairs") && !screen.title.string.contains("Stakes")

        for (index in 0 until rows * 9) {
            val slot = menu.getSlot(index)
            val stack = slot.item
            var isButton = isButtonStack(stack)

            if (ultrasequencer && stack.`is`(Items.LIME_DYE)) {
                isButton = false
            }
            if (superpairs && index > 9 && index < rows * 9 - 9) {
                isButton = false
            }

            val x = index % 9
            val y = index / 9
            buttons[x][y] = isButton
            slots[x][y] = !isBlankStack(stack) && !isButton && !isToggle(stack)
        }

        for (index in 0 until rows * 9) {
            val slot = menu.getSlot(index)
            val stack = slot.item
            val xIndex = index % 9
            val yIndex = index / 9
            val x = left + 7 + xIndex * 18
            val y = top + 17 + yIndex * 18

            when {
                isToggleOn(stack) -> drawFull(graphics, toggleOn, x, y)
                isToggleOff(stack) -> drawFull(graphics, toggleOff, x, y)
                buttons[xIndex][yIndex] -> drawCtm(graphics, buttonTexture, x, y, buttons, xIndex, yIndex)
                slots[xIndex][yIndex] -> drawCtm(graphics, slotTexture, x, y, slots, xIndex, yIndex)
            }
        }
    }

    private fun drawFull(graphics: GuiGraphicsExtractor, texture: Identifier, x: Int, y: Int) {
        graphics.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, 0f, 0f, 18, 18, 18, 18)
    }

    private fun drawCtm(
        graphics: GuiGraphicsExtractor,
        texture: Identifier,
        x: Int,
        y: Int,
        mask: Array<BooleanArray>,
        xi: Int,
        yi: Int
    ) {
        val up = yi > 0 && mask[xi][yi - 1]
        val right = xi < mask.size - 1 && mask[xi + 1][yi]
        val down = yi < mask[xi].size - 1 && mask[xi][yi + 1]
        val left = xi > 0 && mask[xi - 1][yi]
        val upleft = yi > 0 && xi > 0 && mask[xi - 1][yi - 1]
        val upright = yi > 0 && xi < mask.size - 1 && mask[xi + 1][yi - 1]
        val downright = xi < mask.size - 1 && yi < mask[xi].size - 1 && mask[xi + 1][yi + 1]
        val downleft = xi > 0 && yi < mask[xi].size - 1 && mask[xi - 1][yi + 1]
        val ctm = getCtmIndex(up, right, down, left, upleft, upright, downright, downleft)
        val u = (ctm % 12) * 19f
        val v = (ctm / 12) * 19f
        graphics.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, u, v, 18, 18, 228, 76)
    }

    private fun styleId(): Int = styleId(ImprovedSkyblockMenusModule.style.selected)

    private fun styleId(selected: String): Int = selected.toIntOrNull()?.coerceIn(1, 7) ?: 1

    private fun baseTexture(style: Int): Identifier =
        Identifier.fromNamespaceAndPath("larpclient", "dynamic_54/style$style/dynamic_54.png")

    private fun slotTexture(style: Int): Identifier =
        Identifier.fromNamespaceAndPath("larpclient", "dynamic_54/style$style/dynamic_54_slot_ctm.png")

    private fun buttonTexture(style: Int): Identifier =
        Identifier.fromNamespaceAndPath("larpclient", "dynamic_54/style$style/dynamic_54_button_ctm.png")

    private fun loadTextColor(style: Int): Int {
        val id = Identifier.fromNamespaceAndPath("larpclient", "dynamic_54/style$style/dynamic_config.json")
        return try {
            val resource = Minecraft.getInstance().resourceManager.openAsReader(id)
            resource.use { reader ->
                val json = gson.fromJson(reader, JsonObject::class.java) ?: return DEFAULT_TEXT_COLOR
                val raw = json.get("text-colour")?.asString ?: return DEFAULT_TEXT_COLOR
                raw.toLong(16).toInt()
            }
        } catch (_: IOException) {
            DEFAULT_TEXT_COLOR
        } catch (_: Throwable) {
            DEFAULT_TEXT_COLOR
        }
    }

    private fun isBlacklisted(title: String): Boolean {
        return title.trim().lowercase().startsWith("navigate the maze")
    }

    private fun isChestSlot(screen: AbstractContainerScreen<*>, slot: Slot): Boolean {
        val menu = (screen.menu as? ChestMenu) ?: return false
        return slot.container !== Minecraft.getInstance().player?.inventory && slot.index < menu.rowCount * 9
    }

    private fun chestSlots(menu: ChestMenu): List<Slot> {
        val upperCount = menu.rowCount * 9
        return menu.slots.take(upperCount)
    }

    private fun isBlankStack(stack: ItemStack): Boolean {
        if (stack.isEmpty) return false
        val item = stack.item as? BlockItem ?: return false
        if (item.block != Blocks.BLACK_STAINED_GLASS_PANE) return false
        return TextSanitizer.stripFormatting(stack.hoverName.string).isBlank()
    }

    private fun isButtonStack(stack: ItemStack): Boolean {
        if (stack.isEmpty) return false
        return !isBlankStack(stack) && !isToggle(stack)
    }

    private fun isToggle(stack: ItemStack): Boolean = isToggleOn(stack) || isToggleOff(stack)

    private fun isToggleOn(stack: ItemStack): Boolean {
        val lore = stack.get(DataComponents.LORE) ?: return false
        val lines = lore.lines().map { TextSanitizer.stripFormatting(it.string).lowercase() }
        return lines.size == 1 && lines[0] == "click to disable!"
    }

    private fun isToggleOff(stack: ItemStack): Boolean {
        val lore = stack.get(DataComponents.LORE) ?: return false
        val lines = lore.lines().map { TextSanitizer.stripFormatting(it.string).lowercase() }
        return lines.size == 1 && lines[0] == "click to enable!"
    }

    private fun getCtmIndex(
        up: Boolean,
        right: Boolean,
        down: Boolean,
        left: Boolean,
        upleft: Boolean,
        upright: Boolean,
        downright: Boolean,
        downleft: Boolean
    ): Int {
        return when {
            up && right && down && left -> when {
                upleft && upright && downright && downleft -> 26
                upleft && upright && downright && !downleft -> 33
                upleft && upright && !downright && downleft -> 32
                upleft && upright && !downright && !downleft -> 11
                upleft && !upright && downright && downleft -> 44
                upleft && !upright && downright && !downleft -> 35
                upleft && !upright && !downright && downleft -> 10
                upleft && !upright && !downright && !downleft -> 20
                !upleft && upright && downright && downleft -> 45
                !upleft && upright && downright && !downleft -> 23
                !upleft && upright && !downright && downleft -> 34
                !upleft && upright && !downright && !downleft -> 8
                !upleft && !upright && downright && downleft -> 22
                !upleft && !upright && downright && !downleft -> 9
                !upleft && !upright && !downright && downleft -> 21
                else -> 46
            }
            up && right && down && !left -> when {
                !upright && !downright -> 6
                !upright -> 28
                !downright -> 30
                else -> 25
            }
            up && right && !down && left -> when {
                !upleft && !upright -> 18
                !upleft -> 40
                !upright -> 42
                else -> 38
            }
            up && right && !down && !left -> if (!upright) 16 else 37
            up && !right && down && left -> when {
                !upleft && !downleft -> 19
                !upleft -> 43
                !downleft -> 41
                else -> 27
            }
            up && !right && down && !left -> 24
            up && !right && !down && left -> if (!upleft) 17 else 39
            up && !right && !down && !left -> 36
            !up && right && down && left -> when {
                !downleft && !downright -> 7
                !downleft -> 31
                !downright -> 29
                else -> 14
            }
            !up && right && down && !left -> if (!downright) 4 else 13
            !up && right && !down && left -> 2
            !up && right && !down && !left -> 1
            !up && !right && down && left -> if (!downleft) 5 else 15
            !up && !right && down && !left -> 12
            !up && !right && !down && left -> 3
            else -> 0
        }
    }
}
