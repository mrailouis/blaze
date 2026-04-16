package me.mrai.larpclient.features.impl.misc.ui.playercustomisations

import me.mrai.larpclient.module.BoolSetting
import me.mrai.larpclient.module.ComponentSetting
import me.mrai.larpclient.module.InfoSetting
import me.mrai.larpclient.module.Module
import me.mrai.larpclient.module.ModuleCategory
import me.mrai.larpclient.module.ModuleConfigManager
import me.mrai.larpclient.module.SliderSetting
import me.mrai.larpclient.module.shownWhen
import me.mrai.larpclient.playeroverride.PlayerOverrideManager
import me.mrai.larpclient.presence.HeartbeatManager

object PlayerCustomisationsModule : Module(
    name = "Player Customisations",
    description = "Local player size and custom naming. If your account has a matching database entry, you can publish it to other Larp users.",
    category = ModuleCategory.MISC_UI
) {
    private var hasHydratedServerState = false
    private var lastKnownEligibility = false

    private val databaseStatus = InfoSetting("Database Status") {
        val state = PlayerOverrideManager.getSelfCustomizationState()
        when {
            !state.checked -> "Checking..."
            !state.eligible -> "Local only"
            state.authenticated -> "In database"
            else -> "In database, waiting for IGN match"
        }
    }

    private val showToOthers = BoolSetting("Show to others", false)
        .shownWhen { PlayerOverrideManager.getSelfCustomizationState().eligible }

    private val sizeX = SliderSetting("Player Size X", 1.0, 0.1, 4.0, 0.05)
    private val sizeY = SliderSetting("Player Size Y", 1.0, 0.1, 4.0, 0.05)
    private val sizeZ = SliderSetting("Player Size Z", 1.0, 0.1, 4.0, 0.05)
    private val customName = ComponentSetting("Custom Name", "")

    init {
        settings += listOf(
            databaseStatus,
            showToOthers,
            sizeX,
            sizeY,
            sizeZ,
            customName
        )
    }

    override fun onEnable() {
        ensureHydratedServerState()
        syncLocalOverride()
        HeartbeatManager.requestImmediateHeartbeat()
    }

    override fun onDisable() {
        PlayerOverrideManager.clearLocalOverride()
        HeartbeatManager.requestImmediateHeartbeat()
    }

    override fun onTick() {
        ensureHydratedServerState()
        syncLocalOverride()
    }

    @JvmStatic
    fun shouldForceOwnNameTag(): Boolean = enabled

    @JvmStatic
    fun heartbeatPayload(): HeartbeatManager.PlayerCustomizationPayload {
        ensureHydratedServerState()
        val state = PlayerOverrideManager.getSelfCustomizationState()
        return HeartbeatManager.PlayerCustomizationPayload(
            customName = customName.text.trim().ifBlank { null },
            scaleX = sizeX.value.toFloat(),
            scaleY = sizeY.value.toFloat(),
            scaleZ = sizeZ.value.toFloat(),
            showToOthers = enabled && state.eligible && showToOthers.value,
            applyChanges = hasHydratedServerState && state.checked
        )
    }

    private fun ensureHydratedServerState() {
        val state = PlayerOverrideManager.getSelfCustomizationState()
        if (state.checked && state.eligible != lastKnownEligibility) {
            hasHydratedServerState = false
            lastKnownEligibility = state.eligible
        }

        if (!state.checked || hasHydratedServerState) {
            return
        }

        if (state.eligible && isPristineConfig()) {
            customName.text = state.customName.orEmpty()
            sizeX.value = state.scaleX.toDouble()
            sizeY.value = state.scaleY.toDouble()
            sizeZ.value = state.scaleZ.toDouble()
            showToOthers.value = state.showToOthers
            ModuleConfigManager.save()
        }

        hasHydratedServerState = true
    }

    private fun syncLocalOverride() {
        val player = client().player ?: return

        if (!enabled) {
            PlayerOverrideManager.clearLocalOverride()
            return
        }

        PlayerOverrideManager.setLocalOverride(
            uuid = player.uuid,
            minecraftName = player.gameProfile.name,
            customName = customName.text,
            scaleX = sizeX.value.toFloat(),
            scaleY = sizeY.value.toFloat(),
            scaleZ = sizeZ.value.toFloat()
        )
    }

    private fun isPristineConfig(): Boolean {
        return !showToOthers.value &&
            customName.text.isBlank() &&
            sizeX.value == 1.0 &&
            sizeY.value == 1.0 &&
            sizeZ.value == 1.0
    }
}
