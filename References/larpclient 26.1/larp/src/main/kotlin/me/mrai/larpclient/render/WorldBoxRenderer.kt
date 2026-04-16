package me.mrai.larpclient.render

import com.mojang.blaze3d.buffers.GpuBuffer
import com.mojang.blaze3d.pipeline.RenderPipeline
import com.mojang.blaze3d.systems.CommandEncoder
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
import net.minecraft.client.renderer.rendertype.RenderType
import net.minecraft.resources.Identifier
import net.minecraft.world.phys.AABB
import org.joml.Matrix4f
import org.joml.Matrix4fc
import org.joml.Vector3f
import org.joml.Vector4f
import org.lwjgl.system.MemoryUtil
import java.nio.ByteBuffer
import java.util.OptionalDouble
import java.util.OptionalInt

object WorldBoxRenderer {
    private val pipeline: RenderPipeline = RenderPipelines.register(
        RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath("larpclient", "pipeline/world_boxes"))
            .build()
    )

    private val allocator = ByteBufferBuilder(RenderType.SMALL_BUFFER_SIZE)
    private var buffer: BufferBuilder? = null
    private var vertexBuffer: MappableRingBuffer? = null

    private val colorModulator = Vector4f(1f, 1f, 1f, 1f)
    private val modelOffset = Vector3f()
    private val textureMatrix = Matrix4f()

    data class BoxStyle(
        val fillRed: Float,
        val fillGreen: Float,
        val fillBlue: Float,
        val fillAlpha: Float,
        val outlineRed: Float,
        val outlineGreen: Float,
        val outlineBlue: Float,
        val outlineAlpha: Float,
        val outlineThickness: Float = 0.03f
    )

    fun render(context: LevelRenderContext, boxes: List<Pair<AABB, BoxStyle>>, depthless: Boolean = false) {
        if (boxes.isEmpty()) return

        val client = Minecraft.getInstance()
        val matrices = context.poseStack()
        val cameraPos = client.gameRenderer.mainCamera.position()

        matrices.pushPose()
        matrices.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z)

        val builder = ensureBuffer()
        val pose = matrices.last().pose()

        for ((box, style) in boxes) {
            renderFilledBox(pose, builder, box, style.fillRed, style.fillGreen, style.fillBlue, style.fillAlpha)
            renderOutlineBox(
                pose,
                builder,
                box,
                style.outlineRed,
                style.outlineGreen,
                style.outlineBlue,
                style.outlineAlpha,
                style.outlineThickness
            )
        }

        matrices.popPose()
        drawBuiltBuffer(client, depthless)
    }

    private fun ensureBuffer(): BufferBuilder {
        val existing = buffer
        if (existing != null) return existing

        val created = BufferBuilder(
            allocator,
            pipeline.vertexFormatMode,
            pipeline.vertexFormat
        )
        buffer = created
        return created
    }

    private fun renderOutlineBox(
        positionMatrix: Matrix4fc,
        buffer: BufferBuilder,
        box: AABB,
        red: Float,
        green: Float,
        blue: Float,
        alpha: Float,
        thickness: Float
    ) {
        val t = thickness.coerceAtLeast(0.01f)
        val minX = box.minX.toFloat()
        val minY = box.minY.toFloat()
        val minZ = box.minZ.toFloat()
        val maxX = box.maxX.toFloat()
        val maxY = box.maxY.toFloat()
        val maxZ = box.maxZ.toFloat()

        fun edge(x1: Float, y1: Float, z1: Float, x2: Float, y2: Float, z2: Float) {
            renderFilledBox(
                positionMatrix,
                buffer,
                minOf(x1, x2) - t / 2f,
                minOf(y1, y2) - t / 2f,
                minOf(z1, z2) - t / 2f,
                maxOf(x1, x2) + t / 2f,
                maxOf(y1, y2) + t / 2f,
                maxOf(z1, z2) + t / 2f,
                red,
                green,
                blue,
                alpha
            )
        }

        edge(minX, minY, minZ, maxX, minY, minZ)
        edge(minX, minY, maxZ, maxX, minY, maxZ)
        edge(minX, maxY, minZ, maxX, maxY, minZ)
        edge(minX, maxY, maxZ, maxX, maxY, maxZ)

        edge(minX, minY, minZ, minX, maxY, minZ)
        edge(maxX, minY, minZ, maxX, maxY, minZ)
        edge(minX, minY, maxZ, minX, maxY, maxZ)
        edge(maxX, minY, maxZ, maxX, maxY, maxZ)

        edge(minX, minY, minZ, minX, minY, maxZ)
        edge(maxX, minY, minZ, maxX, minY, maxZ)
        edge(minX, maxY, minZ, minX, maxY, maxZ)
        edge(maxX, maxY, minZ, maxX, maxY, maxZ)
    }

    private fun renderFilledBox(
        positionMatrix: Matrix4fc,
        buffer: BufferBuilder,
        box: AABB,
        red: Float,
        green: Float,
        blue: Float,
        alpha: Float
    ) {
        renderFilledBox(
            positionMatrix,
            buffer,
            box.minX.toFloat(),
            box.minY.toFloat(),
            box.minZ.toFloat(),
            box.maxX.toFloat(),
            box.maxY.toFloat(),
            box.maxZ.toFloat(),
            red,
            green,
            blue,
            alpha
        )
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

    private fun drawBuiltBuffer(client: Minecraft, depthless: Boolean) {
        val built = buffer?.build() ?: return
        buffer = null

        val drawState = built.drawState()
        if (drawState.vertexCount() <= 0) {
            built.close()
            return
        }

        val vertices = uploadVertexBuffer(built.vertexBuffer())
        draw(client, built, drawState, vertices, depthless)
        vertexBuffer?.rotate()
    }

    private fun uploadVertexBuffer(vertexData: ByteBuffer): GpuBuffer {
        val vertexBufferSizeInt = vertexData.remaining().coerceAtLeast(1)
        val vertexBufferSizeLong = vertexBufferSizeInt.toLong()

        if (vertexBuffer == null || vertexBuffer!!.size() < vertexBufferSizeLong) {
            vertexBuffer?.close()
            vertexBuffer = MappableRingBuffer(
                { "larpclient world box renderer" },
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
            val mappedData = mappedView.data()
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
                { "larpclient world box render pass depthless" },
                colorView,
                OptionalInt.empty()
            )
        } else {
            val depthView = requireNotNull(client.mainRenderTarget.depthTextureView)
            encoder.createRenderPass(
                { "larpclient world box render pass" },
                colorView,
                OptionalInt.empty(),
                depthView,
                OptionalDouble.empty()
            )
        }

        renderPass.use { pass: RenderPass ->
                pass.setPipeline(pipeline)
                RenderSystem.bindDefaultUniforms(pass)
                pass.setUniform("DynamicTransforms", dynamicTransforms)
                pass.setVertexBuffer(0, vertices)
                pass.setIndexBuffer(indices, indexType)
                pass.drawIndexed(0, 0, drawState.indexCount(), 1)
            }

        built.close()
    }
}
