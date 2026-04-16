package me.mrai.larpclient.mixin;

import me.mrai.larpclient.features.impl.misc.ui.cleanscoreboard.CleanScoreboardRenderer;
import me.mrai.larpclient.ui.hud.ArcherUtilsHudRenderer;
import me.mrai.larpclient.ui.hud.AutoClickerCpsHudRenderer;
import me.mrai.larpclient.ui.hud.BuildProgressHudRenderer;
import me.mrai.larpclient.ui.hud.DeployableDisplayHudRenderer;
import me.mrai.larpclient.ui.hud.GolemTimerHudRenderer;
import me.mrai.larpclient.ui.hud.KuudraDirectionHudRenderer;
import me.mrai.larpclient.ui.hud.KuudraGoToHudRenderer;
import me.mrai.larpclient.ui.hud.KuudraPriorityHudRenderer;
import me.mrai.larpclient.ui.hud.KuudraSpawnedCratesHudRenderer;
import me.mrai.larpclient.ui.hud.MaxorHpHudRenderer;
import me.mrai.larpclient.ui.hud.LastBreathUtilsHudRenderer;
import me.mrai.larpclient.ui.hud.ModuleListRenderer;
import me.mrai.larpclient.ui.hud.PerformanceHudRenderer;
import me.mrai.larpclient.ui.toast.ToastManager;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import me.mrai.larpclient.features.impl.dungeons.general.truesplits.TrueSplitsRenderer;

@Mixin(Gui.class)
public class GuiMixin {

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void larpclient$renderCustomHud(GuiGraphicsExtractor context, DeltaTracker deltaTracker, CallbackInfo ci) {
        CleanScoreboardRenderer.INSTANCE.render(context);
        ModuleListRenderer.INSTANCE.render(context);
        AutoClickerCpsHudRenderer.INSTANCE.render(context);
        KuudraGoToHudRenderer.INSTANCE.render(context);
        KuudraDirectionHudRenderer.INSTANCE.render(context);
        KuudraPriorityHudRenderer.INSTANCE.render(context);
        KuudraSpawnedCratesHudRenderer.INSTANCE.render(context);
        BuildProgressHudRenderer.INSTANCE.render(context);
        DeployableDisplayHudRenderer.INSTANCE.render(context);
        GolemTimerHudRenderer.INSTANCE.render(context);
        PerformanceHudRenderer.INSTANCE.render(context);
        ArcherUtilsHudRenderer.INSTANCE.render(context);
        LastBreathUtilsHudRenderer.INSTANCE.render(context);
        MaxorHpHudRenderer.INSTANCE.render(context);
        ToastManager.INSTANCE.render(context);
        TrueSplitsRenderer.INSTANCE.render(context);
    }
}
