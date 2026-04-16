package me.mrai.larpclient.features.impl.kuudra.p1.autopearls

import net.minecraft.client.Minecraft
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

object PearlBypassController {
    var enabled: Boolean = false
        private set

    fun toggle(): Boolean {
        enabled = !enabled
        return enabled
    }

    fun onClientTick() {
        if (!enabled) return

        val player = Minecraft.getInstance().player ?: return
        val cooldowns = player.getCooldowns()
        val cooldownGroup = cooldowns.getCooldownGroup(ItemStack(Items.ENDER_PEARL))
        cooldowns.removeCooldown(cooldownGroup)
    }

    fun shouldBypass(stack: ItemStack): Boolean {
        return enabled && stack.`is`(Items.ENDER_PEARL)
    }
}
