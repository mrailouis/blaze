package me.mrai.larpclient.mixin;

import me.mrai.larpclient.features.impl.misc.ui.playercustomisations.PlayerCustomisationsModule;
import me.mrai.larpclient.playeroverride.PlayerDisplayNameResolver;
import me.mrai.larpclient.playeroverride.PlayerOverrideManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityAttachment;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderer.class)
public class EntityRendererNameOverrideMixin {

    @Inject(method = "getNameTag", at = @At("RETURN"), cancellable = true)
    private void larpclient$rewritePlayerNameTag(Entity entity, CallbackInfoReturnable<Component> cir) {
        Component current = cir.getReturnValue();
        if (current == null) {
            return;
        }

        Component rewritten = entity instanceof Player player
            ? PlayerOverrideManager.rewritePlayerComponent(
                player.getUUID(),
                player.getGameProfile().name(),
                current
            )
            : PlayerOverrideManager.rewriteComponent(current);

        if (rewritten != current) {
            cir.setReturnValue(rewritten);
        }
    }

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void larpclient$rewriteExtractedNames(Entity entity, EntityRenderState renderState, float tickDelta, CallbackInfo ci) {
        if (renderState.nameTag != null) {
            renderState.nameTag = PlayerOverrideManager.rewriteComponent(renderState.nameTag);
        }

        if (renderState.scoreText != null) {
            renderState.scoreText = PlayerOverrideManager.rewriteComponent(renderState.scoreText);
        }

        if (!(entity instanceof Player player)) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (
            player == minecraft.player
            && PlayerCustomisationsModule.shouldForceOwnNameTag()
            && !minecraft.options.getCameraType().isFirstPerson()
        ) {
            renderState.nameTag = PlayerOverrideManager.rewritePlayerComponent(
                player.getUUID(),
                player.getGameProfile().name(),
                PlayerDisplayNameResolver.resolveTabListName(player.getUUID(), player.getGameProfile().name())
            );
            renderState.nameTagAttachment = entity.getAttachments()
                .getNullable(EntityAttachment.NAME_TAG, 0, entity.getYRot(tickDelta));
        }

        if (renderState.nameTag != null) {
            renderState.nameTag = PlayerOverrideManager.rewritePlayerComponent(
                player.getUUID(),
                player.getGameProfile().name(),
                renderState.nameTag
            );
        }

        var override = PlayerOverrideManager.getOverride(player.getUUID());
        if (override == null || renderState.nameTagAttachment == null || override.getScaleY() == 1.0f) {
            return;
        }

        Vec3 attachment = renderState.nameTagAttachment;
        renderState.nameTagAttachment = new Vec3(attachment.x, attachment.y * override.getScaleY(), attachment.z);
    }
}
