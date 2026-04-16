package me.mrai.larpclient.mixin;

import me.mrai.larpclient.playeroverride.PlayerOverrideManager;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.client.renderer.MultiBufferSource;
import org.joml.Matrix4fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(Font.class)
public class FontNameOverrideMixin {
    @Unique
    private static final ThreadLocal<Boolean> larpclient$rewriteGuard = ThreadLocal.withInitial(() -> false);

    @Inject(
        method = "drawInBatch(Ljava/lang/String;FFIZLorg/joml/Matrix4fc;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/client/gui/Font$DisplayMode;II)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void larpclient$rewriteDrawString(
        String text,
        float x,
        float y,
        int color,
        boolean shadow,
        Matrix4fc matrix,
        MultiBufferSource bufferSource,
        Font.DisplayMode displayMode,
        int backgroundColor,
        int packedLight,
        CallbackInfo ci
    ) {
        if (larpclient$rewriteGuard.get()) {
            return;
        }

        String rewritten = PlayerOverrideManager.rewriteLegacyText(text);
        if (rewritten.equals(text)) {
            return;
        }

        larpclient$rewriteGuard.set(true);
        try {
            ((Font) (Object) this).drawInBatch(rewritten, x, y, color, shadow, matrix, bufferSource, displayMode, backgroundColor, packedLight);
        } finally {
            larpclient$rewriteGuard.set(false);
        }

        ci.cancel();
    }

    @Inject(
        method = "drawInBatch(Lnet/minecraft/network/chat/Component;FFIZLorg/joml/Matrix4fc;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/client/gui/Font$DisplayMode;II)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void larpclient$rewriteDrawComponent(
        Component component,
        float x,
        float y,
        int color,
        boolean shadow,
        Matrix4fc matrix,
        MultiBufferSource bufferSource,
        Font.DisplayMode displayMode,
        int backgroundColor,
        int packedLight,
        CallbackInfo ci
    ) {
        if (larpclient$rewriteGuard.get()) {
            return;
        }

        Component rewritten = PlayerOverrideManager.rewriteComponent(component);
        if (rewritten == component) {
            return;
        }

        larpclient$rewriteGuard.set(true);
        try {
            ((Font) (Object) this).drawInBatch(rewritten, x, y, color, shadow, matrix, bufferSource, displayMode, backgroundColor, packedLight);
        } finally {
            larpclient$rewriteGuard.set(false);
        }

        ci.cancel();
    }

    @Inject(
        method = "width(Ljava/lang/String;)I",
        at = @At("HEAD"),
        cancellable = true
    )
    private void larpclient$rewriteStringWidth(String text, CallbackInfoReturnable<Integer> cir) {
        if (larpclient$rewriteGuard.get()) {
            return;
        }

        String rewritten = PlayerOverrideManager.rewriteLegacyText(text);
        if (rewritten.equals(text)) {
            return;
        }

        larpclient$rewriteGuard.set(true);
        try {
            cir.setReturnValue(((Font) (Object) this).width(rewritten));
        } finally {
            larpclient$rewriteGuard.set(false);
        }
    }

    @Inject(
        method = "width(Lnet/minecraft/network/chat/FormattedText;)I",
        at = @At("HEAD"),
        cancellable = true
    )
    private void larpclient$rewriteFormattedWidth(FormattedText formattedText, CallbackInfoReturnable<Integer> cir) {
        if (larpclient$rewriteGuard.get() || !(formattedText instanceof Component component)) {
            return;
        }

        Component rewritten = PlayerOverrideManager.rewriteComponent(component);
        if (rewritten == component) {
            return;
        }

        larpclient$rewriteGuard.set(true);
        try {
            cir.setReturnValue(((Font) (Object) this).width((FormattedText) rewritten));
        } finally {
            larpclient$rewriteGuard.set(false);
        }
    }

    @Inject(
        method = "split(Lnet/minecraft/network/chat/FormattedText;I)Ljava/util/List;",
        at = @At("HEAD"),
        cancellable = true
    )
    private void larpclient$rewriteSplit(
        FormattedText formattedText,
        int maxWidth,
        CallbackInfoReturnable<List<FormattedCharSequence>> cir
    ) {
        if (larpclient$rewriteGuard.get() || !(formattedText instanceof Component component)) {
            return;
        }

        Component rewritten = PlayerOverrideManager.rewriteComponent(component);
        if (rewritten == component) {
            return;
        }

        larpclient$rewriteGuard.set(true);
        try {
            cir.setReturnValue(((Font) (Object) this).split(rewritten, maxWidth));
        } finally {
            larpclient$rewriteGuard.set(false);
        }
    }
}
