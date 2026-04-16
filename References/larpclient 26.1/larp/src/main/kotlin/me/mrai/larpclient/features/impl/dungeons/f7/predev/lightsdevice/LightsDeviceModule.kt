package me.mrai.larpclient.features.impl.dungeons.f7.predev.lightsdevice

import com.mojang.blaze3d.buffers.GpuBuffer
import com.mojang.blaze3d.pipeline.RenderPipeline
import com.mojang.blaze3d.systems.CommandEncoder
import com.mojang.blaze3d.systems.RenderPass
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.BufferBuilder
import com.mojang.blaze3d.vertex.ByteBufferBuilder
import com.mojang.blaze3d.vertex.MeshData
import com.mojang.blaze3d.vertex.VertexFormat
import me.mrai.larpclient.integration.AddonAutomationAccess
import me.mrai.larpclient.module.BoolSetting
import me.mrai.larpclient.module.Module
import me.mrai.larpclient.module.ModuleCategory
import me.mrai.larpclient.module.shownWhen
import me.mrai.larpclient.render.WorldBoxRenderer
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.player.LocalPlayer
import net.minecraft.client.renderer.MappableRingBuffer
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.client.renderer.rendertype.RenderType
import net.minecraft.core.BlockPos
import net.minecraft.resources.Identifier
import net.minecraft.world.level.block.LeverBlock
import net.minecraft.world.level.block.RedstoneLampBlock
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.AABB
import org.joml.Matrix4f
import org.joml.Matrix4fc
import org.joml.Vector3f
import org.joml.Vector4f
import org.lwjgl.system.MemoryUtil
import java.nio.ByteBuffer
import java.util.ArrayDeque
import java.util.OptionalDouble
import java.util.OptionalInt

