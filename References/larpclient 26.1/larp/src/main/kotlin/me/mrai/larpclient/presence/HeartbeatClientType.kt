package me.mrai.larpclient.presence

enum class HeartbeatClientType(val wireValue: String) {
    MOD("larp-mod"),
    ADDON("larp-addon")
}
