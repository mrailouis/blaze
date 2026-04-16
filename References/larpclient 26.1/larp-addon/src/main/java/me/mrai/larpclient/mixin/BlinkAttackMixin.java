package me.mrai.larpclient.mixin;

import me.mrai.larpclient.features.impl.skyblock.general.blink.BlinkModule;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public abstract class BlinkAttackMixin {

    @Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
    private void larpclient$cancelAttackDuringBlinkAnchor(CallbackInfoReturnable<Boolean> cir) {
        if (BlinkModule.INSTANCE.shouldCancelAttack()) {
            cir.setReturnValue(false);
            cir.cancel();
        }
    }
}
