package me.mrai.larpclient.ui.clickgui.render;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import me.mrai.larpclient.mixin.GuiGraphicsExtractorAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.DynamicUniformStorage;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3x2fStack;
import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.nio.ByteBuffer;
import java.util.OptionalDouble;
import java.util.OptionalInt;

public final class ShaderRoundRectRenderer extends PictureInPictureRenderer<ShaderRoundRectRenderer.RoundRectState> {
    private static final float DEFAULT_EDGE_SOFTNESS = 1.0F;
    private static final float DEFAULT_PADDING = 14.0F;
    private static final Vector4f NO_COLOR = new Vector4f();
    private static final Vector3f MODEL_OFFSET = new Vector3f();
    private static final Matrix4f TEXTURE_MATRIX = new Matrix4f();
    private static final Vector2f VERTICAL_GRADIENT = new Vector2f(0.0F, 1.0F);
    private static final DynamicUniformStorage<RoundRectUniform> UNIFORM_STORAGE =
        new DynamicUniformStorage<>("Larp RoundRect UBO", 112, 8);
    private static final RenderPipeline ROUND_RECT_PIPELINE = RenderPipelines.register(
        RenderPipeline.builder(RenderPipelines.GUI_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath("larpclient", "pipeline/round_rect"))
            .withVertexShader(Identifier.fromNamespaceAndPath("larpclient", "core/round_rect"))
            .withFragmentShader(Identifier.fromNamespaceAndPath("larpclient", "core/round_rect"))
            .withUniform("u", UniformType.UNIFORM_BUFFER)
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withVertexFormat(DefaultVertexFormat.POSITION, VertexFormat.Mode.QUADS)
            .build()
    );
    private RoundRectState lastState;

    public ShaderRoundRectRenderer(MultiBufferSource.BufferSource bufferSource) {
        super(bufferSource);
    }

    public static void endFrame() {
        UNIFORM_STORAGE.endFrame();
    }

    public static void draw(
        GuiGraphicsExtractor graphics,
        float x,
        float y,
        float width,
        float height,
        float radius,
        Vector4f color,
        Vector4f color2,
        Vector4f shadowColor,
        float borderWidth,
        float edgeSoftness,
        float shadowSoftness
    ) {
        if (width <= 0.01F || height <= 0.01F) {
            return;
        }

        GuiGraphicsExtractorAccessor accessor = (GuiGraphicsExtractorAccessor) graphics;
        Matrix3x2fStack pose = graphics.pose();
        float scaledX = pose.m20() + x * pose.m00();
        float scaledY = pose.m21() + y * pose.m11();
        float scaledWidth = width * pose.m00();
        float scaledHeight = height * pose.m11();
        float scaledRadius = radius * pose.m00();
        float scaledBorderWidth = borderWidth * pose.m00();
        float scaledEdgeSoftness = Math.max(DEFAULT_EDGE_SOFTNESS, edgeSoftness * pose.m00());
        float scaledShadowSoftness = Math.max(0.0F, shadowSoftness * pose.m00());
        float padding = Math.max(DEFAULT_PADDING, scaledShadowSoftness * 2.0F + scaledEdgeSoftness + scaledBorderWidth);
        ScreenRectangle scissor = accessor.larpclient$getScissorStack().peek();
        GuiRenderState renderState = accessor.larpclient$getGuiRenderState();

        renderState.addPicturesInPictureState(new RoundRectState(
            scaledX,
            scaledY,
            scaledWidth,
            scaledHeight,
            scaledRadius,
            new Vector4f(color),
            new Vector4f(color2),
            new Vector4f(shadowColor),
            new Vector2f(VERTICAL_GRADIENT),
            scaledBorderWidth,
            scaledEdgeSoftness,
            scaledShadowSoftness,
            padding,
            (float) Minecraft.getInstance().getWindow().getGuiScale(),
            scissor
        ));
    }

    @Override
    public Class<RoundRectState> getRenderStateClass() {
        return RoundRectState.class;
    }

    @Override
    protected boolean textureIsReadyToBlit(RoundRectState state) {
        return state.equals(lastState);
    }

    @Override
    protected String getTextureLabel() {
        return "larp_round_rect";
    }