object LightsDeviceModule : Module(
    name = "Lights Device",
    description = "Shows the Lights Device solver and can auto-complete it when addon automation is available.",
    category = ModuleCategory.DUNGEONS_F7_PREDEV
) {
    private const val INTERACT_COOLDOWN_TICKS = 2
    private const val FLICK_INTERVAL_TICKS = 20

    private val showSolverSetting = BoolSetting("Show Solver", true)
    private val autoCompleteLightsSetting = BoolSetting("Auto Complete Lights", false)
        .shownWhen { showAutomationSettings }
    private val showAutomationSettings: Boolean
        get() = AddonAutomationAccess.hasAddonFeatures()
    private val automationAllowed: Boolean
        get() = AddonAutomationAccess.isAutomationEnabled()

    private data class LeverAction(
        val pos: BlockPos,
        val targetPowered: Boolean
    )

    private val interactionQueue = ArrayDeque<LeverAction>()
    private var interactCooldown = 0
    private var flickCooldown = 0
    private var wasInsideRing = false

    private val ringMinX = 60.0
    private val ringMinY = 132.0
    private val ringMinZ = 140.0
    private val ringMaxX = 62.0
    private val ringMaxY = 134.0
    private val ringMaxZ = 142.0

    private val usefulIndices = listOf(1, 5, 8, 13, 16, 20)

    private val leverPositionsByIndex: Map<Int, BlockPos> = buildMap {
        var index = 1
        for (y in 136 downTo 133) {
            for (x in 58..62) {
                put(index, BlockPos(x, y, 142))
                index++
            }
        }
    }

    private val solutionLeverPositions: Set<BlockPos> by lazy {
        usefulIndices.mapTo(linkedSetOf()) { index -> leverPositionsByIndex.getValue(index) }
    }
    private val deviceLeverPositions: Set<BlockPos> by lazy { leverPositionsByIndex.values.toSet() }
    private val flickLeverPos: BlockPos by lazy { leverPositionsByIndex.getValue(18) }

    private val lampPositionsByIndex: Map<Int, BlockPos> = buildMap {
        var index = 1
        for (y in 136 downTo 133) {
            for (x in 58..62) {
                put(index, BlockPos(x, y, 143))
                index++
            }
        }
    }

    private val hiddenLeverPositions: Set<BlockPos> = leverPositionsByIndex
        .filterKeys { it !in usefulIndices }
        .values
        .toSet()

    private val ringPipeline: RenderPipeline = RenderPipelines.register(
        RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath("larpclient", "pipeline/lights_device_ring"))
            .build()
    )

    private val allocator = ByteBufferBuilder(RenderType.SMALL_BUFFER_SIZE)
    private var buffer: BufferBuilder? = null
    private var vertexBuffer: MappableRingBuffer? = null

    private val colorModulator = Vector4f(1f, 1f, 1f, 1f)
    private val modelOffset = Vector3f()
    private val textureMatrix = Matrix4f()
    private val solverLeverBoxStyle = WorldBoxRenderer.BoxStyle(
        fillRed = 0.16f,
        fillGreen = 0.94f,
        fillBlue = 0.32f,
        fillAlpha = 0.18f,
        outlineRed = 0.28f,
        outlineGreen = 1.0f,
        outlineBlue = 0.42f,
        outlineAlpha = 1.0f,
        outlineThickness = 0.035f
    )

    init {
        settings += showSolverSetting
        settings += autoCompleteLightsSetting
    }

    override fun onEnable() {
        resetState()
    }

    override fun onDisable() {
        resetState()
    }

    override fun onTick() {
        val client = Minecraft.getInstance()
        val player = client.player
        val level = client.level
        val gameMode = client.gameMode

        if (interactCooldown > 0) {
            interactCooldown--
        }
        if (flickCooldown > 0) {
            flickCooldown--
        }

        if (player == null || level == null || gameMode == null) {
            resetState()
            return
        }

        if (client.screen != null) {
            wasInsideRing = false
            interactionQueue.clear()
            return
        }

        val insideRing = isInsideInteractionRing(player)
        if (!insideRing) {
            wasInsideRing = false
            interactionQueue.clear()
            return
        }

        if (!wasInsideRing) {
            wasInsideRing = true
            interactionQueue.clear()
            flickCooldown = 0
        }

        if (interactionQueue.isEmpty()) {
            queueStateActions(level)
        }
        if (interactCooldown > 0 || interactionQueue.isEmpty()) return

        val action = interactionQueue.removeFirst()
        val state = level.getBlockState(action.pos)
        if (state.block !is LeverBlock) {
            return
        }
        if (isLeverPowered(state) == action.targetPowered) return
        if (!LeverInteractions.isWithinInteractionRange(player, action.pos)) return

        if (LeverInteractions.tryToggleLever(client, player, action.pos)) {
            interactCooldown = INTERACT_COOLDOWN_TICKS
        }
    }

    fun render(context: LevelRenderContext) {
        if (!enabled || !showSolverSetting.value) return

        val client = Minecraft.getInstance()
        val player = client.player ?: return
        val level = client.level ?: return
        if (!isDevicePresent(level)) return
        if (player.distanceToSqr(61.0, 133.0, 141.0) > 18.0 * 18.0) return

        val matrices = context.poseStack()
        val cameraPos = client.gameRenderer.mainCamera.position()

        matrices.pushPose()
        matrices.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z)

        val builder = ensureBuffer()
        val fillAlpha = if (isInsideInteractionRing(player)) 0.26f else 0.13f
        renderFilledBox(
            matrices.last().pose(),
            builder,
            ringMinX.toFloat() + 0.02f,
            ringMinY.toFloat() + 0.02f,
            ringMinZ.toFloat() + 0.02f,
            ringMaxX.toFloat() - 0.02f,
            ringMinY.toFloat() + 0.08f,
            ringMaxZ.toFloat() - 0.02f,
            0.22f,
            0.86f,
            0.95f,
            fillAlpha
        )

        matrices.popPose()
        drawBuiltBuffer(client)

        val solverTargets = solverTargets(level)
        if (solverTargets.isNotEmpty()) {
            WorldBoxRenderer.render(
                context,
                solverTargets.map { pos -> AABB(pos).inflate(0.01) to solverLeverBoxStyle },
                depthless = true
            )
        }
    }

    fun shouldHideLever(state: BlockState, pos: BlockPos): Boolean {
        if (!enabled || !showSolverSetting.value) return false
        if (state.block !is LeverBlock) return false
        return pos in hiddenLeverPositions
    }

    fun isManagedLever(pos: BlockPos): Boolean {
        return pos in deviceLeverPositions
    }

    fun closeRenderer() {
        allocator.close()
        vertexBuffer?.close()
        vertexBuffer = null
        buffer = null
    }

    private fun resetState() {
        interactCooldown = 0
        flickCooldown = 0
        wasInsideRing = false
        interactionQueue.clear()
    }

    private fun isInsideInteractionRing(player: LocalPlayer): Boolean {
        val position = player.position()
        return position.x >= ringMinX && position.x < ringMaxX &&
            position.y >= ringMinY && position.y < ringMaxY &&
            position.z >= ringMinZ && position.z < ringMaxZ
    }

    private fun queueStateActions(level: ClientLevel) {
        if (!isDevicePresent(level)) return
        if (!automationAllowed || !autoCompleteLightsSetting.value) return
        solverActions(level).forEach { interactionQueue += it }
        if (interactionQueue.any { it.pos == flickLeverPos }) {
            flickCooldown = FLICK_INTERVAL_TICKS
        }
    }

    private fun solverActions(level: ClientLevel): List<LeverAction> {
        val poweredExtras = deviceLeverPositions
            .asSequence()
            .filter { it !in solutionLeverPositions }
            .filter { isLeverPowered(level.getBlockState(it)) }
            .sortedBy { it.y }
            .map { LeverAction(it, false) }
            .toList()
        val missingSolution = solutionLeverPositions
            .asSequence()
            .filter { !isLeverPowered(level.getBlockState(it)) }
            .sortedByDescending { it.y }
            .map { LeverAction(it, true) }
            .toList()

        if (poweredExtras.isNotEmpty() || missingSolution.isNotEmpty()) {
            return poweredExtras + missingSolution
        }

        if (flickCooldown > 0) return emptyList()
        if (solutionLeverPositions.any { !isLeverPowered(level.getBlockState(it)) }) return emptyList()

        val currentPowered = isLeverPowered(level.getBlockState(flickLeverPos))
        return listOf(LeverAction(flickLeverPos, !currentPowered))
    }

    private fun solverTargets(level: ClientLevel): List<BlockPos> {
        return solverActions(level).map(LeverAction::pos)
    }

    private fun isDevicePresent(level: ClientLevel): Boolean {
        return lampPositionsByIndex.values.all { level.getBlockState(it).block is RedstoneLampBlock } &&
            leverPositionsByIndex.values.all {
                val state = level.getBlockState(it)
                state.block is LeverBlock
            }
    }

    private fun isLeverPowered(state: BlockState): Boolean {
        if (state.block !is LeverBlock) return false
        return state.getValue(LeverBlock.POWERED)
    }

    private fun ensureBuffer(): BufferBuilder {
        val existing = buffer
        if (existing != null) return existing

        val created = BufferBuilder(
            allocator,
            ringPipeline.vertexFormatMode,
            ringPipeline.vertexFormat
        )
        buffer = created
        return created
    }

    private fun renderFilledBox(
        positionMatrix: Matrix4fc,
        buffer: BufferBuilder,
        minX: Float,
        minY: Float,
        minZ: Float,
        maxX: Float,
        maxY: Float,
        maxZ: Float,
        red: Float,
        green: Float,
        blue: Float,
        alpha: Float
    ) {
        buffer.addVertex(positionMatrix, minX, minY, maxZ).setColor(red, green, blue, alpha)
        buffer.addVertex(positionMatrix, maxX, minY, maxZ).setColor(red, green, blue, alpha)
        buffer.addVertex(positionMatrix, maxX, maxY, maxZ).setColor(red, green, blue, alpha)
        buffer.addVertex(positionMatrix, minX, maxY, maxZ).setColor(red, green, blue, alpha)

        buffer.addVertex(positionMatrix, maxX, minY, minZ).setColor(red, green, blue, alpha)
        buffer.addVertex(positionMatrix, minX, minY, minZ).setColor(red, green, blue, alpha)
        buffer.addVertex(positionMatrix, minX, maxY, minZ).setColor(red, green, blue, alpha)
        buffer.addVertex(positionMatrix, maxX, maxY, minZ).setColor(red, green, blue, alpha)

        buffer.addVertex(positionMatrix, minX, minY, minZ).setColor(red, green, blue, alpha)
        buffer.addVertex(positionMatrix, minX, minY, maxZ).setColor(red, green, blue, alpha)
        buffer.addVertex(positionMatrix, minX, maxY, maxZ).setColor(red, green, blue, alpha)
        buffer.addVertex(positionMatrix, minX, maxY, minZ).setColor(red, green, blue, alpha)

        buffer.addVertex(positionMatrix, maxX, minY, maxZ).setColor(red, green, blue, alpha)
        buffer.addVertex(positionMatrix, maxX, minY, minZ).setColor(red, green, blue, alpha)
        buffer.addVertex(positionMatrix, maxX, maxY, minZ).setColor(red, green, blue, alpha)
        buffer.addVertex(positionMatrix, maxX, maxY, maxZ).setColor(red, green, blue, alpha)

        buffer.addVertex(positionMatrix, minX, maxY, maxZ).setColor(red, green, blue, alpha)
        buffer.addVertex(positionMatrix, maxX, maxY, maxZ).setColor(red, green, blue, alpha)
        buffer.addVertex(positionMatrix, maxX, maxY, minZ).setColor(red, green, blue, alpha)
        buffer.addVertex(positionMatrix, minX, maxY, minZ).setColor(red, green, blue, alpha)

        buffer.addVertex(positionMatrix, minX, minY, minZ).setColor(red, green, blue, alpha)
        buffer.addVertex(positionMatrix, maxX, minY, minZ).setColor(red, green, blue, alpha)
        buffer.addVertex(positionMatrix, maxX, minY, maxZ).setColor(red, green, blue, alpha)
        buffer.addVertex(positionMatrix, minX, minY, maxZ).setColor(red, green, blue, alpha)
    }

    private fun drawBuiltBuffer(client: Minecraft) {
        val built = buffer?.buildOrThrow() ?: return
        val drawState = built.drawState()
        val format = drawState.format()

        val vertices = upload(drawState, format, built)
        draw(client, built, drawState, vertices)

        vertexBuffer?.rotate()
        buffer = null
    }

    private fun upload(
        drawState: MeshData.DrawState,
        format: VertexFormat,
        built: MeshData
    ): GpuBuffer {
        val vertexBufferSizeInt = drawState.vertexCount() * format.vertexSize
        val vertexBufferSizeLong = vertexBufferSizeInt.toLong()
        val vertexData: ByteBuffer = built.vertexBuffer()

        if (vertexBuffer == null || vertexBuffer!!.size() < vertexBufferSizeLong) {
            vertexBuffer?.close()
            vertexBuffer = MappableRingBuffer(
                { "larpclient lights device ring" },
                GpuBuffer.USAGE_VERTEX or GpuBuffer.USAGE_MAP_WRITE,
                vertexBufferSizeInt
            )
        }

        val commandEncoder: CommandEncoder = RenderSystem.getDevice().createCommandEncoder()
        commandEncoder.mapBuffer(
            vertexBuffer!!.currentBuffer().slice(0, vertexData.remaining().toLong()),
            false,
            true
        ).use { mappedView ->
            val mappedData: ByteBuffer = requireNotNull(mappedView.data())
            MemoryUtil.memCopy(
                MemoryUtil.memAddress(vertexData),
                MemoryUtil.memAddress(mappedData),
                vertexData.remaining().toLong()
            )
        }

        return vertexBuffer!!.currentBuffer()
    }

    private fun draw(
        client: Minecraft,
        built: MeshData,
        drawState: MeshData.DrawState,
        vertices: GpuBuffer
    ) {
        val indices: GpuBuffer
        val indexType: VertexFormat.IndexType

        if (ringPipeline.vertexFormatMode == VertexFormat.Mode.QUADS) {
            built.sortQuads(allocator, RenderSystem.getProjectionType().vertexSorting())
            indices = ringPipeline.vertexFormat.uploadImmediateIndexBuffer(requireNotNull(built.indexBuffer()))
            indexType = built.drawState().indexType()
        } else {
            val shapeIndexBuffer = RenderSystem.getSequentialBuffer(ringPipeline.vertexFormatMode)
            indices = shapeIndexBuffer.getBuffer(drawState.indexCount())
            indexType = shapeIndexBuffer.type()
        }

        val dynamicTransforms = RenderSystem.getDynamicUniforms()
            .writeTransform(RenderSystem.getModelViewMatrix(), colorModulator, modelOffset, textureMatrix)

        val colorView = requireNotNull(client.mainRenderTarget.colorTextureView)
        val depthView = requireNotNull(client.mainRenderTarget.depthTextureView)

        RenderSystem.getDevice()
            .createCommandEncoder()
            .createRenderPass(
                { "larpclient lights device ring render pass" },
                colorView,
                OptionalInt.empty(),
                depthView,
                OptionalDouble.empty()
            ).use { renderPass: RenderPass ->
                renderPass.setPipeline(ringPipeline)
                RenderSystem.bindDefaultUniforms(renderPass)
                renderPass.setUniform("DynamicTransforms", dynamicTransforms)
                renderPass.setVertexBuffer(0, vertices)
                renderPass.setIndexBuffer(indices, indexType)
                renderPass.drawIndexed(0, 0, drawState.indexCount(), 1)
            }

        built.close()
    }
}
