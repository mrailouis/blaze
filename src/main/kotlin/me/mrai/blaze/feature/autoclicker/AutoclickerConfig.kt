package me.mrai.blaze.feature.autoclicker

import com.mojang.blaze3d.platform.InputConstants

enum class AutoclickerSide {
    LEFT,
    RIGHT;

    val displayName: String
        get() = name.lowercase().replaceFirstChar(Char::uppercase)
}

enum class AutoclickerActivationMode {
    TOGGLE,
    HOLD;

    fun cycle(): AutoclickerActivationMode {
        return entries[(ordinal + 1) % entries.size]
    }

    val displayName: String
        get() = name.lowercase().replaceFirstChar(Char::uppercase)
}

enum class BlazeInputType {
    KEYSYM,
    MOUSE
}

data class BlazeInputBind(
    val type: BlazeInputType = BlazeInputType.KEYSYM,
    val value: Int = InputConstants.UNKNOWN.value
) {
    fun isBound(): Boolean = value != InputConstants.UNKNOWN.value
}

data class SideAutoclickerConfig(
    val enabled: Boolean = true,
    val cps: Int = 10,
    val activationMode: AutoclickerActivationMode = AutoclickerActivationMode.HOLD,
    val bind: BlazeInputBind = BlazeInputBind()
)

data class AutoclickerConfig(
    val left: SideAutoclickerConfig = SideAutoclickerConfig(),
    val right: SideAutoclickerConfig = SideAutoclickerConfig()
)
