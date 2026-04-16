package me.mrai.larpclient.playeroverride

import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.world.level.GameType
import net.minecraft.world.scores.PlayerTeam
import java.util.UUID

object PlayerDisplayNameResolver {
    @JvmStatic
    fun resolveTabListName(uuid: UUID, fallbackName: String): Component {
        val connection = Minecraft.getInstance().connection ?: return Component.literal(fallbackName)
        val info = connection.getPlayerInfo(uuid) ?: return Component.literal(fallbackName)

        val base = info.tabListDisplayName?.copy()
            ?: PlayerTeam.formatNameForTeam(info.team, Component.literal(info.profile.name()))

        return if (info.gameMode == GameType.SPECTATOR) {
            base.withStyle(ChatFormatting.ITALIC)
        } else {
            base
        }
    }
}
