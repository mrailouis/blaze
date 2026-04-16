package me.mrai.larpclient.features.impl.kuudra.p1.autoroutetopre

import com.mojang.blaze3d.buffers.GpuBuffer
import com.mojang.blaze3d.pipeline.RenderPipeline
import com.mojang.blaze3d.systems.CommandEncoder
import com.mojang.blaze3d.systems.RenderPass
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.BufferBuilder
import com.mojang.blaze3d.vertex.ByteBufferBuilder
import com.mojang.blaze3d.vertex.MeshData
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexFormat
import me.mrai.larpclient.features.impl.kuudra.p1.autoroutetopre.AutoRouteToPreModule
import me.mrai.larpclient.render.WorldRingRenderer
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.MappableRingBuffer
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.client.renderer.rendertype.RenderType
import net.minecraft.resources.Identifier
import org.joml.Matrix4f
import org.joml.Matrix4fc
import org.joml.Vector3f
import org.joml.Vector4f
import org.lwjgl.system.MemoryUtil
import java.util.OptionalDouble
import java.util.OptionalInt
object PreRouteRenderer {

    private val PIPELINE: RenderPipeline = RenderPipelines.register(
        RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath("larpclient", "pipeline/preroute_filled"))
            .build()
    )

    private val allocator = ByteBufferBuilder(RenderType.SMALL_BUFFER_SIZE)
    private var buffer: BufferBuilder? = null
    private var vertexBuffer: MappableRingBuffer? = null

    private val colorModulator = Vector4f(1f, 1f, 1f, 1f)
    private val modelOffset = Vector3f()
    private val textureMatrix = Matrix4f()

    fun render(context: LevelRenderContext) {
        val client = Minecraft.getInstance()
        client.player ?: return
        if (!AutoRouteToPreModule.enabled) return

        val matrices = context.poseStack()
        val cameraPos = client.gameRenderer.mainCamera.position()

        matrices.pushPose()
        matrices.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z)

        val rings = mutableListOf<WorldRingRenderer.RingSpec>()
        var wroteMarkers = false

        for (route in PreRouteManager.getAll()) {
            if (!AutoRouteToPreModule.shouldRender(route.name)) continue
            if (route.segments.isEmpty()) continue

            val color = colorFor(route.name)

            for (segment in route.segments) {
                rings += WorldRingRenderer.RingSpec(
                    centerX = segment.triggerBlock.x + 0.5,
                    centerY = segment.triggerBlock.y + 1.01,
                    centerZ = segment.triggerBlock.z + 0.5,
                    radius = 0.48,
                    thickness = 0.06,
                    height = 0.02,
                    red = color.r,
                    green = color.g,
                    blue = color.b,
                    fillAlpha = 0.24f,
                    outlineAlpha = 0.9f
                )
                val builder = ensureBuffer()
                wroteMarkers = true

                renderMarker(
                    matrices.last().pose(),
                    builder,
                    segment.hitX.toFloat(),
                    segment.hitY.toFloat(),
                    segment.hitZ.toFloat(),
                    color.r,
                    color.g,
                    color.b,
                    0.95f
                )
            }
        }

        matrices.popPose()

        if (rings.isNotEmpty()) {
            WorldRingRenderer.render(context, rings)
        }

        if (wroteMarkers) {
            drawBuiltBuffer(client)
        } else {
            buffer = null
        }
    }

    fun close() {
        allocator.close()
        vertexBuffer?.close()
        vertexBuffer = null
        buffer = null
    }

    private fun ensureBuffer(): BufferBuilder {
        val existing = buffer
        if (existing != null) return existing

        val created = BufferBuilder(
            allocator,
            PIPELINE.vertexFormatMode,
            PIPELINE.vertexFormat
        )
        buffer = created
        return created
    }

    private fun renderMarker(
        positionMatrix: Matrix4fc,
        buffer: BufferBuilder,
        x: Float,
        y: Float,
        z: Float,
        red: Float,
        green: Float,
        blue: Float,
        alpha: Float
    ) {
        val half = 0.06f
        renderFilledBox(
            positionMatrix,
            buffer,
            x - half,
            y - half,
            z - half,
            x + half,
            y + half,
            z + half,
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
        val vertexData: java.nio.ByteBuffer = built.vertexBuffer()!!

        if (vertexBuffer == null || vertexBuffer!!.size() < vertexBufferSizeLong) {
            vertexBuffer?.close()
            vertexBuffer = MappableRingBuffer(
                { "larpclient preroute renderer" },
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
            val mappedData: java.nio.ByteBuffer = mappedView.data()!!
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

        if (PIPELINE.vertexFormatMode == VertexFormat.Mode.QUADS) {
            built.sortQuads(allocator, RenderSystem.getProjectionType().vertexSorting())
            indices = PIPELINE.vertexFormat.uploadImmediateIndexBuffer(built.indexBuffer()!!)
            indexType = built.drawState().indexType()
        } else {
            val shapeIndexBuffer = RenderSystem.getSequentialBuffer(PIPELINE.vertexFormatMode)
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
                { "larpclient preroute render pass" },
                colorView,
                OptionalInt.empty(),
                depthView,
                OptionalDouble.empty()
            ).use { renderPass: RenderPass ->
                renderPass.setPipeline(PIPELINE)
                RenderSystem.bindDefaultUniforms(renderPass)
                renderPass.setUniform("DynamicTransforms", dynamicTransforms)
                renderPass.setVertexBuffer(0, vertices)
                renderPass.setIndexBuffer(indices, indexType)
                renderPass.drawIndexed(0, 0, drawState.indexCount(), 1)
            }

        built.close()
    }

    private fun colorFor(name: PreRouteName): RouteColor {
        return when (name) {
            PreRouteName.TRI -> RouteColor(0.20f, 1.00f, 0.35f)
            PreRouteName.X -> RouteColor(1.00f, 0.25f, 0.25f)
            PreRouteName.SLASH -> RouteColor(1.00f, 0.95f, 0.20f)
            PreRouteName.EQUALS -> RouteColor(0.85f, 0.35f, 1.00f)
        }
    }

    private data class RouteColor(
        val r: Float,
        val g: Float,
        val b: Float
    )
}
