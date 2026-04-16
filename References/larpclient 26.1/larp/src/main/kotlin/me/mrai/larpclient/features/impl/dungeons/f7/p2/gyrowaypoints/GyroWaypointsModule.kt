package me.mrai.larpclient.features.impl.dungeons.f7.p2.gyrowaypoints

import com.mojang.blaze3d.buffers.GpuBuffer
import com.mojang.blaze3d.pipeline.RenderPipeline
import com.mojang.blaze3d.systems.CommandEncoder
import com.mojang.blaze3d.systems.RenderPass
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.BufferBuilder
import com.mojang.blaze3d.vertex.ByteBufferBuilder
import com.mojang.blaze3d.vertex.MeshData
import com.mojang.blaze3d.vertex.VertexFormat
import me.mrai.larpclient.module.Module
import me.mrai.larpclient.module.ModuleCategory
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.MappableRingBuffer
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.client.renderer.rendertype.RenderType
import net.minecraft.core.BlockPos
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

object GyroWaypointsModule : Module(
    name = "Gyro Waypoints",
    description = "Renders purple gyro waypoints for Floor 7 P2.",
    category = ModuleCategory.DUNGEONS_F7_P2
) {
    private const val FILL_ALPHA = 0.22f
    private const val OUTLINE_ALPHA = 1.0f
    private const val OUTLINE_THICKNESS = 0.03

    private val waypointPositions = listOf(
        BlockPos(109, 169, 89),
        BlockPos(98, 168, 53),
        BlockPos(48, 168, 53),
        BlockPos(37, 169, 89),
        BlockPos(37, 169, 17)
    )

    private val pipeline: RenderPipeline = RenderPipelines.register(
        RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath("larpclient", "pipeline/gyro_waypoints_boxes"))
            .build()
    )

    private val allocator = ByteBufferBuilder(RenderType.SMALL_BUFFER_SIZE)
    private var buffer: BufferBuilder? = null
    private var vertexBuffer: MappableRingBuffer? = null

    private val colorModulator = Vector4f(1f, 1f, 1f, 1f)
    private val modelOffset = Vector3f()
    private val textureMatrix = Matrix4f()

    fun render(context: LevelRenderContext) {
        if (!enabled) return

        val mc = Minecraft.getInstance()
        val level = mc.level ?: return
        val cameraPos = mc.gameRenderer.mainCamera.position()
        val matrices = context.poseStack()

        matrices.pushPose()
        matrices.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z)

        var wroteAny = false
        val builder = ensureBuffer()
        val pose = matrices.last().pose()

        for (pos in waypointPositions) {
            if (!level.isLoaded(pos)) continue

            val box = AABB(
                pos.x.toDouble(),
                pos.y.toDouble() + 0.5,
                pos.z.toDouble(),
                pos.x.toDouble() + 1.0,
                pos.y.toDouble() + 1.5,
                pos.z.toDouble() + 1.0
            )

            renderFilledBox(pose, builder, box, 0.62f, 0.20f, 0.94f, FILL_ALPHA)
            renderOutlineBoxes(pose, builder, box, 0.78f, 0.42f, 1.00f, OUTLINE_ALPHA, OUTLINE_THICKNESS)
            wroteAny = true
        }

        matrices.popPose()

        if (wroteAny) {
            drawBuiltBuffer(mc)
        } else {
            buffer = null
        }
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

    private fun renderOutlineBoxes(
        positionMatrix: Matrix4fc,
        buffer: BufferBuilder,
        box: AABB,
        red: Float,
        green: Float,
        blue: Float,
        alpha: Float,
        thickness: Double
    ) {
        val minX = box.minX
        val minY = box.minY
        val minZ = box.minZ
        val maxX = box.maxX
        val maxY = box.maxY
        val maxZ = box.maxZ

        fun edge(minBx: Double, minBy: Double, minBz: Double, maxBx: Double, maxBy: Double, maxBz: Double) {
            renderFilledBox(
                positionMatrix,
                buffer,
                minBx.toFloat(),
                minBy.toFloat(),
                minBz.toFloat(),
                maxBx.toFloat(),
                maxBy.toFloat(),
                maxBz.toFloat(),
                red,
                green,
                blue,
                alpha
            )
        }

        edge(minX, minY, minZ, maxX, minY + thickness, minZ + thickness)
        edge(minX, minY, maxZ - thickness, maxX, minY + thickness, maxZ)
        edge(minX, minY, minZ + thickness, minX + thickness, minY + thickness, maxZ - thickness)
        edge(maxX - thickness, minY, minZ + thickness, maxX, minY + thickness, maxZ - thickness)

        edge(minX, maxY - thickness, minZ, maxX, maxY, minZ + thickness)
        edge(minX, maxY - thickness, maxZ - thickness, maxX, maxY, maxZ)
        edge(minX, maxY - thickness, minZ + thickness, minX + thickness, maxY, maxZ - thickness)
        edge(maxX - thickness, maxY - thickness, minZ + thickness, maxX, maxY, maxZ - thickness)

        edge(minX, minY + thickness, minZ, minX + thickness, maxY - thickness, minZ + thickness)
        edge(maxX - thickness, minY + thickness, minZ, maxX, maxY - thickness, minZ + thickness)
        edge(minX, minY + thickness, maxZ - thickness, minX + thickness, maxY - thickness, maxZ)
        edge(maxX - thickness, minY + thickness, maxZ - thickness, maxX, maxY - thickness, maxZ)
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
                { "larpclient gyro waypoints renderer" },
                GpuBuffer.USAGE_VERTEX or GpuBuffer.USAGE_MAP_WRITE,
                vertexBufferSizeInt
            )
        }

        val commandEncoder: CommandEncoder = RenderSystem.getDevice().createCommandEncoder()
        commandEncoder.mapBuffer(vertexBuffer!!.currentBuffer().slice(0, vertexData.remaining().toLong()), false, true).use { mappedView ->
            val mappedData: ByteBuffer = mappedView.data()
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

        if (pipeline.vertexFormatMode == VertexFormat.Mode.QUADS) {
            built.sortQuads(allocator, RenderSystem.getProjectionType().vertexSorting())
            indices = pipeline.vertexFormat.uploadImmediateIndexBuffer(
                built.indexBuffer() ?: throw IllegalStateException("Index buffer was null")
            )
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

        RenderSystem.getDevice().createCommandEncoder().createRenderPass(
            { "larpclient gyro waypoints render pass" },
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
    }
}
