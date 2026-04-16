package me.mrai.larpclient.features.impl.skyblock.general.cancelinteract

import me.mrai.larpclient.module.Module
import me.mrai.larpclient.module.ModuleCategory
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.ItemStack

object CancelInteractModule : Module(
    name = "Cancel Interact",
    description = "Prevents block interaction so right click uses the held item instead.",
    category = ModuleCategory.SKYBLOCK_GENERAL
) {
    fun shouldUseItemInstead(stack: ItemStack): Boolean {
        if (!enabled) return false
        if (stack.isEmpty) return false
        if (stack.item is BlockItem) return false
        return true
    }
}