    @Override
    protected void renderToTexture(RoundRectState state, PoseStack poseStack) {
        float scale = state.scale();
        float textureWidth = (state.extentX() + state.padding() * 2.0F + state.subpixelX()) * scale;
        float textureHeight = (state.extentY() + state.padding() * 2.0F + state.subpixelY()) * scale;

        BufferBuilder builder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
        builder.addVertex(0.0F, 0.0F, 0.0F);
        builder.addVertex(0.0F, textureHeight, 0.0F);
        builder.addVertex(textureWidth, textureHeight, 0.0F);
        builder.addVertex(textureWidth, 0.0F, 0.0F);

        try (MeshData mesh = builder.buildOrThrow()) {
            GpuBufferSlice dynamicTransforms = RenderSystem.getDynamicUniforms()
                .writeTransform(RenderSystem.getModelViewMatrix(), NO_COLOR, MODEL_OFFSET, TEXTURE_MATRIX);
            GpuBufferSlice uniform = UNIFORM_STORAGE.writeUniform(new RoundRectUniform(state, textureWidth, textureHeight));
            GpuBuffer vertexBuffer = ROUND_RECT_PIPELINE.getVertexFormat().uploadImmediateVertexBuffer(mesh.vertexBuffer());
            MeshData.DrawState drawState = mesh.drawState();
            RenderSystem.AutoStorageIndexBuffer sequential = RenderSystem.getSequentialBuffer(drawState.mode());

            Minecraft minecraft = Minecraft.getInstance();
            GpuTextureView colorView = RenderSystem.outputColorTextureOverride != null
                ? RenderSystem.outputColorTextureOverride
                : minecraft.getMainRenderTarget().getColorTextureView();

            if (colorView == null) {
                return;
            }

            GpuTextureView depthView = RenderSystem.outputDepthTextureOverride;

            if (depthView == null && minecraft.getMainRenderTarget().useDepth) {
                depthView = minecraft.getMainRenderTarget().getDepthTextureView();
            }

            try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                () -> "Larp RoundRect",
                colorView,
                OptionalInt.empty(),
                depthView,
                OptionalDouble.empty()
            )) {
                pass.setPipeline(ROUND_RECT_PIPELINE);
                RenderSystem.bindDefaultUniforms(pass);
                pass.setUniform("DynamicTransforms", dynamicTransforms);
                pass.setUniform("u", uniform);
                pass.setVertexBuffer(0, vertexBuffer);
                pass.setIndexBuffer(sequential.getBuffer(drawState.indexCount()), sequential.type());
                pass.drawIndexed(0, 0, drawState.indexCount(), 1);
            }
        }

        lastState = state;
    }

    private record RoundRectUniform(RoundRectState state, float textureWidth, float textureHeight)
        implements DynamicUniformStorage.DynamicUniform {
        @Override
        public void write(ByteBuffer buffer) {
            float scale = state.scale();
            float radius = state.clampedRadius() * scale;
            float centerX = state.subpixelX() * scale + textureWidth / 2.0F;
            float centerY = state.subpixelY() * scale + textureHeight / 2.0F;

            Std140Builder.intoBuffer(buffer)
                .putVec4(centerX, centerY, state.extentX() * scale, state.extentY() * scale)
                .putVec4(radius, radius, radius, radius)
                .putVec4(state.color())
                .putVec4(state.color2())
                .putVec4(state.shadowColor())
                .putVec2(state.gradientDir())
                .putFloat(state.edgeSoftness() * scale)
                .putFloat(state.shadowSoftness() * scale)
                .putFloat(state.borderWidth() * scale);
        }
    }

    public record RoundRectState(
        float x,
        float y,
        float extentX,
        float extentY,
        float radius,
        Vector4f color,
        Vector4f color2,
        Vector4f shadowColor,
        Vector2f gradientDir,
        float borderWidth,
        float edgeSoftness,
        float shadowSoftness,
        float padding,
        float scale,
        @Nullable ScreenRectangle scissorRect
    ) implements PictureInPictureRenderState {
        public float subpixelX() {
            return x - (float) Math.floor(x);
        }

        public float subpixelY() {
            return y - (float) Math.floor(y);
        }

        public float clampedRadius() {
            return Math.max(0.0F, Math.min(radius, Math.min(extentX, extentY) * 0.5F));
        }

        @Override
        public int x0() {
            return (int) Math.floor(x - padding);
        }

        @Override
        public int x1() {
            return (int) Math.ceil(x + extentX + padding);
        }

        @Override
        public int y0() {
            return (int) Math.floor(y - padding);
        }

        @Override
        public int y1() {
            return (int) Math.ceil(y + extentY + padding);
        }

        @Override
        public ScreenRectangle scissorArea() {
            ScreenRectangle bounds = PictureInPictureRenderState.getBounds(x0(), y0(), x1(), y1(), null);
            return scissorRect == null ? bounds : scissorRect.intersection(bounds);
        }

        @Override
        public ScreenRectangle bounds() {
            return PictureInPictureRenderState.getBounds(x0(), y0(), x1(), y1(), null);
        }
    }
}
