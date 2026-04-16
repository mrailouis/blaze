package me.mrai.larpclient.features.impl.kuudra.general.leftclickshop

import me.mrai.larpclient.module.Module
import me.mrai.larpclient.module.ModuleCategory
import net.minecraft.client.Minecraft
import net.minecraft.world.InteractionHand

object LeftClickShopModule : Module(
    name = "Left Click Shop",
    description = "Use left click with the Open Shop item to send a right click while keeping swing.",
    category = ModuleCategory.KUUDRA_GENERAL
) {
    private var lastAttackPressed = false
    private var nextAllowedRightClickAt = 0L
    private var suppressedUntil = 0L

    override fun onEnable() {
        lastAttackPressed = false
        nextAllowedRightClickAt = 0L
    }

    override fun onDisable() {
        lastAttackPressed = false
        nextAllowedRightClickAt = 0L
        suppressedUntil = 0L
    }

    override fun onTick() {
        val client = Minecraft.getInstance()
        val player = client.player
        val gameMode = client.gameMode

        if (player == null || gameMode == null) {
            lastAttackPressed = false
            return
        }

        if (client.screen != null) {
            lastAttackPressed = false
            return
        }

        val now = System.currentTimeMillis()
        val attackPressed = client.options.keyAttack.isDown

        val shouldInteractionHandleClick =
            attackPressed &&
                    !lastAttackPressed &&
                    now >= nextAllowedRightClickAt &&
                    now >= suppressedUntil &&
                    isHoldingOpenShop(player.mainHandItem.hoverName.string)

        if (shouldInteractionHandleClick) {
            gameMode.useItem(player, InteractionHand.MAIN_HAND)
            nextAllowedRightClickAt = now + 1000L
        }

        lastAttackPressed = attackPressed
    }

    fun suppressForFiveSeconds() {
        suppressedUntil = System.currentTimeMillis() + 5000L
    }

    private fun isHoldingOpenShop(name: String): Boolean {
        return name.contains("Open Shop", ignoreCase = true)
    }
}