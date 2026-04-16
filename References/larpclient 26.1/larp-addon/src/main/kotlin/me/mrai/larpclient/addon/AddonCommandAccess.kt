package me.mrai.larpclient.addon

import me.mrai.larpclient.util.LarpBranding
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource

object AddonCommandAccess {
    var accessCheck: () -> Boolean = { false }

    fun granted(): Boolean = accessCheck()

    fun requireCommandAccess(source: FabricClientCommandSource): Boolean {
        if (granted()) return true

        source.sendFeedback(
            LarpBranding.prefixed(
                LarpBranding.error("LarpClient authentication is required for this command.")
            )
        )
        return false
    }
}
