package me.mrai.larpclient.mixin;

import me.mrai.larpclient.features.impl.misc.ui.cleanscoreboard.CleanScoreboardModule;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public class HideScoreboardMixin {

    @Inject(method = "extractScoreboardSidebar", at = @At("HEAD"), cancellable = true)
    private void larpclient$hideVanillaScoreboard(
            GuiGraphicsExtractor graphics,
            DeltaTracker deltaTracker,
            CallbackInfo ci
    ) {
        if (CleanScoreboardModule.INSTANCE.getEnabled()
                && CleanScoreboardModule.INSTANCE.getHideVanilla().getValue()) {
            ci.cancel();
        }
    }
}