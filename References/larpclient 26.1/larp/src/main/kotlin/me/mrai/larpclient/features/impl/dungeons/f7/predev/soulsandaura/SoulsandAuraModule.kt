package me.mrai.larpclient.features.impl.dungeons.f7.predev.soulsandaura

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
import me.mrai.larpclient.util.LarpBranding
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.player.LocalPlayer
import net.minecraft.client.renderer.MappableRingBuffer
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.client.renderer.rendertype.RenderType
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket
import net.minecraft.resources.Identifier
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.state.BlockState
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

object SoulsandAuraModule : Module(
    name = "Soulsand Aura",
    description = "Tracks lava placement spots and aura-places soulsand near them when addon automation is available.",
    category = ModuleCategory.DUNGEONS_F7_PREDEV
) {
    private const val PLACE_RANGE = 4.5
    private const val PLACE_COOLDOWN_TICKS = 2

    private val showSolverSetting = BoolSetting("Show Solver", true)
    private val autoCompleteArrowAlignSetting = BoolSetting("Auto Place Soulsand", false)
        .shownWhen { showAutomationSettings }
    private val showAutomationSettings: Boolean
        get() = AddonAutomationAccess.hasAddonFeatures()
    private val automationAllowed: Boolean
        get() = AddonAutomationAccess.isAutomationEnabled()

    private val selectedPlacements = linkedSetOf<BlockPos>()

    private var editMode = false
    private var wasUsePressed = false
    private var placeCooldown = 0

    private val pipeline: RenderPipeline = RenderPipelines.register(
        RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath("larpclient", "pipeline/soulsand_aura_filled"))
            .build()
    )

    private val allocator = ByteBufferBuilder(RenderType.SMALL_BUFFER_SIZE)
    private var buffer: BufferBuilder? = null
    private var vertexBuffer: MappableRingBuffer? = null

    private val colorModulator = Vector4f(1f, 1f, 1f, 1f)
    private val modelOffset = Vector3f()
    private val textureMatrix = Matrix4f()

    init {
        settings += showSolverSetting
        settings += autoCompleteArrowAlignSetting
    }

    fun toggleEditMode(): Boolean {
        editMode = !editMode
        displayClientMessage(
            if (editMode) {
                "Soulsand Aura edit mode §aenabled§f. Right click a block face where a placement should be tracked."
            } else {
                "Soulsand Aura edit mode §cdisabled§f."
            }
        )
        return editMode
    }

    fun onClientTick() {
        val client = Minecraft.getInstance()
        val player = client.player
        val level = client.level

        if (placeCooldown > 0) {
            placeCooldown--
        }

        if (player == null || level == null) {
            wasUsePressed = false
            return
        }

        if (editMode) {
            handleEditClick(client)
        } else {
            wasUsePressed = client.options.keyUse.isDown
        }

        if (!enabled || client.screen != null) return
        if (placeCooldown > 0) return
        if (!automationAllowed || !autoCompleteArrowAlignSetting.value) return

        val targetPos = selectedPlacements.firstOrNull { pos ->
            isWithinPlaceRange(player.eyePosition, pos) && canPlaceAt(level.getBlockState(pos))
        } ?: return

        val supportPos = findSupportBlock(level, targetPos) ?: return
        val clickSide = Direction.entries.firstOrNull { supportPos.relative(it) == targetPos } ?: Direction.UP

        val soulsandSlot = findSoulsandSlot(player)
        if (soulsandSlot == -1) return

        val connection = client.connection ?: return
        val oldSlot = player.inventory.selectedSlot
        if (oldSlot != soulsandSlot) {
            player.inventory.selectedSlot = soulsandSlot
            connection.send(ServerboundSetCarriedItemPacket(soulsandSlot))
        }

        val gameMode = client.gameMode ?: return
        val hitVec = Vec3.atCenterOf(supportPos).add(
            clickSide.stepX * 0.5,
            clickSide.stepY * 0.5,
            clickSide.stepZ * 0.5
        )

        val hit = BlockHitResult(
            hitVec,
            clickSide,
            supportPos,
            false
        )

        val result = gameMode.useItemOn(player, InteractionHand.MAIN_HAND, hit)
        if (result.consumesAction()) {
            player.swing(InteractionHand.MAIN_HAND)
            placeCooldown = PLACE_COOLDOWN_TICKS
        }

        if (oldSlot != soulsandSlot) {
            player.inventory.selectedSlot = oldSlot
            connection.send(ServerboundSetCarriedItemPacket(oldSlot))
        }
    }

    fun render(context: LevelRenderContext) {
        val client = Minecraft.getInstance()
        client.player ?: return
        val level = client.level ?: return
        if (!editMode && (!enabled || !showSolverSetting.value)) return

        val matrices = context.poseStack()
        val cameraPos = client.gameRenderer.mainCamera.position()

        matrices.pushPose()
        matrices.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z)

        var wroteAny = false
        val builder = ensureBuffer()

        for (pos in selectedPlacements) {
            if (!canPlaceAt(level.getBlockState(pos))) continue
            wroteAny = true

            val color = if (editMode) {
                RenderColor(0.95f, 0.75f, 0.20f, 0.90f)
            } else {
                RenderColor(0.55f, 0.30f, 0.10f, 0.80f)
            }

            renderPlacementBox(
                matrices.last().pose(),
                builder,
                pos,
                color
            )
        }

        matrices.popPose()

        if (wroteAny) {
            drawBuiltBuffer(client)
        } else {
            buffer = null
        }
    }

    fun closeRenderer() {
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
            pipeline.vertexFormatMode,
            pipeline.vertexFormat
        )
        buffer = created
        return created
    }

    private fun renderPlacementBox(
        positionMatrix: Matrix4fc,
        buffer: BufferBuilder,
        pos: BlockPos,
        color: RenderColor
    ) {
        val minX = pos.x.toFloat() + 0.02f
        val minY = pos.y.toFloat() + 0.02f
        val minZ = pos.z.toFloat() + 0.02f
        val maxX = pos.x.toFloat() + 0.98f
        val maxY = pos.y.toFloat() + 0.98f
        val maxZ = pos.z.toFloat() + 0.98f

        renderFilledBox(
            positionMatrix,
            buffer,
            minX,
            minY,
            minZ,
            maxX,
            maxY,
            maxZ,
            color.r,
            color.g,
            color.b,
            color.a
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
        val vertexData: ByteBuffer = built.vertexBuffer()!!

        if (vertexBuffer == null || vertexBuffer!!.size() < vertexBufferSizeLong) {
            vertexBuffer?.close()
            vertexBuffer = MappableRingBuffer(
                { "larpclient soulsand aura renderer" },
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
            val mappedData: ByteBuffer = mappedView.data()!!
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
                { "larpclient soulsand aura render pass" },
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

    private fun handleEditClick(client: Minecraft) {
        val usePressed = client.options.keyUse.isDown

        if (usePressed && !wasUsePressed && client.screen == null) {
            val hit = client.hitResult
            if (hit is BlockHitResult && hit.type == HitResult.Type.BLOCK) {
                val level = client.level ?: run {
                    wasUsePressed = usePressed
                    return
                }

                val placePos = hit.blockPos.relative(hit.direction)
                val placeState = level.getBlockState(placePos)

                if (canPlaceAt(placeState)) {
                    if (selectedPlacements.add(placePos.immutable())) {
                        displayClientMessage("Added placement at §6${placePos.x}§f, §6${placePos.y}§f, §6${placePos.z}§f")
                    } else {
                        selectedPlacements.remove(placePos)
                        displayClientMessage("Removed placement at §6${placePos.x}§f, §6${placePos.y}§f, §6${placePos.z}§f")
                    }
                } else {
                    displayClientMessage("That position is not placeable.")
                }
            }
        }

        wasUsePressed = usePressed
    }

    private fun canPlaceAt(state: BlockState): Boolean {
        return state.isAir || state.canBeReplaced()
    }

    private fun findSupportBlock(level: ClientLevel, placePos: BlockPos): BlockPos? {
        for (dir in Direction.entries) {
            val support = placePos.relative(dir.opposite)
            val supportState = level.getBlockState(support)
            if (!supportState.isAir && supportState.isCollisionShapeFullBlock(level, support)) {
                return support
            }
        }
        return null
    }

    private fun isWithinPlaceRange(eyePosition: Vec3, pos: BlockPos): Boolean {
        return eyePosition.distanceTo(Vec3.atCenterOf(pos)) <= PLACE_RANGE
    }

    private fun findSoulsandSlot(player: LocalPlayer): Int {
        for (slot in 0 until 9) {
            val stack = player.inventory.getItem(slot)
            if (!stack.isEmpty && stack.item == Items.SOUL_SAND) {
                return slot
            }
        }
        return -1
    }

    private fun displayClientMessage(message: String) {
        val player = Minecraft.getInstance().player ?: return
        player.sendSystemMessage(LarpBranding.prefixed(message))
    }

    private data class RenderColor(
        val r: Float,
        val g: Float,
        val b: Float,
        val a: Float
    )
}
