package me.mrai.larpclient.mixin;

import me.mrai.larpclient.features.impl.skyblock.general.preventcursorreset.PreventCursorResetState;
import me.mrai.larpclient.features.impl.skyblock.general.preventcursorreset.PreventCursorResetModule;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Screen.class)
public abstract class ScreenPreventCursorResetMixin {

    @Inject(method = "init", at = @At("TAIL"))
    private void larpclient$restoreCursorOnScreenInit(CallbackInfo ci) {
        if (PreventCursorResetModule.INSTANCE.getEnabled()) {
            Screen screen = (Screen) (Object) this;
            double savedX = PreventCursorResetState.INSTANCE.getLastMouseX();
            double savedY = PreventCursorResetState.INSTANCE.getLastMouseY();
            
            if (savedX > 0 || savedY > 0) {
                screen.mouseMoved(savedX, savedY);
            }
        }
    }
}
