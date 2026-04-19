package me.mrai.blaze.feature.blaze

data class BlazePathfinderConfig(
    val rotationSpeedMultiplier: Double = 1.6,
    val rotationSmoothness: Double = 0.92,
    val movementLookBias: Double = 0.9,
    val headFreedomDegrees: Double = 32.0
)
