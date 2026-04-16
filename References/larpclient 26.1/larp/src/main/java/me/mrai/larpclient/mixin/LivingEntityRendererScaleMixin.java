package me.mrai.larpclient.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import me.mrai.larpclient.playeroverride.PlayerOverrideManager;
import me.mrai.larpclient.playeroverride.PlayerScaleRenderStateAccess;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class)
public class LivingEntityRendererScaleMixin {

    @Inject(
        method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V",
        at = @At("TAIL")
    )
    private void larpclient$capturePlayerScaleOverride(
        LivingEntity entity,
        LivingEntityRenderState renderState,
        float tickDelta,
        CallbackInfo ci
    ) {
        PlayerScaleRenderStateAccess access = (PlayerScaleRenderStateAccess) renderState;

        if (entity instanceof Player player) {
            var override = PlayerOverrideManager.getOverride(player.getUUID());
            if (override != null) {
                access.larpclient$setPlayerScale(override.getScaleX(), override.getScaleY(), override.getScaleZ());
                return;
            }
        }

        access.larpclient$setPlayerScale(1.0f, 1.0f, 1.0f);
    }

    @Inject(
        method = "scale(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;)V",
        at = @At("TAIL")
    )
    private void larpclient$applyPlayerScaleOverride(
        LivingEntityRenderState renderState,
        PoseStack poseStack,
        CallbackInfo ci
    ) {
        PlayerScaleRenderStateAccess access = (PlayerScaleRenderStateAccess) renderState;
        float scaleX = access.larpclient$getPlayerScaleX();
        float scaleY = access.larpclient$getPlayerScaleY();
        float scaleZ = access.larpclient$getPlayerScaleZ();

        if (scaleX == 1.0f && scaleY == 1.0f && scaleZ == 1.0f) {
            return;
        }

        poseStack.scale(scaleX, scaleY, scaleZ);
    }
}
