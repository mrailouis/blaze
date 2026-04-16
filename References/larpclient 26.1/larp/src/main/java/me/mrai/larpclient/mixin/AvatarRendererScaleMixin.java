package me.mrai.larpclient.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import me.mrai.larpclient.playeroverride.PlayerOverrideManager;
import me.mrai.larpclient.playeroverride.PlayerScaleRenderStateAccess;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AvatarRenderer.class)
public class AvatarRendererScaleMixin {
    @Unique
    private boolean larpclient$scaledHandPose = false;

    @Inject(
        method = "scale(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;)V",
        at = @At("TAIL")
    )
    private void larpclient$applyAvatarScaleOverride(AvatarRenderState renderState, PoseStack poseStack, CallbackInfo ci) {
        PlayerScaleRenderStateAccess access = (PlayerScaleRenderStateAccess) renderState;
        float scaleX = access.larpclient$getPlayerScaleX();
        float scaleY = access.larpclient$getPlayerScaleY();
        float scaleZ = access.larpclient$getPlayerScaleZ();

        if (scaleX == 1.0f && scaleY == 1.0f && scaleZ == 1.0f) {
            return;
        }

        poseStack.scale(scaleX, scaleY, scaleZ);
    }

    @Inject(method = "renderRightHand", at = @At("HEAD"))
    private void larpclient$scaleRightHand(PoseStack poseStack, SubmitNodeCollector collector, int light, Identifier texture, boolean sleeve, CallbackInfo ci) {
        larpclient$pushLocalHandScale(poseStack);
    }

    @Inject(method = "renderRightHand", at = @At("TAIL"))
    private void larpclient$restoreRightHand(PoseStack poseStack, SubmitNodeCollector collector, int light, Identifier texture, boolean sleeve, CallbackInfo ci) {
        larpclient$popLocalHandScale(poseStack);
    }

    @Inject(method = "renderLeftHand", at = @At("HEAD"))
    private void larpclient$scaleLeftHand(PoseStack poseStack, SubmitNodeCollector collector, int light, Identifier texture, boolean sleeve, CallbackInfo ci) {
        larpclient$pushLocalHandScale(poseStack);
    }

    @Inject(method = "renderLeftHand", at = @At("TAIL"))
    private void larpclient$restoreLeftHand(PoseStack poseStack, SubmitNodeCollector collector, int light, Identifier texture, boolean sleeve, CallbackInfo ci) {
        larpclient$popLocalHandScale(poseStack);
    }

    @Unique
    private void larpclient$pushLocalHandScale(PoseStack poseStack) {
        Minecraft minecraft = Minecraft.getInstance();
        Player localPlayer = minecraft.player;
        if (localPlayer == null) {
            larpclient$scaledHandPose = false;
            return;
        }

        var override = PlayerOverrideManager.getOverride(localPlayer.getUUID());
        if (override == null) {
            larpclient$scaledHandPose = false;
            return;
        }

        if (override.getScaleX() == 1.0f && override.getScaleY() == 1.0f && override.getScaleZ() == 1.0f) {
            larpclient$scaledHandPose = false;
            return;
        }

        poseStack.pushPose();
        poseStack.scale(override.getScaleX(), override.getScaleY(), override.getScaleZ());
        larpclient$scaledHandPose = true;
    }

    @Unique
    private void larpclient$popLocalHandScale(PoseStack poseStack) {
        if (!larpclient$scaledHandPose) {
            return;
        }

        poseStack.popPose();
        larpclient$scaledHandPose = false;
    }
}
