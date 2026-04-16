package me.mrai.larpclient.features.impl.dungeons.f7.p5.autocowhat

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
import me.mrai.larpclient.util.LarpLog
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.MultiPlayerGameMode
import net.minecraft.client.player.LocalPlayer
import net.minecraft.client.renderer.MappableRingBuffer
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.resources.Identifier
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import org.joml.Matrix4f
import org.joml.Matrix4fc
import org.joml.Vector3f
import org.joml.Vector4f
import org.lwjgl.system.MemoryUtil
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.Path
import java.util.OptionalDouble
import java.util.OptionalInt

object AutoCowHatModule : Module(
    name = "Auto Cow Hat",
    description = "Silently equips Cow Hat when you enter configured zones.",
    category = ModuleCategory.DUNGEONS_F7_P5
) {
    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val configPath: Path =
        FabricLoader.getInstance().configDir.resolve("larpclient/auto_cow_hat_zones.json")

    data class Zone(
        var centerX: Double,
        var centerY: Double,
        var centerZ: Double,
        var sizeX: Double,
        var sizeY: Double,
        var sizeZ: Double
    ) {
        fun aabb(): AABB {
            val hx = sizeX / 2.0
            val hy = sizeY / 2.0
            val hz = sizeZ / 2.0
            return AABB(
                centerX - hx, centerY - hy, centerZ - hz,
                centerX + hx, centerY + hy, centerZ + hz
            )
        }

        fun contains(pos: Vec3): Boolean = aabb().contains(pos)
    }

    private data class ZoneConfig(
        var zones: MutableList<Zone> = mutableListOf()
    )

    private var config = ZoneConfig()

    @JvmField
    var pendingOpen = false

    @JvmField
    var suppressScreen = false

    @JvmField
    var pendingContainerId = -1

    private var busy = false
    private var awaitingContents = false
    private var insideLastTick = false
    private var timeoutTicks = 0
    private var clickDelayTicks = 0
    private var clickedForContainerId = -1
    private var lastEquipAttemptMs = 0L

    private const val ATTEMPT_COOLDOWN_MS = 1500L
    private const val OPEN_TIMEOUT_TICKS = 40

    private val pipeline: RenderPipeline = RenderPipelines.register(
        RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath("larpclient", "pipeline/auto_cow_hat"))
            .build()
    )

    private val allocator = ByteBufferBuilder(262144)
    private var buffer: BufferBuilder? = null
    private var vertexBuffer: MappableRingBuffer? = null
    private val colorModulator = Vector4f(1f, 1f, 1f, 1f)
    private val modelOffset = Vector3f()
    private val textureMatrix = Matrix4f()

    init {
        load()
    }

    override fun onEnable() {
        resetState()
    }

    override fun onDisable() {
        resetState()
    }

    fun onWorldChange() {
        resetState()
        insideLastTick = false
    }

    fun addZoneAtPlayer(sizeX: Double, sizeY: Double, sizeZ: Double): Boolean {
        val mc = Minecraft.getInstance()
        val player = mc.player ?: return false

        config.zones.add(
            Zone(
                centerX = player.x,
                centerY = player.y,
                centerZ = player.z,
                sizeX = sizeX,
                sizeY = sizeY,
                sizeZ = sizeZ
            )
        )
        save()
        return true
    }

    fun clearZones() {
        config.zones.clear()
        save()
    }

    fun getZones(): List<Zone> = config.zones

    override fun onTick() {
        if (!enabled) return

        val mc = Minecraft.getInstance()
        val player = mc.player ?: return

        if (timeoutTicks > 0) timeoutTicks--
        if (clickDelayTicks > 0) clickDelayTicks--

        if (timeoutTicks == 0 && isBusy()) {
            player.closeContainer()
            resetState()
            return
        }

        val inside = config.zones.any { it.contains(player.position()) }

        if (
            inside &&
            !insideLastTick &&
            !busy &&
            System.currentTimeMillis() - lastEquipAttemptMs >= ATTEMPT_COOLDOWN_MS
        ) {
            beginEquip(player)
        }

        insideLastTick = inside

        if (
            pendingContainerId != -1 &&
            !awaitingContents &&
            clickDelayTicks == 0 &&
            player.containerMenu.containerId == pendingContainerId
        ) {
            tryClickCowHat(mc.gameMode, player)
        }
    }

    private fun beginEquip(player: LocalPlayer) {
        pendingOpen = true
        suppressScreen = true
        pendingContainerId = -1
        busy = true
        awaitingContents = false
        timeoutTicks = OPEN_TIMEOUT_TICKS
        clickDelayTicks = 0
        clickedForContainerId = -1
        lastEquipAttemptMs = System.currentTimeMillis()

        try {
            player.connection.sendCommand("eq")
        } catch (_: Throwable) {
            resetState()
        }
    }

    fun onOpenScreenCaptured(containerId: Int, title: String) {
        if (!enabled) return
        if (!pendingOpen) return

        pendingOpen = false
        pendingContainerId = containerId
        awaitingContents = true
        clickedForContainerId = -1
    }

    fun onContainerContents(containerId: Int, items: List<ItemStack>) {
        if (!enabled) return
        if (!awaitingContents) return
        if (containerId != pendingContainerId) return

        awaitingContents = false

        val hasCowHat = items.any { stack ->
            !stack.isEmpty && stack.displayName.string.lowercase().contains("cow hat")
        }

        if (hasCowHat) {
            clickDelayTicks = 1
        } else {
            Minecraft.getInstance().player?.closeContainer()
            resetState()
        }
    }

    fun shouldSuppressScreen(title: String): Boolean {
        if (!enabled) return false
        if (!suppressScreen) return false
        return ChatFormatting.stripFormatting(title)?.isNotBlank() == true
    }

    private fun tryClickCowHat(gameMode: MultiPlayerGameMode?, player: LocalPlayer) {
        if (gameMode == null) {
            resetState()
            return
        }

        if (clickedForContainerId == pendingContainerId) return
        clickedForContainerId = pendingContainerId

        val menu = player.containerMenu
        val targetSlot = menu.slots.indexOfFirst { slot ->
            val stack = slot.item
            !stack.isEmpty && stack.displayName.string.lowercase().contains("cow hat")
        }

        if (targetSlot == -1) {
            player.closeContainer()
            resetState()
            return
        }

        val success = clickSlot(gameMode, pendingContainerId, targetSlot, player)
        player.closeContainer()

        if (!success) {
            resetState()
            return
        }

        resetState()
    }

    fun render(context: LevelRenderContext) {
        if (!enabled) return
        if (config.zones.isEmpty()) return

        val mc = Minecraft.getInstance()
        val cameraPos = mc.gameRenderer.mainCamera.position()
        val matrices = context.poseStack()

        matrices.pushPose()
        matrices.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z)

        val builder = ensureBuffer()
        val pose = matrices.last().pose()
        var wroteAny = false

        for (zone in config.zones) {
            val box = zone.aabb()

            renderFilledBox(
                pose,
                builder,
                box,
                0.73f,
                0.25f,
                0.95f,
                0.18f
            )
            renderOutlineBox(
                pose,
                builder,
                box,
                0.85f,
                0.50f,
                1.00f,
                1.00f
            )
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

    private fun renderOutlineBox(
        positionMatrix: Matrix4fc,
        buffer: BufferBuilder,
        box: AABB,
        red: Float,
        green: Float,
        blue: Float,
        alpha: Float
    ) {
        val t = 0.03f
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

    private fun upload(drawState: MeshData.DrawState, format: VertexFormat, built: MeshData): GpuBuffer {
        val vertexBufferSizeInt = drawState.vertexCount() * format.vertexSize
        val vertexBufferSizeLong = vertexBufferSizeInt.toLong()
        val vertexData: ByteBuffer = built.vertexBuffer()

        if (vertexBuffer == null || vertexBuffer!!.size() < vertexBufferSizeLong) {
            vertexBuffer?.close()
            vertexBuffer = MappableRingBuffer(
                { "larpclient auto cow hat renderer" },
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

    private fun draw(client: Minecraft, built: MeshData, drawState: MeshData.DrawState, vertices: GpuBuffer) {
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
            { "larpclient auto cow hat render pass" },
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

    private fun isBusy(): Boolean {
        return pendingOpen || awaitingContents || suppressScreen || pendingContainerId != -1 || busy
    }

    private fun resetState() {
        pendingOpen = false
        suppressScreen = false
        awaitingContents = false
        pendingContainerId = -1
        busy = false
        timeoutTicks = 0
        clickDelayTicks = 0
        clickedForContainerId = -1
    }

    private fun clickSlot(
        gameMode: MultiPlayerGameMode,
        containerId: Int,
        slot: Int,
        player: LocalPlayer
    ): Boolean {
        return try {
            val clickMethod = gameMode.javaClass.declaredMethods.firstOrNull { method ->
                method.parameterCount == 5 &&
                        method.parameterTypes[0] == Int::class.javaPrimitiveType &&
                        method.parameterTypes[1] == Int::class.javaPrimitiveType &&
                        method.parameterTypes[2] == Int::class.javaPrimitiveType &&
                        method.parameterTypes[3].isEnum &&
                        Player::class.java.isAssignableFrom(method.parameterTypes[4])
            } ?: return false

            clickMethod.isAccessible = true

            val clickTypeClass = clickMethod.parameterTypes[3]
            val clickType = clickTypeClass.enumConstants.firstOrNull {
                it.toString().equals("PICKUP", ignoreCase = true)
            } ?: clickTypeClass.enumConstants.firstOrNull() ?: return false

            clickMethod.invoke(gameMode, containerId, slot, 0, clickType, player)
            true
        } catch (_: Throwable) {
            false
        }
    }

    private fun load() {
        try {
            Files.createDirectories(configPath.parent)
            config = if (Files.exists(configPath)) {
                gson.fromJson(Files.readString(configPath), ZoneConfig::class.java) ?: ZoneConfig()
            } else {
                ZoneConfig()
            }
            save()
        } catch (throwable: Throwable) {
            LarpLog.warn("Failed to load Auto Cow Hat config from $configPath: ${throwable.message ?: throwable.javaClass.simpleName}")
            config = ZoneConfig()
        }
    }

    private fun save() {
        try {
            Files.createDirectories(configPath.parent)
            Files.writeString(configPath, gson.toJson(config))
        } catch (throwable: Throwable) {
            LarpLog.warn("Failed to save Auto Cow Hat config to $configPath: ${throwable.message ?: throwable.javaClass.simpleName}")
        }
    }
}
