package me.mrai.larpclient.mixin;

import me.mrai.larpclient.features.impl.dungeons.f7.p3.autop3.AutoP3Module;
import me.mrai.larpclient.features.impl.skyblock.general.blink.BlinkModule;
import net.minecraft.client.player.KeyboardInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardInput.class)
public abstract class BlinkKeyboardInputMixin {

    @Inject(method = "tick", at = @At("TAIL"))
    private void larpclient$handleAddonClientInput(CallbackInfo ci) {
        KeyboardInput input = (KeyboardInput) (Object) this;
        AutoP3Module.INSTANCE.onInputTick(input);
        BlinkModule.INSTANCE.onInputTick(input);
    }
}
