package me.mrai.larpclient.mixin;

import me.mrai.larpclient.features.impl.skyblock.general.slotlocking.SlotLockingState;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LocalPlayer.class)
public abstract class LocalPlayerSlotLockingMixin {

    @Inject(method = "drop", at = @At("HEAD"), cancellable = true)
    private void larpclient$preventLockedDrop(boolean dropWholeStack, CallbackInfoReturnable<Boolean> cir) {
        LocalPlayer player = (LocalPlayer) (Object) this;
        int selectedSlot = player.getInventory().getSelectedSlot();

        if (SlotLockingState.INSTANCE.shouldCancelDropSelected(selectedSlot)) {
            SlotLockingState.INSTANCE.sendDropBlockedMessage();
            cir.setReturnValue(false);
        }
    }
}
