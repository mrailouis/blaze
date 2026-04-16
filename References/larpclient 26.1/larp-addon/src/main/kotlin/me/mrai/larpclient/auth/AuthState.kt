package me.mrai.larpclient.auth

enum class AuthState {
    UNCHECKED,
    IN_PROGRESS,
    AUTHENTICATED,
    DENIED
}
