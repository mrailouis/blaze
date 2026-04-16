package me.mrai.larpclient.features.impl.kuudra.p1.pearlprediction

import com.mojang.blaze3d.buffers.GpuBuffer
import com.mojang.blaze3d.pipeline.RenderPipeline
import com.mojang.blaze3d.systems.CommandEncoder
import com.mojang.blaze3d.systems.RenderPass
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.BufferBuilder
import com.mojang.blaze3d.vertex.ByteBufferBuilder
import com.mojang.blaze3d.vertex.MeshData
import com.mojang.blaze3d.vertex.VertexFormat
import me.mrai.larpclient.features.impl.kuudra.p1.kuudrawaypoints.KuudraWaypointModule
import me.mrai.larpclient.module.Module
import me.mrai.larpclient.module.ModuleCategory
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.renderer.MappableRingBuffer
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.client.renderer.rendertype.RenderType
import net.minecraft.resources.Identifier
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl
import net.minecraft.world.level.ClipContext
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import org.joml.Matrix4f
import org.joml.Matrix4fc
import org.joml.Vector3f
import org.joml.Vector4f
import org.lwjgl.system.MemoryUtil
import java.nio.ByteBuffer
import java.util.OptionalDouble
import java.util.OptionalInt
import kotlin.math.cos
import kotlin.math.sin

