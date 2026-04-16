package me.mrai.larpclient.features.impl.kuudra.p1.crateaura

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
import me.mrai.larpclient.module.SliderSetting
import me.mrai.larpclient.features.impl.kuudra.p1.summoncrates.SummonCratesSimulator
import me.mrai.larpclient.util.LarpLog
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.MappableRingBuffer
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.client.renderer.rendertype.RenderType
import net.minecraft.network.protocol.game.ServerboundInteractPacket
import net.minecraft.resources.Identifier
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.monster.Giant
import net.minecraft.world.entity.monster.zombie.Zombie
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import org.joml.Matrix4f
import org.joml.Matrix4fc
import org.joml.Vector3f
import org.joml.Vector4f
import org.lwjgl.system.MemoryUtil
import java.nio.ByteBuffer
import java.util.OptionalDouble
import java.util.OptionalInt

object CrateAuraModule : Module(
    name = "Crate Aura",
    description = "Interacts with Kuudra supply crate zombies using C2S interact packets.",
    category = ModuleCategory.KUUDRA_P1
) {
    private val reach = SliderSetting("Reach", 7.0, 1.0, 12.0, 0.1)

    private const val START_MESSAGE = "[NPC] Elle: Okay adventurers, I will go and fish up Kuudra!"
    private const val SUPPLY_PHASE_MESSAGE =
        "[NPC] Elle: ARGH! All of the supplies fell into the lava! You need to retrieve them quickly!"
    private const val FINISH_MESSAGE =
        "[NPC] Elle: OMG! Great work collecting my supplies!"
    private const val PICKING_UP_MESSAGE =
        "You are already currently picking up some supplies!"
    private const val CONTESTED_MESSAGE =
        "Someone else is currently trying to pick up these supplies!"
    private const val RETRIEVED_MESSAGE =
        "You retrieved some of Elle's supplies from the Lava!"
    private const val SLIPPED_MESSAGE =
        "You moved and the Chest slipped out of your hands!"

    private const val RENDER_RANGE = 40.0
    private const val ATTEMPT_COOLDOWN_TICKS = 8

    private var kuudraRunning = false
    private var supplyPhase = false
    private var blockedByPickupState = false
    private var attemptCooldown = 0

    private val highlightedEntityIds = linkedSetOf<Int>()

    private val pipeline: RenderPipeline = RenderPipelines.register(
        RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath("larpclient", "pipeline/crate_aura_boxes"))
            .build()
    )

    private val allocator = ByteBufferBuilder(RenderType.SMALL_BUFFER_SIZE)
    private var buffer: BufferBuilder? = null
    private var vertexBuffer: MappableRingBuffer? = null

    private val colorModulator = Vector4f(1f, 1f, 1f, 1f)
    private val modelOffset = Vector3f()
    private val textureMatrix = Matrix4f()

    init {
        settings += reach
    }

    override fun onEnable() {
        kuudraRunning = false
        supplyPhase = false
        blockedByPickupState = false
        attemptCooldown = 0
        highlightedEntityIds.clear()
    }

    override fun onDisable() {
        kuudraRunning = false
        supplyPhase = false
        blockedByPickupState = false
        attemptCooldown = 0
        highlightedEntityIds.clear()
    }

    override fun onTick() {
        if (!enabled) return

        if (attemptCooldown > 0) {
            attemptCooldown--
        }

        val mc = Minecraft.getInstance()
        val player = mc.player ?: return
        val level = mc.level ?: return
        val connection = mc.connection ?: return

        val renderRangeSq = RENDER_RANGE * RENDER_RANGE
        val interactRangeSq = reach.value * reach.value

        val allTargets = level.entitiesForRendering()
            .asSequence()
            .filter { isValidSupplyEntity(it) }
            .filter { player.distanceToSqr(it) <= renderRangeSq }
            .sortedBy { player.distanceToSqr(it) }
            .toList()

        highlightedEntityIds.clear()
        highlightedEntityIds.addAll(allTargets.map { it.id }.filterNot(SummonCratesSimulator::isFakeEntity))

        if (!kuudraRunning || !supplyPhase) return
        if (blockedByPickupState) return
        if (attemptCooldown > 0) return

        val target = allTargets.firstOrNull { player.distanceToSqr(it) <= interactRangeSq } ?: return

        if (SummonCratesSimulator.tryStartPickup(target)) {
            attemptCooldown = ATTEMPT_COOLDOWN_TICKS
            return
        }

        val hitVec = relativeHitVec(player, target)
        val secondary = player.isShiftKeyDown

        connection.send(
            ServerboundInteractPacket(
                target.id,
                InteractionHand.MAIN_HAND,
                hitVec,
                secondary
            )
        )

        connection.send(
            ServerboundInteractPacket(
                target.id,
                InteractionHand.MAIN_HAND,
                Vec3.ZERO,
                secondary
            )
        )

        attemptCooldown = ATTEMPT_COOLDOWN_TICKS
    }

    fun onRawMessage(cleanText: String) {
        when (cleanText) {
            START_MESSAGE -> {
                kuudraRunning = true
                supplyPhase = false
                blockedByPickupState = false
                attemptCooldown = 0
                LarpLog.debug("Crate Aura detected Kuudra start.")
            }

            SUPPLY_PHASE_MESSAGE -> {
                kuudraRunning = true
                supplyPhase = true
                blockedByPickupState = false
                attemptCooldown = 0
                LarpLog.debug("Crate Aura detected supply phase start.")
            }

            FINISH_MESSAGE -> {
                supplyPhase = false
                blockedByPickupState = false
                attemptCooldown = 0
            }

            PICKING_UP_MESSAGE -> {
                if (!blockedByPickupState) {
                    blockedByPickupState = true
                    attemptCooldown = 12
                    LarpLog.debug("Crate Aura paused because pickup is already in progress.")
                }
            }

            CONTESTED_MESSAGE -> {
                if (!blockedByPickupState) {
                    blockedByPickupState = true
                    attemptCooldown = 12
                    LarpLog.debug("Crate Aura detected contested pickup.")
                }
            }

            RETRIEVED_MESSAGE -> {
                blockedByPickupState = false
                attemptCooldown = 10
                LarpLog.debug("Crate Aura registered a successful pickup.")
            }

            SLIPPED_MESSAGE -> {
                blockedByPickupState = false
                attemptCooldown = 0
                LarpLog.debug("Crate Aura registered a slipped chest.")
            }
        }
    }

    fun onWorldChange() {
        kuudraRunning = false
        supplyPhase = false
        blockedByPickupState = false
        attemptCooldown = 0
        highlightedEntityIds.clear()
        LarpLog.debug("Crate Aura state cleared on world change.")
    }

    fun render(context: LevelRenderContext) {
        if (!enabled) return
        if (highlightedEntityIds.isEmpty()) return

        val mc = Minecraft.getInstance()
        val level = mc.level ?: return
        val cameraPos = mc.gameRenderer.mainCamera.position()
        val matrices = context.poseStack()

        matrices.pushPose()
        matrices.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z)

        var wroteAny = false
        val builder = ensureBuffer()
        val pose = matrices.last().pose()

        for (entity in level.entitiesForRendering()) {
            if (entity.id !in highlightedEntityIds) continue

            val box = entity.boundingBox.inflate(0.05)
            renderFilledBox(
                pose,
                builder,
                box,
                0.0f,
                1.0f,
                1.0f,
                0.22f
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

    private fun isValidSupplyEntity(entity: Entity): Boolean {
        if (!entity.isAlive) return false
        return entity is Zombie || entity is Giant
    }

    private fun relativeHitVec(player: Entity, target: Entity): Vec3 {
        val playerCenter = player.boundingBox.center
        val box = target.boundingBox

        val x = playerCenter.x.coerceIn(box.minX, box.maxX)
        val y = playerCenter.y.coerceIn(box.minY, box.maxY)
        val z = playerCenter.z.coerceIn(box.minZ, box.maxZ)

        return Vec3(
            x - target.x,
            y - target.y,
            z - target.z
        )
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
            ?: throw IllegalStateException("Vertex buffer was null")

        if (vertexBuffer == null || vertexBuffer!!.size() < vertexBufferSizeLong) {
            vertexBuffer?.close()
            vertexBuffer = MappableRingBuffer(
                { "larpclient crate aura renderer" },
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
                ?: throw IllegalStateException("Mapped GPU buffer data was null")
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
            .writeTransform(
                RenderSystem.getModelViewMatrix(),
                colorModulator,
                modelOffset,
                textureMatrix
            )

        val colorView = requireNotNull(client.mainRenderTarget.colorTextureView)
        val depthView = requireNotNull(client.mainRenderTarget.depthTextureView)

        RenderSystem.getDevice()
            .createCommandEncoder()
            .createRenderPass(
                { "larpclient crate aura render pass" },
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
}
