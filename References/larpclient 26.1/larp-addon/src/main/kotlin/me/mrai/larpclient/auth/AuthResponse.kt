package me.mrai.larpclient.auth

data class AuthResponse(
    val allowed: Boolean = false,
    val reason: String = "unknown",
    val expiresAt: String? = null,
    val boundUuid: String? = null
)