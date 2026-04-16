package me.mrai.larpclient.mixin;

import me.mrai.larpclient.features.impl.skyblock.general.storagegui.StorageOverlayRenderer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenStorageOverlayMixin<T extends AbstractContainerMenu> {

    @Inject(method = "extractLabels", at = @At("HEAD"), cancellable = true)
    private void larpclient$hideStorageLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY, CallbackInfo ci) {
        if (StorageOverlayRenderer.INSTANCE.shouldRender((AbstractContainerScreen<?>) (Object) this)) {
            ci.cancel();
        }
    }

    @Inject(method = "extractSlot", at = @At("HEAD"), cancellable = true)
    private void larpclient$hideStorageSlots(GuiGraphicsExtractor graphics, Slot slot, int mouseX, int mouseY, CallbackInfo ci) {
        if (StorageOverlayRenderer.INSTANCE.shouldRender((AbstractContainerScreen<?>) (Object) this)) {
            ci.cancel();
        }
    }

    @Inject(method = "extractSlotHighlightBack", at = @At("HEAD"), cancellable = true)
    private void larpclient$hideStorageHighlightBack(GuiGraphicsExtractor graphics, CallbackInfo ci) {
        if (StorageOverlayRenderer.INSTANCE.shouldRender((AbstractContainerScreen<?>) (Object) this)) {
            ci.cancel();
        }
    }

    @Inject(method = "extractSlotHighlightFront", at = @At("HEAD"), cancellable = true)
    private void larpclient$hideStorageHighlightFront(GuiGraphicsExtractor graphics, CallbackInfo ci) {
        if (StorageOverlayRenderer.INSTANCE.shouldRender((AbstractContainerScreen<?>) (Object) this)) {
            ci.cancel();
        }
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void larpclient$tickStorageOverlay(CallbackInfo ci) {
        StorageOverlayRenderer.INSTANCE.tick((AbstractContainerScreen<?>) (Object) this);
    }

    @Inject(method = "removed", at = @At("HEAD"))
    private void larpclient$clearStorageOverlay(CallbackInfo ci) {
        StorageOverlayRenderer.INSTANCE.onRemoved();
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void larpclient$handleStorageOverlayClick(MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
        if (StorageOverlayRenderer.INSTANCE.mouseClicked((AbstractContainerScreen<?>) (Object) this, event)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "mouseScrolled", at = @At("HEAD"), cancellable = true)
    private void larpclient$handleStorageOverlayScroll(double mouseX, double mouseY, double horizontalAmount, double verticalAmount, CallbackInfoReturnable<Boolean> cir) {
        if (StorageOverlayRenderer.INSTANCE.mouseScrolled((AbstractContainerScreen<?>) (Object) this, mouseX, mouseY, verticalAmount)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void larpclient$handleStorageOverlayKey(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
        if (StorageOverlayRenderer.INSTANCE.keyPressed((AbstractContainerScreen<?>) (Object) this, event)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "isHovering(Lnet/minecraft/world/inventory/Slot;DD)Z", at = @At("HEAD"), cancellable = true)
    private void larpclient$overrideStorageHover(Slot slot, double mouseX, double mouseY, CallbackInfoReturnable<Boolean> cir) {
        Boolean result = StorageOverlayRenderer.INSTANCE.overrideSlotHover((AbstractContainerScreen<?>) (Object) this, slot, mouseX, mouseY);
        if (result != null) {
            cir.setReturnValue(result);
        }
    }

    @Inject(method = "hasClickedOutside", at = @At("HEAD"), cancellable = true)
    private void larpclient$overrideStorageOutside(double mouseX, double mouseY, int leftPos, int topPos, CallbackInfoReturnable<Boolean> cir) {
        Boolean result = StorageOverlayRenderer.INSTANCE.overrideClickedOutside((AbstractContainerScreen<?>) (Object) this, mouseX, mouseY);
        if (result != null) {
            cir.setReturnValue(result);
        }
    }

}
