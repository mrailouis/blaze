package me.mrai.larpclient.features.impl.misc.ui.itemrarity

import me.mrai.larpclient.util.TextSanitizer
import net.minecraft.core.component.DataComponents
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.component.ItemLore

object RarityDetector {
    fun getRarity(stack: ItemStack): Rarity? {
        if (stack.isEmpty) return null

        val lore: ItemLore = stack.get(DataComponents.LORE) ?: return null
        val lines = lore.lines().map { TextSanitizer.stripFormatting(it.string) }
        return Rarity.fromLore(lines)
    }
}
