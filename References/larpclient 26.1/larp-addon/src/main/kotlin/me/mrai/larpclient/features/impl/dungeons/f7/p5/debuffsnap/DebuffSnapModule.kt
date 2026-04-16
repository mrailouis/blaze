package me.mrai.larpclient.features.impl.dungeons.f7.p5.debuffsnap

import com.google.gson.GsonBuilder
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
import me.mrai.larpclient.render.WorldRingRenderer
import me.mrai.larpclient.util.LarpLog
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.MappableRingBuffer
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.resources.Identifier
import net.minecraft.world.phys.Vec3
import org.joml.Matrix4f
import org.joml.Matrix4fc
import org.joml.Vector3f
import org.joml.Vector4f
import org.lwjgl.system.MemoryUtil
import java.nio.file.Files
import java.nio.file.Path
import java.util.OptionalDouble
import java.util.OptionalInt
import java.util.UUID
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

object DebuffSnapModule : Module(
    name = "Debuff Snap",
    description = "Snaps your view straight up every time you enter a configured ring.",
    category = ModuleCategory.DUNGEONS_F7_P5
) {
    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val configPath: Path =
        FabricLoader.getInstance().configDir.resolve("larpclient/debuff_snap.json")

    data class SnapRing(
        var id: String = UUID.randomUUID().toString(),
        var x: Double,
        var y: Double,
        var z: Double,
        var radius: Double
    )

    private data class DebuffSnapConfig(
        var entries: MutableList<SnapRing> = mutableListOf()
    )

    private val pipeline: RenderPipeline = RenderPipelines.register(
        RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath("larpclient", "pipeline/debuff_snap"))
            .build()
    )

    private val allocator = ByteBufferBuilder(262144)
    private var buffer: BufferBuilder? = null
    private var vertexBuffer: MappableRingBuffer? = null
    private val colorModulator = Vector4f(1f, 1f, 1f, 1f)
    private val modelOffset = Vector3f()
    private val textureMatrix = Matrix4f()

    private var config = DebuffSnapConfig()
    private val insideRingIds = hashSetOf<String>()

    private const val RING_HEIGHT = 0.03f
    private const val RING_THICKNESS = 0.16f
    private const val RING_Y_OFFSET = 0.02f
    private const val MAX_VERTICAL_TRIGGER_DELTA = 2.5
    private const val SEGMENT_SPACING = 0.28
    private const val SNAP_PITCH = -90f

    init {
        load()
    }

    fun addAtPlayer(radius: Double): Boolean {
        val player = Minecraft.getInstance().player ?: return false
        config.entries += SnapRing(
            x = player.x,
            y = player.y,
            z = player.z,
            radius = radius.coerceAtLeast(0.1)
        )
        save()
        return true
    }

    fun onWorldChange() {
        insideRingIds.clear()
    }

    override fun onTick() {
        if (!enabled) return

        val player = Minecraft.getInstance().player ?: return
        val playerPos = player.position()
        val currentlyInside = hashSetOf<String>()

        for (entry in config.entries) {
            if (!isInside(entry, playerPos)) continue

            currentlyInside += entry.id
            if (entry.id !in insideRingIds) {
                snapUp(player)
            }
        }

        insideRingIds.clear()
        insideRingIds += currentlyInside
    }

    fun render(context: LevelRenderContext) {
        if (!enabled) return
        if (config.entries.isEmpty()) return

        val specs = config.entries.map { entry ->
            WorldRingRenderer.RingSpec(
                centerX = entry.x,
                centerY = entry.y + RING_Y_OFFSET,
                centerZ = entry.z,
                radius = entry.radius,
                thickness = RING_THICKNESS.toDouble(),
                height = RING_HEIGHT.toDouble(),
                red = 0.7f,
                green = 0.25f,
                blue = 1.0f,
                fillAlpha = 0.32f,
                outlineAlpha = 0.82f
            )
        }
        WorldRingRenderer.render(context, specs)
    }

    private fun snapUp(player: net.minecraft.client.player.LocalPlayer) {
        val yaw = player.yRot
        player.setXRot(SNAP_PITCH)
        player.xRotO = SNAP_PITCH
        player.setYRot(yaw)
        player.yRotO = yaw
        player.setYHeadRot(yaw)
        player.setYBodyRot(yaw)
        player.setOldPosAndRot(player.position(), yaw, SNAP_PITCH)
    }

    private fun isInside(entry: SnapRing, playerPos: Vec3): Boolean {
        if (abs(playerPos.y - entry.y) > MAX_VERTICAL_TRIGGER_DELTA) return false

        val dx = playerPos.x - entry.x
        val dz = playerPos.z - entry.z
        return dx * dx + dz * dz <= entry.radius * entry.radius
    }

    private fun renderRing(
        positionMatrix: Matrix4fc,
        buffer: BufferBuilder,
        centerX: Float,
        centerY: Float,
        centerZ: Float,
        radius: Float,
        red: Float,
        green: Float,
        blue: Float,
        alpha: Float
    ) {
        val safeRadius = radius.coerceAtLeast(0.1f)
        val circumference = (safeRadius * Math.PI * 2.0).toFloat()
        val segments = max(18, ceil(circumference / SEGMENT_SPACING).toInt())
        val halfThickness = (RING_THICKNESS / 2f).coerceAtMost(safeRadius * 0.6f)
        val y = centerY + RING_Y_OFFSET

        for (i in 0 until segments) {
            val angle = (i.toDouble() / segments.toDouble()) * (Math.PI * 2.0)
            val px = centerX + (cos(angle) * safeRadius).toFloat()
            val pz = centerZ + (sin(angle) * safeRadius).toFloat()

            renderFilledBox(
                positionMatrix,
                buffer,
                px - halfThickness,
                y,
                pz - halfThickness,
                px + halfThickness,
                y + RING_HEIGHT,
                pz + halfThickness,
                red,
                green,
                blue,
                alpha
            )
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

    private fun drawBuiltBuffer(client: Minecraft) {
        val built = buffer?.buildOrThrow() ?: return
        val drawState = built.drawState()
        val format = drawState.format()

        val vertices = uploadVertexBuffer(drawState, format, built)
        drawPass(client, built, drawState, vertices)

        vertexBuffer?.rotate()
        buffer = null
    }

    private fun uploadVertexBuffer(
        drawState: MeshData.DrawState,
        format: VertexFormat,
        mesh: MeshData
    ): GpuBuffer {
        val data = mesh.vertexBuffer()
        val byteSize = drawState.vertexCount() * format.vertexSize
        val ring = ensureVertexBuffer(byteSize)
        val commandEncoder = RenderSystem.getDevice().createCommandEncoder()
        commandEncoder.mapBuffer(
            ring.currentBuffer().slice(0, data.remaining().toLong()),
            false,
            true
        ).use { mappedView ->
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
            { "larpclient debuff snap renderer" },
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
        vertices: GpuBuffer
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
        val depthView = requireNotNull(client.mainRenderTarget.depthTextureView)

        RenderSystem.getDevice()
            .createCommandEncoder()
            .createRenderPass(
                { "larpclient debuff snap render pass" },
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

    private fun load() {
        try {
            Files.createDirectories(configPath.parent)
            config = if (Files.exists(configPath)) {
                gson.fromJson(Files.readString(configPath), DebuffSnapConfig::class.java) ?: DebuffSnapConfig()
            } else {
                DebuffSnapConfig(defaultEntries())
            }
            config.entries.forEach { if (it.id.isBlank()) it.id = UUID.randomUUID().toString() }
            save()
        } catch (throwable: Throwable) {
            LarpLog.warn("Failed to load Debuff Snap config from $configPath: ${throwable.message ?: throwable.javaClass.simpleName}")
            config = DebuffSnapConfig(defaultEntries())
        }
    }

    private fun save() {
        try {
            Files.createDirectories(configPath.parent)
            Files.writeString(configPath, gson.toJson(config))
        } catch (throwable: Throwable) {
            LarpLog.warn("Failed to save Debuff Snap config to $configPath: ${throwable.message ?: throwable.javaClass.simpleName}")
        }
    }

    private fun defaultEntries(): MutableList<SnapRing> {
        return mutableListOf(
            SnapRing("a706ebf0-e03d-4dc9-b744-a956746c72c7", 83.58200984859155, 6.0, 58.71704582239888, 2.0),
            SnapRing("9de95fdf-4ea2-4f97-9c14-b6d2d846c75f", 83.2681809385593, 6.0, 96.83408699645138, 2.0),
            SnapRing("68ce49ef-b059-4c70-88c4-ccc994007c36", 54.05174990107694, 8.0, 123.94675762491272, 2.0),
            SnapRing("ba16157f-a25b-4d80-85cd-78aae83410b6", 28.680304733895372, 6.0, 91.90955957304926, 2.0),
            SnapRing("22651e64-aed9-4779-832e-587a280c842a", 28.44367593235769, 6.0, 56.57978390825396, 2.0)
        )
    }
}
