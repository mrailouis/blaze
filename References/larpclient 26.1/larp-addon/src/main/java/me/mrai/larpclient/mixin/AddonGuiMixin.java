package me.mrai.larpclient.mixin;

import me.mrai.larpclient.ui.hud.BlinkHudRenderer;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public class AddonGuiMixin {

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void larpclient$renderAddonHud(GuiGraphicsExtractor context, DeltaTracker deltaTracker, CallbackInfo ci) {
        BlinkHudRenderer.INSTANCE.render(context);
    }
}
