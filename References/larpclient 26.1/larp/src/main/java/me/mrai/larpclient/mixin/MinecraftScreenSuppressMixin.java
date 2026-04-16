package me.mrai.larpclient.mixin;

import me.mrai.larpclient.features.impl.dungeons.f7.general.termgui.TermGuiModule;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import me.mrai.larpclient.features.impl.skyblock.general.preventcursorreset.PreventCursorResetState;
import me.mrai.larpclient.features.impl.skyblock.general.preventcursorreset.PreventCursorResetModule;

@Mixin(Minecraft.class)
public class MinecraftScreenSuppressMixin {

    @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
    private void larpclient$suppressScreen(Screen screen, CallbackInfo ci) {
        if (PreventCursorResetModule.INSTANCE.getEnabled()) {
            Minecraft minecraft = (Minecraft) (Object) this;
            if (screen != null) {
                PreventCursorResetState.INSTANCE.saveCursorPosition(minecraft.mouseHandler.xpos(), minecraft.mouseHandler.ypos());
            }
        }
        
        if (screen == null) return;
        if (screen instanceof me.mrai.larpclient.features.impl.dungeons.f7.general.termgui.TermGuiScreen) return;

        if (screen instanceof AbstractContainerScreen<?>) {
            Screen replacement = TermGuiModule.INSTANCE.createReplacementScreen(screen);
            if (replacement != null) {
                ci.cancel();
                ((Minecraft) (Object) this).setScreen(replacement);
                return;
            }
        }
    }
}
