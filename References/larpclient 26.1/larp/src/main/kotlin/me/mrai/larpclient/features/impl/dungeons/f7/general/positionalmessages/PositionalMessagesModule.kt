package me.mrai.larpclient.features.impl.dungeons.f7.general.positionalmessages

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

object PositionalMessagesModule : Module(
    name = "Positional Messages",
    description = "Sends saved commands once when you walk into their configured rings.",
    category = ModuleCategory.DUNGEONS_F7_GENERAL
) {
    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val configPath: Path =
        FabricLoader.getInstance().configDir.resolve("larpclient/positional_messages.json")

    data class PositionalMessage(
        var id: String = UUID.randomUUID().toString(),
        var x: Double,
        var y: Double,
        var z: Double,
        var radius: Double,
        var command: String
    )

    private data class PositionalMessageConfig(
        var entries: MutableList<PositionalMessage> = mutableListOf()
    )

    private val pipeline: RenderPipeline = RenderPipelines.register(
        RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath("larpclient", "pipeline/positional_messages"))
            .build()
    )

    private val allocator = ByteBufferBuilder(262144)
    private var buffer: BufferBuilder? = null
    private var vertexBuffer: MappableRingBuffer? = null
    private val colorModulator = Vector4f(1f, 1f, 1f, 1f)
    private val modelOffset = Vector3f()
    private val textureMatrix = Matrix4f()

    private var config = PositionalMessageConfig()
    private val insideIds = hashSetOf<String>()

    private const val RING_HEIGHT = 0.03f
    private const val RING_THICKNESS = 0.16f
    private const val RING_Y_OFFSET = 0.02f
    private const val MAX_VERTICAL_TRIGGER_DELTA = 2.5
    private const val SEGMENT_SPACING = 0.28

    init {
        load()
    }

    fun getEntries(): List<PositionalMessage> = config.entries

    fun addAtPlayer(command: String, radius: Double): Boolean {
        val player = Minecraft.getInstance().player ?: return false
        val trimmedCommand = normalizeCommand(command)
        if (trimmedCommand.isEmpty()) return false

        config.entries += PositionalMessage(
            x = player.x,
            y = player.y,
            z = player.z,
            radius = radius.coerceAtLeast(0.1),
            command = trimmedCommand
        )
        save()
        return true
    }

    fun updateCommand(index: Int, command: String) {
        val entry = config.entries.getOrNull(index) ?: return
        entry.command = command
        save()
    }

    fun remove(index: Int) {
        val removed = config.entries.removeAt(index)
        insideIds -= removed.id
        save()
    }

    fun onWorldChange() {
        insideIds.clear()
    }

    override fun onTick() {
        if (!enabled) return

        val player = Minecraft.getInstance().player ?: return
        val playerPos = player.position()

        for (entry in config.entries) {
            val inside = isInside(entry, playerPos)
            if (!inside) {
                insideIds -= entry.id
                continue
            }
            if (!insideIds.add(entry.id)) continue

            val command = normalizeCommand(entry.command)
            if (command.isEmpty()) continue

            player.connection.sendCommand(command)
        }
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
                red = 0.95f,
                green = 0.25f,
                blue = 0.25f,
                fillAlpha = 0.28f,
                outlineAlpha = 0.78f
            )
        }
        WorldRingRenderer.render(context, specs)
    }

    private fun isInside(entry: PositionalMessage, playerPos: Vec3): Boolean {
        if (abs(playerPos.y - entry.y) > MAX_VERTICAL_TRIGGER_DELTA) return false

        val dx = playerPos.x - entry.x
        val dz = playerPos.z - entry.z
        return dx * dx + dz * dz <= entry.radius * entry.radius
    }

    private fun normalizeCommand(raw: String): String {
        return raw.trim().removePrefix("/").trim()
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
            { "larpclient positional messages renderer" },
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
                { "larpclient positional messages render pass" },
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
                gson.fromJson(Files.readString(configPath), PositionalMessageConfig::class.java) ?: PositionalMessageConfig()
            } else {
                PositionalMessageConfig(defaultEntries())
            }
            config.entries.forEach { if (it.id.isBlank()) it.id = UUID.randomUUID().toString() }
            save()
        } catch (throwable: Throwable) {
            LarpLog.warn("Failed to load positional message config from $configPath: ${throwable.message ?: throwable.javaClass.simpleName}")
            config = PositionalMessageConfig(defaultEntries())
        }
    }

    private fun save() {
        try {
            Files.createDirectories(configPath.parent)
            Files.writeString(configPath, gson.toJson(config))
        } catch (throwable: Throwable) {
            LarpLog.warn("Failed to save positional message config to $configPath: ${throwable.message ?: throwable.javaClass.simpleName}")
        }
    }

    private fun defaultEntries(): MutableList<PositionalMessage> {
        return mutableListOf(
            PositionalMessage("cd01d522-a022-40c7-b4e2-f2e4e37c8cf8", 73.65995193011817, 165.0, 38.64469812559758, 2.0, "pc At P2 Center!"),
            PositionalMessage("a49df99c-ea83-4699-8629-c457d32a0e56", 98.72970890318943, 169.0, 53.62001199354237, 2.0, "pc At P2 Leap!"),
            PositionalMessage("d84ec20f-f1c0-42d0-8b26-9883c722fde1", 99.27581268043112, 169.0, 65.60477686724775, 2.0, "pc At checkpoint!"),
            PositionalMessage("6843f3e1-5644-4e93-9e61-761892face39", 58.195983664154795, 169.0, 64.83547241980818, 2.0, "pc At Yellow Leap!"),
            PositionalMessage("275c7738-a42f-4a56-996e-eb6b14c4c885", 101.03101316786888, 115.0625, 46.222596965302145, 2.0, "pc At P3 Leap!"),
            PositionalMessage("23e45a2e-7e6f-4c71-a0d9-9df810e7f469", 63.42729424970457, 127.0, 35.82102663182081, 2.0, "pc At i4!"),
            PositionalMessage("59287d7c-69a7-4d01-bd37-0205afb0e9a5", 108.24313416251113, 120.0, 93.910755962814, 2.0, "pc At SS!"),
            PositionalMessage("32c86a39-1794-46e5-a8f9-fe31c0b7129c", 95.30000001192093, 121.0, 121.69999998807907, 1.0, "pc Waiting to EE2!"),
            PositionalMessage("40b2e970-eac5-48c6-9a6d-16f72133734b", 60.878903427443106, 132.0, 138.83689353410713, 2.0, "pc At HEE2!"),
            PositionalMessage("fe9324e9-a5b9-4f60-a244-f96225071a0b", 57.69779531664111, 109.0, 130.99823459902248, 2.0, "pc At Low EE2"),
            PositionalMessage("24afafdc-dd32-4991-b245-bf1c271a0c16", 69.3865815120948, 109.0, 120.30000001192093, 1.0, "pc At Low EE2 Safespot"),
            PositionalMessage("b75b900c-693c-4cee-8746-6db961fa97df", 19.30000001192093, 130.0, 137.69999998807907, 1.0, "pc Waiting to EE3!"),
            PositionalMessage("134d6605-1d4b-470a-98a5-60f8b48bf3cd", 19.30000001192093, 121.0, 137.69999998807907, 1.0, "pc Waiting to EE3!"),
            PositionalMessage("4e01d9b2-8ad1-47b5-8f98-fea041a5f50e", 19.30000001192093, 130.0, 127.30000001192093, 1.0, "pc Waiting to EE3!"),
            PositionalMessage("8bcb8d88-9c23-4c0f-9abd-cefb0286d762", 19.30000001192093, 121.0, 127.30000001192093, 1.0, "pc Waiting to EE3!"),
            PositionalMessage("87561db5-3ea4-4de8-ad88-f5f61890ae03", 2.1781541887642057, 109.0, 103.8754905502997, 2.0, "pc At EE3!"),
            PositionalMessage("e3f11d0f-9893-4071-9017-cea44c65144d", 18.69999998807907, 121.5, 91.32025328971724, 1.0, "pc At Coffee3!"),
            PositionalMessage("e50c3f02-f510-4e91-b45c-e5014ad2f934", 0.6132436211177826, 120.0, 77.40775491518451, 1.0, "pc At Arrow Align!"),
            PositionalMessage("07631cde-105b-4261-86bd-d99e79d6b2a6", 54.5, 115.0625, 50.5, 2.0, "pc At Core!"),
            PositionalMessage("17e34e01-93c9-424c-b8db-e41657e7d66b", 54.59254872526951, 115.0, 56.34217473324508, 2.0, "pc In Core!"),
            PositionalMessage("62288b0e-d3df-4018-b70d-d95f7fad651e", 54.24240920186189, 64.0, 114.66660999559907, 6.0, "pc In P4!"),
            PositionalMessage("6ac5429c-5af7-423b-84fd-c0c597545696", 54.31775427525154, 65.0, 76.4799767245337, 7.0, "pc At Mid!"),
            PositionalMessage("a4e71040-36f6-46ef-a2cb-1ac55324d988", 54.28683095111998, 5.0, 76.39912465508897, 3.0, "pc At P5 Center!"),
            PositionalMessage("86f79722-64e5-4b47-a489-e3d5cbede552", 91.69999998807907, 6.0, 94.36284743074178, 4.0, "pc At Blue Relic"),
            PositionalMessage("cc0d426a-27f2-4ef1-b608-7cf0aa1e3a7c", 92.69999998807907, 6.0, 56.4662995041467, 4.0, "pc At Orange Relic"),
            PositionalMessage("9f8ec8f2-0587-4081-85eb-9a1dc12ca701", 56.345462699334654, 8.0, 132.31408736121284, 4.0, "pc At Purple Relic"),
            PositionalMessage("6d684dab-c90a-4454-9d3c-e1a09800bcd6", 20.434745563352497, 6.0, 94.66442375098462, 4.0, "pc At Green Relic"),
            PositionalMessage("5652559e-5aa6-4c6d-b155-6505dc7c389e", 20.406750181700534, 6.0, 59.49344886607897, 4.0, "pc At Red Relic"),
            PositionalMessage("958ad7f8-b58d-4a5f-9ca4-d72e5f55193a", 54.35688230827807, 6.0, 43.35753830024535, 8.0, "pc At Altar")
        )
    }
}
