package me.mrai.larpclient.render.gui

import com.mojang.blaze3d.buffers.Std140Builder
import com.mojang.blaze3d.buffers.Std140SizeCalculator
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.Tesselator
import com.mojang.blaze3d.vertex.VertexFormat
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.navigation.ScreenRectangle
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer
import net.minecraft.client.gui.render.state.pip.PictureInPictureRenderState
import net.minecraft.client.renderer.DynamicUniformStorage
import net.minecraft.client.renderer.MultiBufferSource
import org.joml.Matrix4f
import org.joml.Vector3f
import org.joml.Vector4f
import java.util.OptionalDouble
import java.util.OptionalInt
import kotlin.math.roundToInt

class RoundRectPIPRenderer(bufferSource: MultiBufferSource.BufferSource) :
    PictureInPictureRenderer<RoundRectPIPRenderer.State>(bufferSource) {

    private var lastState: State? = null

    override fun getRenderStateClass(): Class<State> = State::class.java

    override fun textureIsReadyToBlit(state: State): Boolean = state == lastState

    override fun renderToTexture(state: State, poseStack: PoseStack) {
        val guiScale = state.guiScale.toFloat()
        val outset = OUTSET * guiScale
        val tw = (state.width + 2 * OUTSET) * guiScale
        val th = (state.height + 2 * OUTSET) * guiScale

        // Build vertex positions for the quad
        val builder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION)
        builder.addVertex(0f, 0f, 0f)
        builder.addVertex(0f, th, 0f)
        builder.addVertex(tw, th, 0f)
        builder.addVertex(tw, 0f, 0f)
        val mesh = builder.buildOrThrow()

        // Setup dynamic transforms (projection matrices)
        val dynamicTransforms = RenderSystem.getDynamicUniforms().writeTransform(
            RenderSystem.getModelViewMatrix(),
            Vector4f(1f, 1f, 1f, 1f),
            Vector3f(),
            Matrix4f()
        )

        // Setup UBO data
        val uniformBuffer = uniformStorage.writeUniform { buffer ->
            // outsetScreenPx = the per-side expansion in screen pixels (outsetPx * guiScale).
            // state.width/height are the *expanded* GUI-pixel sizes; subtract the expansion to get
            // the original rect size the shader should draw, then center it within the texture.
            val outsetScreenPx = (OUTSET * guiScale).roundToInt() * state.guiScale
            val rectW = state.width * guiScale - 2f * outsetScreenPx
            val rectH = state.height * guiScale - 2f * outsetScreenPx
            val centerX = outsetScreenPx + rectW / 2f
            val centerY = outsetScreenPx + rectH / 2f
            val radiusPx = state.radius * guiScale

            Std140Builder.intoBuffer(buffer)
                .putVec4(centerX, centerY, rectW, rectH) // u_Rect
                .putVec4(radiusPx, radiusPx, radiusPx, radiusPx) // u_Radii (uniform radius)
                .putVec4(state.fillRed, state.fillGreen, state.fillBlue, state.fillAlpha) // u_Color
                .putVec4(state.fillRed, state.fillGreen, state.fillBlue, state.fillAlpha) // u_Color2 (no gradient)
                .putVec4(0f, 0f, 0f, 0f) // u_ColorShadow
                .putVec2(0f, 1f) // u_GradientDir
                .putFloat(1.5f) // u_EdgeSoftness (in pixels)
                .putFloat(0f) // u_ShadowSoftness
                .putFloat(state.borderWidth * guiScale) // u_BorderWidth
        }

        // Upload vertex buffer
        val vertexBuffer = LarpRenderPipelines.ROUND_RECT.vertexFormat.uploadImmediateVertexBuffer(mesh.vertexBuffer())
        val indexStorage = RenderSystem.getSequentialBuffer(mesh.drawState().mode())
        val indexBuffer = indexStorage.getBuffer(mesh.drawState().indexCount())

        // Get render target and execute render pass
        val renderTarget = Minecraft.getInstance().mainRenderTarget
        mesh.use {
            val colorView = RenderSystem.outputColorTextureOverride ?: renderTarget.colorTextureView
            if (colorView != null) {
                val depthView = if (renderTarget.useDepth) {
                    RenderSystem.outputDepthTextureOverride ?: renderTarget.depthTextureView
                } else {
                    null
                }

                RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                    { "LarpClient RoundRect" },
                    colorView,
                    OptionalInt.empty(),
                    depthView,
                    OptionalDouble.empty()
                ).use { pass ->
                    pass.setPipeline(LarpRenderPipelines.ROUND_RECT)
                    RenderSystem.bindDefaultUniforms(pass)
                    pass.setUniform("DynamicTransforms", dynamicTransforms)
                    pass.setUniform("u", uniformBuffer)
                    pass.setVertexBuffer(0, vertexBuffer)
                    pass.setIndexBuffer(indexBuffer, indexStorage.type())
                    pass.drawIndexed(0, 0, mesh.drawState().indexCount(), 1)
                }
            }
        }

        lastState = state
    }

    override fun getTextureLabel(): String = "LarpClient RoundRect"

    class State(
        val x: Int,
        val y: Int,
        val width: Int,
        val height: Int,
        val radius: Float,
        val fillRed: Float,
        val fillGreen: Float,
        val fillBlue: Float,
        val fillAlpha: Float,
        val borderWidth: Float,
        val guiScale: Int,
        val scissorRect: ScreenRectangle?,
    ) : PictureInPictureRenderState {

        override fun x0(): Int = x
        override fun x1(): Int = x + width
        override fun y0(): Int = y
        override fun y1(): Int = y + height
        override fun scale(): Float = 1f
        override fun scissorArea(): ScreenRectangle? = scissorRect
        override fun bounds(): ScreenRectangle? {
            val rect = ScreenRectangle(x, y, width, height)
            return scissorRect?.let { rect.intersection(it) } ?: rect
        }
    }

    companion object {
        private const val OUTSET = 0.5f // GUI pixels for anti-aliasing (minimal, since shader handles edge softness)

        // UBO layout: vec4 * 5 + vec2 + float * 3 = 80 + 8 + 12 = 100 → padded to 112
        private val UBO_SIZE = Std140SizeCalculator()
            .putVec4() // u_Rect
            .putVec4() // u_Radii
            .putVec4() // u_Color
            .putVec4() // u_Color2
            .putVec4() // u_ColorShadow
            .putVec2() // u_GradientDir
            .putFloat() // u_EdgeSoftness
            .putFloat() // u_ShadowSoftness
            .putFloat() // u_BorderWidth
            .get()

        private val uniformStorage = DynamicUniformStorage<DynamicUniformStorage.DynamicUniform>(
            "LarpClient RoundRect UBO",
            UBO_SIZE,
            64
        )

        fun submit(
            context: GuiGraphics,
            x: Int,
            y: Int,
            w: Int,
            h: Int,
            radius: Float,
            fillColor: Int,
            borderColor: Int = 0,
            borderWidth: Float = 0f
        ) {
            val fillA = (fillColor shr 24 and 0xFF) / 255f
            val fillR = (fillColor shr 16 and 0xFF) / 255f
            val fillG = (fillColor shr 8 and 0xFF) / 255f
            val fillB = (fillColor and 0xFF) / 255f

            val guiScale = Minecraft.getInstance().window.guiScale
            val scissorArea = context.scissorStack.peek()

            // Expand rect by OUTSET for anti-aliasing
            val outsetPx = (OUTSET * guiScale).roundToInt()
            val expandedX = x - outsetPx
            val expandedY = y - outsetPx
            val expandedW = w + 2 * outsetPx
            val expandedH = h + 2 * outsetPx

            val state = State(
                expandedX, expandedY, expandedW, expandedH, radius,
                fillR, fillG, fillB, fillA,
                borderWidth,
                guiScale,
                scissorArea
            )

            context.guiRenderState.submitPicturesInPictureState(state)
        }

        fun endFrame() {
            uniformStorage.endFrame()
        }
    }
}