object PearlPredictionModule : Module(
    name = "Pearl Prediction",
    description = "Predicts pearl landing blocks and highlights pile proximity.",
    category = ModuleCategory.KUUDRA_P1
) {
    private const val PEARL_DRAG = 0.99
    private const val PEARL_GRAVITY = 0.03
    private const val MAX_SIM_TICKS = 120
    private const val POST_LAND_MS = 1500L
    private const val PILE_RADIUS = 2.5
    private const val LANDING_RING_RADIUS = 1.5
    private const val LANDING_RING_THICKNESS = 0.08
    private const val PILE_RING_RADIUS = 2.5
    private const val PILE_RING_THICKNESS = 0.08
    private const val RING_HEIGHT = 0.03
    private const val PILE_RING_Y = 79.0

    private data class TrackedPearl(val landingBlock: net.minecraft.core.BlockPos)
    private data class LandedHighlight(val landingBlock: net.minecraft.core.BlockPos, val expiresAt: Long)
    private data class ColorDef(val red: Float, val green: Float, val blue: Float, val alpha: Float)
    private data class RenderBlock(val min: Vec3, val max: Vec3, val fill: ColorDef, val outline: ColorDef)
    private val activePearls = hashMapOf<Int, TrackedPearl>()
    private val landedHighlights = arrayListOf<LandedHighlight>()

    private val pipeline: RenderPipeline = RenderPipelines.register(
        RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath("larpclient", "pipeline/pearl_prediction"))
            .build()
    )

    private val allocator = ByteBufferBuilder(RenderType.SMALL_BUFFER_SIZE)
    private var buffer: BufferBuilder? = null
    private var vertexBuffer: MappableRingBuffer? = null

    private val colorModulator = Vector4f(1f, 1f, 1f, 1f)
    private val modelOffset = Vector3f()
    private val textureMatrix = Matrix4f()

    override fun onDisable() {
        activePearls.clear()
        landedHighlights.clear()
    }

    override fun onTick() {
        if (!enabled) {
            activePearls.clear()
            landedHighlights.clear()
            return
        }

        val client = Minecraft.getInstance()
        val level = client.level ?: run {
            activePearls.clear()
            landedHighlights.clear()
            return
        }
        val player = client.player ?: run {
            activePearls.clear()
            landedHighlights.clear()
            return
        }

        val now = System.currentTimeMillis()
        landedHighlights.removeIf { it.expiresAt <= now }

        val currentIds = hashSetOf<Int>()
        for (entity in level.entitiesForRendering()) {
            val pearl = entity as? ThrownEnderpearl ?: continue
            if (pearl.owner?.uuid != player.uuid) continue

            currentIds += pearl.id
            predictLandingBlock(level, pearl)?.let { landing ->
                activePearls[pearl.id] = TrackedPearl(landing)
            }
        }

        val iterator = activePearls.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.key in currentIds) continue
            landedHighlights += LandedHighlight(entry.value.landingBlock, now + POST_LAND_MS)
            iterator.remove()
        }
    }

    fun render(context: LevelRenderContext) {
        if (!enabled) return

        val client = Minecraft.getInstance()
        val player = client.player ?: return

        val pileTargets = KuudraWaypointModule.getPileTargets()
        val pileCenters = pileTargets.map { (label, pos) ->
            label to Vec3(pos.x, pos.y, pos.z)
        }

        val blocks = arrayListOf<RenderBlock>()
        for ((_, pileCenter) in pileCenters) {
            val nearPile = horizontalDistance(player.position(), pileCenter) <= PILE_RADIUS
            if (nearPile) {
                blocks += pileHighlightBlock(pileCenter)
            }
        }

        val predictedBlocks = activePearls.values.map { it.landingBlock } + landedHighlights.map { it.landingBlock }
        for (blockPos in predictedBlocks.distinct()) {
            blocks += predictionBlock(blockPos)
        }

        if (blocks.isEmpty()) return

        val matrices = context.poseStack()
        val cameraPos = client.gameRenderer.mainCamera.position()
        matrices.pushPose()
        matrices.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z)

        val builder = ensureBuffer()
        val pose = matrices.last().pose()

        for (block in blocks) {
            renderFilledBox(pose, builder, block.min, block.max, block.fill)
            renderOutlineBox(pose, builder, block.min, block.max, block.outline, 0.03f)
        }

        matrices.popPose()
        drawBuiltBuffer(client)
    }

    private fun predictLandingBlock(level: ClientLevel, pearl: ThrownEnderpearl): net.minecraft.core.BlockPos? {
        var pos = pearl.position()
        var velocity = pearl.deltaMovement

        repeat(MAX_SIM_TICKS) {
            val nextPos = pos.add(velocity)
            val hit = level.clip(
                ClipContext(
                    pos,
                    nextPos,
                    ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE,
                    pearl
                )
            )

            if (hit.type != HitResult.Type.MISS) {
                return hit.blockPos
            }

            pos = nextPos
            velocity = velocity.scale(PEARL_DRAG).add(0.0, -PEARL_GRAVITY, 0.0)
        }

        return null
    }

    private fun predictionBlock(blockPos: net.minecraft.core.BlockPos): RenderBlock {
        val min = Vec3(blockPos.x.toDouble(), blockPos.y.toDouble(), blockPos.z.toDouble())
        val max = min.add(1.0, 1.0, 1.0)
        return RenderBlock(min, max, GREEN_FILL, GREEN_OUTLINE)
    }

    private fun pileHighlightBlock(center: Vec3): RenderBlock {
        val min = Vec3(center.x - 0.5, center.y - 0.5, center.z - 0.5)
        val max = Vec3(center.x + 0.5, center.y + 0.5, center.z + 0.5)
        return RenderBlock(min, max, GREEN_FILL, GREEN_OUTLINE)
    }

    private fun topCenter(blockPos: net.minecraft.core.BlockPos): Vec3 {
        return Vec3(blockPos.x + 0.5, blockPos.y + 1.0, blockPos.z + 0.5)
    }

    private fun horizontalDistance(a: Vec3, b: Vec3): Double {
        val dx = a.x - b.x
        val dz = a.z - b.z
        return kotlin.math.sqrt(dx * dx + dz * dz)
    }

    private fun addQuad(positionMatrix: Matrix4fc, buffer: BufferBuilder, a: Vec3, b: Vec3, c: Vec3, d: Vec3, color: ColorDef) {
        buffer.addVertex(positionMatrix, a.x.toFloat(), a.y.toFloat(), a.z.toFloat()).setColor(color.red, color.green, color.blue, color.alpha)
        buffer.addVertex(positionMatrix, b.x.toFloat(), b.y.toFloat(), b.z.toFloat()).setColor(color.red, color.green, color.blue, color.alpha)
        buffer.addVertex(positionMatrix, c.x.toFloat(), c.y.toFloat(), c.z.toFloat()).setColor(color.red, color.green, color.blue, color.alpha)
        buffer.addVertex(positionMatrix, d.x.toFloat(), d.y.toFloat(), d.z.toFloat()).setColor(color.red, color.green, color.blue, color.alpha)
    }

    private fun ensureBuffer(): BufferBuilder {
        return buffer ?: BufferBuilder(allocator, pipeline.vertexFormatMode, pipeline.vertexFormat).also { buffer = it }
    }

    private fun renderOutlineBox(positionMatrix: Matrix4fc, buffer: BufferBuilder, min: Vec3, max: Vec3, color: ColorDef, thickness: Float) {
        val t = thickness.toDouble()

        fun edge(x1: Double, y1: Double, z1: Double, x2: Double, y2: Double, z2: Double) {
            renderFilledBox(
                positionMatrix,
                buffer,
                Vec3(minOf(x1, x2) - t / 2.0, minOf(y1, y2) - t / 2.0, minOf(z1, z2) - t / 2.0),
                Vec3(maxOf(x1, x2) + t / 2.0, maxOf(y1, y2) + t / 2.0, maxOf(z1, z2) + t / 2.0),
                color
            )
        }

        edge(min.x, min.y, min.z, max.x, min.y, min.z)
        edge(min.x, min.y, max.z, max.x, min.y, max.z)
        edge(min.x, max.y, min.z, max.x, max.y, min.z)
        edge(min.x, max.y, max.z, max.x, max.y, max.z)
        edge(min.x, min.y, min.z, min.x, max.y, min.z)
        edge(max.x, min.y, min.z, max.x, max.y, min.z)
        edge(min.x, min.y, max.z, min.x, max.y, max.z)
        edge(max.x, min.y, max.z, max.x, max.y, max.z)
        edge(min.x, min.y, min.z, min.x, min.y, max.z)
        edge(max.x, min.y, min.z, max.x, min.y, max.z)
        edge(min.x, max.y, min.z, min.x, max.y, max.z)
        edge(max.x, max.y, min.z, max.x, max.y, max.z)
    }

    private fun renderFilledBox(positionMatrix: Matrix4fc, buffer: BufferBuilder, min: Vec3, max: Vec3, color: ColorDef) {
        val minX = min.x.toFloat()
        val minY = min.y.toFloat()
        val minZ = min.z.toFloat()
        val maxX = max.x.toFloat()
        val maxY = max.y.toFloat()
        val maxZ = max.z.toFloat()

        buffer.addVertex(positionMatrix, minX, minY, maxZ).setColor(color.red, color.green, color.blue, color.alpha)
        buffer.addVertex(positionMatrix, maxX, minY, maxZ).setColor(color.red, color.green, color.blue, color.alpha)
        buffer.addVertex(positionMatrix, maxX, maxY, maxZ).setColor(color.red, color.green, color.blue, color.alpha)
        buffer.addVertex(positionMatrix, minX, maxY, maxZ).setColor(color.red, color.green, color.blue, color.alpha)

        buffer.addVertex(positionMatrix, maxX, minY, minZ).setColor(color.red, color.green, color.blue, color.alpha)
        buffer.addVertex(positionMatrix, minX, minY, minZ).setColor(color.red, color.green, color.blue, color.alpha)
        buffer.addVertex(positionMatrix, minX, maxY, minZ).setColor(color.red, color.green, color.blue, color.alpha)
        buffer.addVertex(positionMatrix, maxX, maxY, minZ).setColor(color.red, color.green, color.blue, color.alpha)

        buffer.addVertex(positionMatrix, minX, minY, minZ).setColor(color.red, color.green, color.blue, color.alpha)
        buffer.addVertex(positionMatrix, minX, minY, maxZ).setColor(color.red, color.green, color.blue, color.alpha)
        buffer.addVertex(positionMatrix, minX, maxY, maxZ).setColor(color.red, color.green, color.blue, color.alpha)
        buffer.addVertex(positionMatrix, minX, maxY, minZ).setColor(color.red, color.green, color.blue, color.alpha)

        buffer.addVertex(positionMatrix, maxX, minY, maxZ).setColor(color.red, color.green, color.blue, color.alpha)
        buffer.addVertex(positionMatrix, maxX, minY, minZ).setColor(color.red, color.green, color.blue, color.alpha)
        buffer.addVertex(positionMatrix, maxX, maxY, minZ).setColor(color.red, color.green, color.blue, color.alpha)
        buffer.addVertex(positionMatrix, maxX, maxY, maxZ).setColor(color.red, color.green, color.blue, color.alpha)

        buffer.addVertex(positionMatrix, minX, maxY, maxZ).setColor(color.red, color.green, color.blue, color.alpha)
        buffer.addVertex(positionMatrix, maxX, maxY, maxZ).setColor(color.red, color.green, color.blue, color.alpha)
        buffer.addVertex(positionMatrix, maxX, maxY, minZ).setColor(color.red, color.green, color.blue, color.alpha)
        buffer.addVertex(positionMatrix, minX, maxY, minZ).setColor(color.red, color.green, color.blue, color.alpha)

        buffer.addVertex(positionMatrix, minX, minY, minZ).setColor(color.red, color.green, color.blue, color.alpha)
        buffer.addVertex(positionMatrix, maxX, minY, minZ).setColor(color.red, color.green, color.blue, color.alpha)
        buffer.addVertex(positionMatrix, maxX, minY, maxZ).setColor(color.red, color.green, color.blue, color.alpha)
        buffer.addVertex(positionMatrix, minX, minY, maxZ).setColor(color.red, color.green, color.blue, color.alpha)
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

    private fun upload(drawState: MeshData.DrawState, format: VertexFormat, built: MeshData): GpuBuffer {
        val vertexBufferSizeInt = drawState.vertexCount() * format.vertexSize
        val vertexBufferSizeLong = vertexBufferSizeInt.toLong()
        val vertexData: ByteBuffer = built.vertexBuffer()

        if (vertexBuffer == null || vertexBuffer!!.size() < vertexBufferSizeLong) {
            vertexBuffer?.close()
            vertexBuffer = MappableRingBuffer(
                { "larpclient pearl prediction renderer" },
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
            val mappedData: ByteBuffer = mappedView.data()
            MemoryUtil.memCopy(
                MemoryUtil.memAddress(vertexData),
                MemoryUtil.memAddress(mappedData),
                vertexData.remaining().toLong()
            )
        }

        return vertexBuffer!!.currentBuffer()
    }

    private fun draw(client: Minecraft, built: MeshData, drawState: MeshData.DrawState, vertices: GpuBuffer) {
        val indices: GpuBuffer
        val indexType: VertexFormat.IndexType

        if (pipeline.vertexFormatMode == VertexFormat.Mode.QUADS) {
            built.sortQuads(allocator, RenderSystem.getProjectionType().vertexSorting())
            indices = pipeline.vertexFormat.uploadImmediateIndexBuffer(built.indexBuffer()!!)
            indexType = built.drawState().indexType()
        } else {
            val shapeIndexBuffer = RenderSystem.getSequentialBuffer(pipeline.vertexFormatMode)
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
                { "larpclient pearl prediction render pass" },
                colorView,
                OptionalInt.empty(),
                depthView,
                OptionalDouble.empty()
            ).use { renderPass: RenderPass ->
                renderPass.setPipeline(pipeline)
                RenderSystem.bindDefaultUniforms(renderPass)
                renderPass.setUniform("DynamicTransforms", dynamicTransforms)
                renderPass.setVertexBuffer(0, vertices)
                renderPass.setIndexBuffer(indices, indexType)
                renderPass.drawIndexed(0, 0, drawState.indexCount(), 1)
            }

        built.close()
    }

    private val GREEN_FILL = ColorDef(0.03f, 0.72f, 0.18f, 0.24f)
    private val GREEN_OUTLINE = ColorDef(0.05f, 0.9f, 0.2f, 1.0f)
    private val GREEN_RING = ColorDef(0.05f, 0.88f, 0.2f, 0.72f)
    private val RED_RING = ColorDef(1.0f, 0.2f, 0.2f, 0.45f)
}
