package me.mrai.larpclient.features.impl.misc.other.headsscale

import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.AbstractSkullBlock

object HeadItemUtil {
    @JvmStatic
    fun isHeadItem(stack: ItemStack): Boolean {
        val item = stack.item
        return item is BlockItem && item.block is AbstractSkullBlock
    }
}
