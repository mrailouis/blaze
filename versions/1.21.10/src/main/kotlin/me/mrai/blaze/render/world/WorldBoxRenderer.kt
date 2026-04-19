package me.mrai.blaze.render.world

import com.mojang.blaze3d.pipeline.RenderPipeline
import com.mojang.blaze3d.platform.DepthTestFunction
import com.mojang.blaze3d.vertex.PoseStack
import java.util.OptionalDouble
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.client.renderer.RenderStateShard
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.renderer.ShapeRenderer
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3

object WorldBoxRenderer {
    private val depthlessFilledBoxPipeline: RenderPipeline = RenderPipelines.register(
        RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
            .withLocation("pipeline/blaze_depthless_filled_box")
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .build()
    )
    private val depthlessOutlinePipeline: RenderPipeline = RenderPipelines.register(
        RenderPipeline.builder(RenderPipelines.LINES_SNIPPET)
            .withLocation("pipeline/blaze_depthless_lines")
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .build()
    )
    private val depthlessFilledBoxRenderType: RenderType = RenderType.create(
        "blaze_depthless_filled_box",
        1536,
        false,
        true,
        depthlessFilledBoxPipeline,
        RenderType.CompositeState.builder()
            .setLayeringState(RenderStateShard.VIEW_OFFSET_Z_LAYERING)
            .createCompositeState(false)
    )
    private val depthlessOutlineRenderType: RenderType = RenderType.create(
        "blaze_depthless_lines",
        1536,
        depthlessOutlinePipeline,
        RenderType.CompositeState.builder()
            .setLineState(RenderStateShard.LineStateShard(OptionalDouble.empty()))
            .setLayeringState(RenderStateShard.VIEW_OFFSET_Z_LAYERING)
            .setOutputState(RenderStateShard.ITEM_ENTITY_TARGET)
            .createCompositeState(false)
    )

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

    data class SurfaceQuad(
        val a: Vec3,
        val b: Vec3,
        val c: Vec3,
        val d: Vec3,
        val style: BoxStyle
    )

    fun render(
        context: WorldRenderContext,
        boxes: List<Pair<AABB, BoxStyle>>,
        quads: List<SurfaceQuad> = emptyList(),
        depthless: Boolean = true
    ) {
        if (boxes.isEmpty() && quads.isEmpty()) return

        val client = Minecraft.getInstance()
        val poseStack = context.matrices()
        val cameraPos = client.gameRenderer.mainCamera.position()
        val bufferSource = client.renderBuffers().bufferSource()
        val fillRenderType = if (depthless) depthlessFilledBoxRenderType else RenderType.debugFilledBox()
        val outlineRenderType = if (depthless) depthlessOutlineRenderType else RenderType.lines()

        poseStack.pushPose()
        poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z)

        renderFilledBoxes(poseStack, bufferSource, fillRenderType, boxes)
        renderOutlinedBoxes(poseStack, bufferSource, outlineRenderType, boxes)
        renderFilledQuads(poseStack, bufferSource, fillRenderType, quads)

        poseStack.popPose()

        bufferSource.endBatch(fillRenderType)
        bufferSource.endBatch(outlineRenderType)
    }

    private fun renderFilledBoxes(
        poseStack: PoseStack,
        bufferSource: MultiBufferSource.BufferSource,
        renderType: RenderType,
        boxes: List<Pair<AABB, BoxStyle>>
    ) {
        val consumer = bufferSource.getBuffer(renderType)

        for ((box, style) in boxes) {
            ShapeRenderer.addChainedFilledBoxVertices(
                poseStack,
                consumer,
                box.minX,
                box.minY,
                box.minZ,
                box.maxX,
                box.maxY,
                box.maxZ,
                style.fillRed,
                style.fillGreen,
                style.fillBlue,
                style.fillAlpha
            )
        }
    }

    private fun renderOutlinedBoxes(
        poseStack: PoseStack,
        bufferSource: MultiBufferSource.BufferSource,
        renderType: RenderType,
        boxes: List<Pair<AABB, BoxStyle>>
    ) {
        val consumer = bufferSource.getBuffer(renderType)
        val pose = poseStack.last()

        for ((box, style) in boxes) {
            ShapeRenderer.renderLineBox(
                pose,
                consumer,
                box,
                style.outlineRed,
                style.outlineGreen,
                style.outlineBlue,
                style.outlineAlpha
            )
        }
    }

    private fun renderFilledQuads(
        poseStack: PoseStack,
        bufferSource: MultiBufferSource.BufferSource,
        renderType: RenderType,
        quads: List<SurfaceQuad>
    ) {
        val consumer = bufferSource.getBuffer(renderType)
        val matrix = poseStack.last().pose()

        for (quad in quads) {
            val style = quad.style
            for (point in listOf(quad.a, quad.b, quad.c, quad.d, quad.d, quad.c, quad.b, quad.a)) {
                consumer.addVertex(matrix, point.x.toFloat(), point.y.toFloat(), point.z.toFloat())
                    .setColor(style.fillRed, style.fillGreen, style.fillBlue, style.fillAlpha)
            }
        }
    }
}
