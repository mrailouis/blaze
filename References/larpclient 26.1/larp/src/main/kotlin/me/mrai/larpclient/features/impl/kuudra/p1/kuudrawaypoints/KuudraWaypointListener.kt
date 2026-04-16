package me.mrai.larpclient.features.impl.kuudra.p1.kuudrawaypoints

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents

object KuudraWaypointListener {
    private var registered = false

    fun register() {
        if (registered) return
        registered = true

        ClientPlayConnectionEvents.DISCONNECT.register { _, _ ->
            KuudraWaypointModule.onWorldChange()
        }
    }
}
