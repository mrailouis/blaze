package me.mrai.larpclient.mixin;

import me.mrai.larpclient.ui.clickgui.render.RoundedRenderer;
import me.mrai.larpclient.ui.clickgui.render.ShaderRoundRectRenderer;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

@Mixin(GuiRenderer.class)
public abstract class GuiRendererRoundRectMixin {
    @Shadow @Final private MultiBufferSource.BufferSource bufferSource;
    @Shadow @Final private GuiRenderState renderState;

    @Unique
    private final List<ShaderRoundRectRenderer> larpclient$roundRectRenderers = new ArrayList<>();

    @Unique
    private int larpclient$roundRectRendererIndex = 0;

    @Inject(method = "preparePictureInPicture", at = @At("HEAD"))
    private void larpclient$beginRoundRectPrepare(CallbackInfo ci) {
        larpclient$roundRectRendererIndex = 0;
    }

    @Inject(method = "preparePictureInPicture", at = @At("RETURN"))
    private void larpclient$trimRoundRectRenderers(CallbackInfo ci) {
        while (larpclient$roundRectRenderers.size() > larpclient$roundRectRendererIndex) {
            int last = larpclient$roundRectRenderers.size() - 1;
            larpclient$roundRectRenderers.remove(last).close();
        }
    }

    @Inject(method = "preparePictureInPictureState", at = @At("HEAD"), cancellable = true)
    private void larpclient$prepareRoundRectState(PictureInPictureRenderState state, int guiScale, CallbackInfo ci) {
        if (!(state instanceof ShaderRoundRectRenderer.RoundRectState roundRectState)) {
            return;
        }

        ShaderRoundRectRenderer renderer;

        if (larpclient$roundRectRendererIndex < larpclient$roundRectRenderers.size()) {
            renderer = larpclient$roundRectRenderers.get(larpclient$roundRectRendererIndex);
        } else {
            renderer = new ShaderRoundRectRenderer(bufferSource);
            larpclient$roundRectRenderers.add(renderer);
        }

        larpclient$roundRectRendererIndex++;
        renderer.prepare(roundRectState, renderState, guiScale);
        ci.cancel();
    }

    @Inject(method = "endFrame", at = @At("RETURN"))
    private void larpclient$endRoundRectFrame(CallbackInfo ci) {
        RoundedRenderer.INSTANCE.endFrame();
    }

    @Inject(method = "close", at = @At("RETURN"))
    private void larpclient$closeRoundRectRenderers(CallbackInfo ci) {
        larpclient$roundRectRenderers.forEach(ShaderRoundRectRenderer::close);
        larpclient$roundRectRenderers.clear();
    }
}
