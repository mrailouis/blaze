package me.mrai.larpclient.features.impl.misc.other.playerparticles

import com.mojang.math.Axis
import me.mrai.larpclient.module.BoolSetting
import me.mrai.larpclient.module.Module
import me.mrai.larpclient.module.ModuleCategory
import me.mrai.larpclient.module.SliderSetting
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.rendertype.RenderTypes
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.resources.Identifier
import net.minecraft.util.Brightness
import kotlin.math.cos
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.tan
import kotlin.random.Random

data class FallingSprite(
    val textureId: Identifier,
    val faceIndex: Int,
    val offsetXOrZ: Double,
    val startYOffset: Double,
    val spawnTick: Int,
    val driftVelocity: Double,
    val swayAmplitude: Double,
    val swayFrequency: Double,
    val swayPhase: Double,
    val yawSpeed: Float,
    val pitchSpeed: Float,
    val rollSpeed: Float,
    val baseYaw: Float,
    val basePitch: Float,
    val baseRoll: Float
)

object PlayerParticlesModule : Module(
    name = "Player Particles",
    description = "Renders falling textures on configurable faces around your body.",
    category = ModuleCategory.MISC_OTHER
) {
    private val particleTextures = listOf(
        Identifier.fromNamespaceAndPath("larp", "textures/particle_asset1.png"),
        Identifier.fromNamespaceAndPath("larp", "textures/particle_asset2.png"),
        Identifier.fromNamespaceAndPath("larp", "textures/particle_asset3.png"),
        Identifier.fromNamespaceAndPath("larp", "textures/particle_asset4.png")
    )
    private val fullBright = Brightness.FULL_BRIGHT.pack()

    private val particlesPerTick = SliderSetting("Particles Per Tick", 1.0, 1.0, 5.0, 1.0)
    private val sidePadding = SliderSetting("Side Padding", 0.5, 0.2, 1.5, 0.1)
    private val faceCount = SliderSetting("Faces", 4.0, 4.0, 16.0, 1.0)
    private val coerceTopAndBottom = BoolSetting("Coerce At Top And Bottom", false)
    private val heightScale = SliderSetting("Height Scale", 2.0, 1.0, 3.0, 0.2)
    private val topCrop = SliderSetting("Top Crop", 0.0, 0.0, 1.5, 0.1)
    private val bottomCrop = SliderSetting("Bottom Crop", 0.0, 0.0, 1.5, 0.1)
    private val verticalOffset = SliderSetting("Vertical Offset", 0.5, -1.0, 2.0, 0.1)
    private val fallSpeed = SliderSetting("Fall Speed", 0.04, 0.01, 0.15, 0.01)

    private val sprites = mutableListOf<FallingSprite>()
    private var currentTick = 0

    init {
        settings += particlesPerTick
        settings += sidePadding
        settings += faceCount
        settings += coerceTopAndBottom
        settings += heightScale
        settings += topCrop
        settings += bottomCrop
        settings += verticalOffset
        settings += fallSpeed
    }

    override fun onTick() {
        val client = client()
        client.level ?: return
        val player = client.player ?: return

        if (player.isRemoved || player.isSpectator) return

        val halfWidth = (player.bbWidth.toDouble() * 0.5) + sidePadding.value
        val faces = faceCount.value.toInt().coerceIn(4, 16)
        val faceHalfSpan = computeFaceHalfSpan(halfWidth, faces)
        val scaledHeight = (player.bbHeight.toDouble() * (heightScale.value * 0.5)).coerceAtLeast(0.5)
        val bottomY = player.y + verticalOffset.value + bottomCrop.value
        val topY = player.y + verticalOffset.value + scaledHeight - topCrop.value
        val croppedHeight = (topY - bottomY).coerceAtLeast(0.2)
        val baseY = bottomY + croppedHeight
        val particleCount = (particlesPerTick.value * 0.5).roundToInt().coerceIn(1, 3)

        // Spawn new sprites
        repeat(particleCount) {
            val chosenFace = Random.nextInt(faces)
            val offsetXOrZ = Random.nextDouble(-faceHalfSpan, faceHalfSpan)
            sprites.add(
                FallingSprite(
                    textureId = particleTextures.random(),
                    faceIndex = chosenFace,
                    offsetXOrZ = offsetXOrZ,
                    startYOffset = croppedHeight,
                    spawnTick = currentTick,
                    driftVelocity = Random.nextDouble(-0.003, 0.003),
                    swayAmplitude = Random.nextDouble(0.01, 0.035),
                    swayFrequency = Random.nextDouble(0.11, 0.20),
                    swayPhase = Random.nextDouble(0.0, Math.PI * 2.0),
                    yawSpeed = Random.nextDouble(-2.0, 2.0).toFloat(),
                    pitchSpeed = Random.nextDouble(-3.5, 3.5).toFloat(),
                    rollSpeed = Random.nextDouble(-6.0, 6.0).toFloat(),
                    baseYaw = Random.nextDouble(-12.0, 12.0).toFloat(),
                    basePitch = Random.nextDouble(-18.0, 18.0).toFloat(),
                    baseRoll = Random.nextDouble(-30.0, 30.0).toFloat()
                )
            )
        }

        // Remove sprites that have fallen off screen
        sprites.removeAll { sprite ->
            val age = (currentTick - sprite.spawnTick).toDouble()
            val fallDistance = computeFallDistance(age)
            sprite.startYOffset - fallDistance < -0.2
        }

        currentTick++
    }

    fun render(context: LevelRenderContext) {
        val minecraft = Minecraft.getInstance()
        val player = minecraft.player ?: return
        val cameraPos = minecraft.gameRenderer.mainCamera.position()
        val tickDelta = minecraft.deltaTracker.getGameTimeDeltaPartialTick(false).toDouble()
        val interpolatedPlayerPos = player.getPosition(tickDelta.toFloat())

        if (!enabled || player.isRemoved || player.isSpectator) return
        if (minecraft.options.cameraType.isFirstPerson) return
        if (sprites.isEmpty()) return

        val halfWidth = (player.bbWidth.toDouble() * 0.5) + sidePadding.value
        val faces = faceCount.value.toInt().coerceIn(4, 16)
        val baseRadius = computeFaceRadius(halfWidth)
        val centerX = interpolatedPlayerPos.x
        val centerZ = interpolatedPlayerPos.z
        val scaledHeight = (player.bbHeight.toDouble() * (heightScale.value * 0.5)).coerceAtLeast(0.5)
        val bottomY = interpolatedPlayerPos.y + verticalOffset.value + bottomCrop.value
        val topY = interpolatedPlayerPos.y + verticalOffset.value + scaledHeight - topCrop.value
        val bufferSource = minecraft.renderBuffers().bufferSource()
        val matrices = context.poseStack()
        matrices.pushPose()
        matrices.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z)

        for (sprite in sprites) {
            val age = (currentTick - sprite.spawnTick).toDouble() + tickDelta
            val fallDistance = computeFallDistance(age)
            val currentY = bottomY + sprite.startYOffset - fallDistance
            if (currentY < bottomY - 0.2 || currentY > topY + 0.2) continue
            val swayTime = age * sprite.swayFrequency + sprite.swayPhase
            val drift = (sprite.driftVelocity * age) + (sin(swayTime) * sprite.swayAmplitude) + (sin(swayTime * 0.45) * sprite.swayAmplitude * 0.35)
            val faceAngle = ((sprite.faceIndex % faces).toDouble() / faces.toDouble()) * (PI * 2.0)
            val normalX = sin(faceAngle)
            val normalZ = -cos(faceAngle)
            val tangentX = cos(faceAngle)
            val tangentZ = sin(faceAngle)
            val verticalT = (((currentY - bottomY) / (topY - bottomY).coerceAtLeast(0.2)) * 2.0 - 1.0).coerceIn(-1.0, 1.0)
            val coercedRadius = if (coerceTopAndBottom.value) {
                baseRadius - (baseRadius * 0.05 * kotlin.math.abs(verticalT))
            } else {
                baseRadius
            }
            val lateral = sprite.offsetXOrZ + drift
            val x = centerX + normalX * coercedRadius + tangentX * lateral
            val z = centerZ + normalZ * coercedRadius + tangentZ * lateral
            val coerceTilt = if (coerceTopAndBottom.value) (-verticalT * 6.0).toFloat() else 0f

            drawTexturedQuad(bufferSource, matrices, sprite, age.toFloat(), faceAngle, coerceTilt, x, currentY, z)
        }

        bufferSource.endBatch()
        matrices.popPose()
    }

    private fun computeFallDistance(age: Double): Double {
        val baseFall = age * fallSpeed.value
        val acceleration = age * age * 0.0012
        val flutter = (1.0 - cos(age * 0.24)) * 0.03
        return baseFall + acceleration + flutter
    }

    private fun computeFaceRadius(halfWidth: Double): Double = halfWidth

    private fun computeFaceHalfSpan(halfWidth: Double, faces: Int): Double {
        return (computeFaceRadius(halfWidth) * tan(PI / faces.toDouble())).coerceAtLeast(0.05)
    }

    private fun drawTexturedQuad(
        bufferSource: MultiBufferSource.BufferSource,
        matrices: com.mojang.blaze3d.vertex.PoseStack,
        sprite: FallingSprite,
        age: Float,
        faceAngle: Double,
        coerceTilt: Float,
        x: Double,
        y: Double,
        z: Double
    ) {
        val renderType = RenderTypes.entityTranslucent(sprite.textureId)
        val consumer = bufferSource.getBuffer(renderType)
        val size = 0.125f
        val halfSize = size / 2f

        matrices.pushPose()
        matrices.translate(x, y + halfSize, z)
        matrices.mulPose(Axis.YP.rotationDegrees((180.0 - Math.toDegrees(faceAngle)).toFloat()))
        if (coerceTilt != 0f) {
            matrices.mulPose(Axis.XP.rotationDegrees(coerceTilt))
        }

        matrices.mulPose(Axis.YP.rotationDegrees(sprite.baseYaw + age * sprite.yawSpeed))
        matrices.mulPose(Axis.XP.rotationDegrees(sprite.basePitch + age * sprite.pitchSpeed))
        matrices.mulPose(Axis.ZP.rotationDegrees(sprite.baseRoll + age * sprite.rollSpeed))

        val matrix = matrices.last().pose()
        consumer.addVertex(matrix, -halfSize, -halfSize, 0f).setColor(1f, 1f, 1f, 1f).setUv(0f, 0f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(fullBright).setNormal(0f, 0f, 1f)
        consumer.addVertex(matrix, halfSize, -halfSize, 0f).setColor(1f, 1f, 1f, 1f).setUv(1f, 0f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(fullBright).setNormal(0f, 0f, 1f)
        consumer.addVertex(matrix, halfSize, halfSize, 0f).setColor(1f, 1f, 1f, 1f).setUv(1f, 1f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(fullBright).setNormal(0f, 0f, 1f)
        consumer.addVertex(matrix, -halfSize, halfSize, 0f).setColor(1f, 1f, 1f, 1f).setUv(0f, 1f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(fullBright).setNormal(0f, 0f, 1f)
        matrices.popPose()
    }
}
