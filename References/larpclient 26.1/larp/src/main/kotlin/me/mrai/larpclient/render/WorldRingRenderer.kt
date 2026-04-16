package me.mrai.larpclient.render

import com.mojang.blaze3d.buffers.GpuBuffer
import com.mojang.blaze3d.pipeline.RenderPipeline
import com.mojang.blaze3d.systems.RenderPass
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.BufferBuilder
import com.mojang.blaze3d.vertex.ByteBufferBuilder
import com.mojang.blaze3d.vertex.MeshData
import com.mojang.blaze3d.vertex.VertexFormat
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.MappableRingBuffer
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.resources.Identifier
import org.joml.Matrix4f
import org.joml.Matrix4fc
import org.joml.Vector3f
import org.joml.Vector4f
import org.lwjgl.system.MemoryUtil
import java.util.OptionalDouble
import java.util.OptionalInt
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

object WorldRingRenderer {
    data class RingSpec(
        val centerX: Double,
        val centerY: Double,
        val centerZ: Double,
        val radius: Double,
        val thickness: Double,
        val height: Double,
        val red: Float,
        val green: Float,
        val blue: Float,
        val fillAlpha: Float,
        val outlineAlpha: Float
    )

    private val pipeline: RenderPipeline = RenderPipelines.register(
        RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath("larpclient", "pipeline/world_rings"))
            .build()
    )

    private val allocator = ByteBufferBuilder(262144)
    private var buffer: BufferBuilder? = null
    private var vertexBuffer: MappableRingBuffer? = null
    private val colorModulator = Vector4f(1f, 1f, 1f, 1f)
    private val modelOffset = Vector3f()
    private val textureMatrix = Matrix4f()
    private const val SEGMENT_SPACING = 0.20
    private const val MAX_VISUAL_THICKNESS = 0.065
    private const val MIN_OUTLINE_BAND = 0.008

    fun render(context: LevelRenderContext, rings: Collection<RingSpec>, depthless: Boolean = true) {
        if (rings.isEmpty()) return

        val client = Minecraft.getInstance()
        val matrices = context.poseStack()
        val cameraPos = client.gameRenderer.mainCamera.position()

        matrices.pushPose()
        matrices.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z)
        val builder = ensureBuffer()

        for (ring in rings) {
            renderRing(matrices.last().pose(), builder, ring)
        }

        matrices.popPose()
        drawBuiltBuffer(client, depthless)
    }

    private fun ensureBuffer(): BufferBuilder {
        val existing = buffer
        if (existing != null) return existing

        val created = BufferBuilder(allocator, pipeline.vertexFormatMode, pipeline.vertexFormat)
        buffer = created
        return created
    }

    private fun renderRing(positionMatrix: Matrix4fc, buffer: BufferBuilder, ring: RingSpec) {
        val outerRadius = ring.radius.toFloat().coerceAtLeast(0.1f)
        val visualThickness = min(
            ring.thickness.coerceAtLeast(0.01),
            min(ring.radius * 0.55, MAX_VISUAL_THICKNESS)
        ).toFloat()
        val innerRadius = (ring.radius - visualThickness).coerceAtLeast(0.02).toFloat()
        val outlineBand = min(
            (visualThickness * 0.35f).coerceAtLeast(MIN_OUTLINE_BAND.toFloat()),
            visualThickness / 2f
        )
        val segments = max(36, ceil((ring.radius * 2.0 * PI) / SEGMENT_SPACING).toInt())
        val y = ring.centerY.toFloat()
        val centerX = ring.centerX.toFloat()
        val centerZ = ring.centerZ.toFloat()

        if (ring.fillAlpha > 0f) {
            renderAnnulus(
                positionMatrix = positionMatrix,
                buffer = buffer,
                centerX = centerX,
                y = y,
                centerZ = centerZ,
                innerRadius = innerRadius,
                outerRadius = outerRadius,
                segments = segments,
                red = ring.red,
                green = ring.green,
                blue = ring.blue,
                alpha = ring.fillAlpha
            )
        }

        if (ring.outlineAlpha > 0f && outlineBand > 0f) {
            renderAnnulus(
                positionMatrix = positionMatrix,
                buffer = buffer,
                centerX = centerX,
                y = y,
                centerZ = centerZ,
                innerRadius = (outerRadius - outlineBand).coerceAtLeast(innerRadius),
                outerRadius = outerRadius,
                segments = segments,
                red = ring.red,
                green = ring.green,
                blue = ring.blue,
                alpha = ring.outlineAlpha
            )
            renderAnnulus(
                positionMatrix = positionMatrix,
                buffer = buffer,
                centerX = centerX,
                y = y,
                centerZ = centerZ,
                innerRadius = innerRadius,
                outerRadius = (innerRadius + outlineBand).coerceAtMost(outerRadius),
                segments = segments,
                red = ring.red,
                green = ring.green,
                blue = ring.blue,
                alpha = ring.outlineAlpha
            )
        }
    }

    private fun renderAnnulus(
        positionMatrix: Matrix4fc,
        buffer: BufferBuilder,
        centerX: Float,
        y: Float,
        centerZ: Float,
        innerRadius: Float,
        outerRadius: Float,
        segments: Int,
        red: Float,
        green: Float,
        blue: Float,
        alpha: Float
    ) {
        if (outerRadius - innerRadius <= 0.0005f) return

        for (i in 0 until segments) {
            val angle0 = (i.toDouble() / segments.toDouble()) * (PI * 2.0)
            val angle1 = ((i + 1).toDouble() / segments.toDouble()) * (PI * 2.0)

            val outer0X = centerX + (cos(angle0) * outerRadius).toFloat()
            val outer0Z = centerZ + (sin(angle0) * outerRadius).toFloat()
            val outer1X = centerX + (cos(angle1) * outerRadius).toFloat()
            val outer1Z = centerZ + (sin(angle1) * outerRadius).toFloat()
            val inner0X = centerX + (cos(angle0) * innerRadius).toFloat()
            val inner0Z = centerZ + (sin(angle0) * innerRadius).toFloat()
            val inner1X = centerX + (cos(angle1) * innerRadius).toFloat()
            val inner1Z = centerZ + (sin(angle1) * innerRadius).toFloat()

            renderQuad(
                positionMatrix,
                buffer,
                inner0X,
                y,
                inner0Z,
                outer0X,
                y,
                outer0Z,
                outer1X,
                y,
                outer1Z,
                inner1X,
                y,
                inner1Z,
                red,
                green,
                blue,
                alpha
            )
            renderQuad(
                positionMatrix,
                buffer,
                inner1X,
                y,
                inner1Z,
                outer1X,
                y,
                outer1Z,
                outer0X,
                y,
                outer0Z,
                inner0X,
                y,
                inner0Z,
                red,
                green,
                blue,
                alpha
            )
        }
    }

    private fun renderQuad(
        positionMatrix: Matrix4fc,
        buffer: BufferBuilder,
        x0: Float,
        y0: Float,
        z0: Float,
        x1: Float,
        y1: Float,
        z1: Float,
        x2: Float,
        y2: Float,
        z2: Float,
        x3: Float,
        y3: Float,
        z3: Float,
        red: Float,
        green: Float,
        blue: Float,
        alpha: Float
    ) {
        buffer.addVertex(positionMatrix, x0, y0, z0).setColor(red, green, blue, alpha)
        buffer.addVertex(positionMatrix, x1, y1, z1).setColor(red, green, blue, alpha)
        buffer.addVertex(positionMatrix, x2, y2, z2).setColor(red, green, blue, alpha)
        buffer.addVertex(positionMatrix, x3, y3, z3).setColor(red, green, blue, alpha)
    }

    private fun drawBuiltBuffer(client: Minecraft, depthless: Boolean) {
        val built = buffer?.buildOrThrow() ?: return
        val drawState = built.drawState()
        val format = drawState.format()
        val vertices = uploadVertexBuffer(drawState, format, built)
        drawPass(client, built, drawState, vertices, depthless)
        vertexBuffer?.rotate()
        buffer = null
    }

    private fun uploadVertexBuffer(drawState: MeshData.DrawState, format: VertexFormat, mesh: MeshData): GpuBuffer {
        val data = mesh.vertexBuffer()
        val byteSize = drawState.vertexCount() * format.vertexSize
        val ring = ensureVertexBuffer(byteSize)
        val commandEncoder = RenderSystem.getDevice().createCommandEncoder()
        commandEncoder.mapBuffer(ring.currentBuffer().slice(0, data.remaining().toLong()), false, true).use { mappedView ->
            val mappedData = mappedView.data()
            MemoryUtil.memCopy(
                MemoryUtil.memAddress(data),
                MemoryUtil.memAddress(mappedData),
                data.remaining().toLong()
            )
        }
        return ring.currentBuffer()
    }

    private fun ensureVertexBuffer(requiredBytes: Int): MappableRingBuffer {
        val existing = vertexBuffer
        if (existing != null && existing.size() >= requiredBytes) {
            return existing
        }

        existing?.close()
        val created = MappableRingBuffer(
            { "larpclient world ring renderer" },
            GpuBuffer.USAGE_VERTEX or GpuBuffer.USAGE_MAP_WRITE,
            max(requiredBytes, 262144)
        )
        vertexBuffer = created
        return created
    }

    private fun drawPass(
        client: Minecraft,
        built: MeshData,
        drawState: MeshData.DrawState,
        vertices: GpuBuffer,
        depthless: Boolean
    ) {
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
        val encoder = RenderSystem.getDevice().createCommandEncoder()
        val renderPass = if (depthless) {
            encoder.createRenderPass(
                { "larpclient world ring render pass depthless" },
                colorView,
                OptionalInt.empty()
            )
        } else {
            val depthView = requireNotNull(client.mainRenderTarget.depthTextureView)
            encoder.createRenderPass(
                { "larpclient world ring render pass" },
                colorView,
                OptionalInt.empty(),
                depthView,
                OptionalDouble.empty()
            )
        }

        renderPass.use { renderPass: RenderPass ->
                renderPass.setPipeline(pipeline)
                RenderSystem.bindDefaultUniforms(renderPass)
                renderPass.setUniform("DynamicTransforms", dynamicTransforms)
                renderPass.setVertexBuffer(0, vertices)
                renderPass.setIndexBuffer(indices, indexType)
                renderPass.drawIndexed(0, 0, drawState.indexCount(), 1)
            }

        built.close()
    }
}
