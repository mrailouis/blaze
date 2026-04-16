package me.mrai.larpclient.mixin;

import me.mrai.larpclient.features.impl.kuudra.p1.autopearls.PearlBypassController;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(net.minecraft.world.item.ItemCooldowns.class)
public class ItemCooldownsMixin {

    @Inject(method = "isOnCooldown", at = @At("HEAD"), cancellable = true)
    private void larpclient$bypassPearlCooldown(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (PearlBypassController.INSTANCE.shouldBypass(stack)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "getCooldownPercent", at = @At("HEAD"), cancellable = true)
    private void larpclient$bypassPearlCooldownPercent(ItemStack stack, float partialTick, CallbackInfoReturnable<Float> cir) {
        if (PearlBypassController.INSTANCE.shouldBypass(stack)) {
            cir.setReturnValue(0.0f);
        }
    }

}
