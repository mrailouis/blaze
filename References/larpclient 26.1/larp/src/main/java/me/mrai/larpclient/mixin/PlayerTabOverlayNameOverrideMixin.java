package me.mrai.larpclient.mixin;

import me.mrai.larpclient.playeroverride.PlayerOverrideManager;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerTabOverlay.class)
public class PlayerTabOverlayNameOverrideMixin {

    @Inject(method = "getNameForDisplay", at = @At("RETURN"), cancellable = true)
    private void larpclient$rewritePlayerListName(PlayerInfo playerInfo, CallbackInfoReturnable<Component> cir) {
        Component current = cir.getReturnValue();
        if (current == null) {
            return;
        }

        Component rewritten = PlayerOverrideManager.rewritePlayerComponent(
            playerInfo.getProfile().id(),
            playerInfo.getProfile().name(),
            current
        );

        if (rewritten != current) {
            cir.setReturnValue(rewritten);
        }
    }
}
