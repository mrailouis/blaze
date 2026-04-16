package me.mrai.larpclient.features.impl.skyblock.general.camerafix

import me.mrai.larpclient.module.BoolSetting
import me.mrai.larpclient.module.Module
import me.mrai.larpclient.module.ModuleCategory
import me.mrai.larpclient.module.SliderSetting
import net.minecraft.client.CameraType

object CameraFixModule : Module(
    name = "Camera Fix",
    description = "Disables front camera and provides various camera improvements.",
    category = ModuleCategory.SKYBLOCK_GENERAL
) {
    private val disableFrontCamera = BoolSetting("Disable Front Camera", true)
    private val hideFireOverlay = BoolSetting("Hide Fire Overlay", false)
    private val hidePortalOverlay = BoolSetting("Hide Portal Overlay", false)
    private val hideWaterOverlay = BoolSetting("Hide Water Overlay", false)
    private val hideBlockOverlay = BoolSetting("Hide Block Overlay", false)
    private val cameraClip = BoolSetting("Camera Clip", false)

    init {
        settings += disableFrontCamera
        settings += hideFireOverlay
        settings += hidePortalOverlay
        settings += hideWaterOverlay
        settings += hideBlockOverlay
        settings += cameraClip
    }

    override fun onTick() {
        if (!enabled) return
        val mc = client()

        if (disableFrontCamera.value && mc.options.cameraType == CameraType.THIRD_PERSON_FRONT) {
            mc.options.cameraType = CameraType.FIRST_PERSON
        }
    }

    fun shouldHideFireOverlay(): Boolean = enabled && hideFireOverlay.value
    fun shouldHidePortalOverlay(): Boolean = enabled && hidePortalOverlay.value
    fun shouldHideWaterOverlay(): Boolean = enabled && hideWaterOverlay.value
    fun shouldHideBlockOverlay(): Boolean = enabled && hideBlockOverlay.value
    fun shouldEnableCameraClip(): Boolean = enabled && cameraClip.value
}

