package me.mrai.larpclient.mixin;

import me.mrai.larpclient.features.impl.skyblock.general.storagegui.StorageOverlayRenderer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.ShulkerBoxScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ShulkerBoxScreen.class)
public abstract class ShulkerBoxScreenStorageOverlayMixin {

    @Inject(method = "extractBackground", at = @At("HEAD"), cancellable = true)
    private void larpclient$renderStorageOverlayBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        ShulkerBoxScreen screen = (ShulkerBoxScreen) (Object) this;
        if (StorageOverlayRenderer.INSTANCE.shouldRender(screen)) {
            StorageOverlayRenderer.INSTANCE.render(screen, graphics, mouseX, mouseY);
            ci.cancel();
        }
    }
}
