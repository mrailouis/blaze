package me.mrai.larpclient.features.impl.kuudra.general.blockpickobulus

import me.mrai.larpclient.module.BoolSetting
import me.mrai.larpclient.module.Module
import me.mrai.larpclient.module.ModuleCategory
import me.mrai.larpclient.util.TextSanitizer
import net.minecraft.client.Minecraft
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

object BlockPickobulusModule : Module(
    name = "Block Pickobulus",
    description = "Blocks pickaxe and prismarine shard right-clicks during Kuudra build.",
    category = ModuleCategory.KUUDRA_GENERAL
) {
    private val blockPickaxes = BoolSetting("Block Pickaxes", true)
    private val blockPrismarineShard = BoolSetting("Block Prismarine Shard", true)

    private const val BUILD_START_MESSAGE = "[npc]elle:talktometobegin!"
    private const val BUILD_END_MESSAGE = "[npc]elle:phew!theballistaisfinallyready!itshouldbestrongenoughtotankkuudra'sblowsnow!"

    @Volatile
    private var blockWindowActive = false

    init {
        settings += listOf(blockPickaxes, blockPrismarineShard)
    }

    override fun onDisable() {
        blockWindowActive = false
    }

    fun onWorldChange() {
        blockWindowActive = false
    }

    fun onChatMessage(rawMessage: String) {
        when (TextSanitizer.compactLower(rawMessage)) {
            BUILD_START_MESSAGE -> blockWindowActive = true
            BUILD_END_MESSAGE -> blockWindowActive = false
        }
    }

    fun shouldCancelUse(stack: ItemStack): Boolean {
        if (!enabled || !blockWindowActive || stack.isEmpty) return false
        if (Minecraft.getInstance().player == null) return false

        val item = stack.item
        return (blockPickaxes.value && item.descriptionId.contains("pickaxe", ignoreCase = true)) ||
            (blockPrismarineShard.value && item === Items.PRISMARINE_SHARD)
    }
}
