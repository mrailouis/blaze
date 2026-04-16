package me.mrai.larpclient.mixin;

import me.mrai.larpclient.features.impl.kuudra.p1.summoncrates.SummonCratesSimulator;
import me.mrai.larpclient.features.impl.misc.other.etherwarp.EtherwarpController;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class EtherwarpUseItemMixin {

    @Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
    private void larpclient$handleEtherwarpUse(CallbackInfo ci) {
        Minecraft client = (Minecraft) (Object) this;
        if (SummonCratesSimulator.INSTANCE.handleUseItem(client)) {
            ci.cancel();
            return;
        }
        if (EtherwarpController.INSTANCE.handleUseItem(client)) {
            ci.cancel();
        }
    }
}
