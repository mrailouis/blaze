package me.mrai.larpclient.mixin;

import me.mrai.larpclient.features.impl.skyblock.general.wardrobekeybinds.WardrobeKeybindsModule;
import me.mrai.larpclient.features.impl.skyblock.general.slotlocking.SlotLockingState;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenSlotLockingMixin<T extends AbstractContainerMenu> {

    @Shadow protected T menu;
    @Shadow protected Slot hoveredSlot;
    @Shadow protected int leftPos;
    @Shadow protected int topPos;

    @Inject(method = "extractContents", at = @At("TAIL"))
    private void larpclient$renderSlotLocking(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        SlotLockingState.INSTANCE.render((AbstractContainerScreen<?>) (Object) this, graphics, mouseX, mouseY);
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void larpclient$handleSlotLockKey(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
        if (WardrobeKeybindsModule.INSTANCE.onScreenKeyPressed((AbstractContainerScreen<?>) (Object) this, event.key())) {
            cir.setReturnValue(true);
            return;
        }
        if (SlotLockingState.INSTANCE.onKeyPressed(this.hoveredSlot, event.key())) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void larpclient$handleSlotBinding(MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
        if (SlotLockingState.INSTANCE.onMouseClicked((AbstractContainerScreen<?>) (Object) this, event.x(), event.y())) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "mouseReleased", at = @At("HEAD"), cancellable = true)
    private void larpclient$finishSlotBinding(MouseButtonEvent event, CallbackInfoReturnable<Boolean> cir) {
        if (SlotLockingState.INSTANCE.onMouseReleased((AbstractContainerScreen<?>) (Object) this, event.x(), event.y())) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "slotClicked", at = @At("HEAD"), cancellable = true)
    private void larpclient$preventLockedSlotInteractions(Slot slot, int slotId, int mouseButton, ContainerInput input, CallbackInfo ci) {
        if (SlotLockingState.INSTANCE.interceptSlotClick((AbstractContainerScreen<?>) (Object) this, slot, mouseButton, input)) {
            ci.cancel();
        }
    }
    @Inject(method = "removed", at = @At("HEAD"))
    private void larpclient$clearSlotLockState(CallbackInfo ci) {
        SlotLockingState.INSTANCE.onScreenClosed();
    }
}
