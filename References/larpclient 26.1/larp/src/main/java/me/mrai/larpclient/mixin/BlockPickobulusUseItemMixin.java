package me.mrai.larpclient.mixin;

import me.mrai.larpclient.features.impl.kuudra.general.blockpickobulus.BlockPickobulusModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class BlockPickobulusUseItemMixin {

    @Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
    private void larpclient$blockPickobulusUse(CallbackInfo ci) {
        Minecraft client = (Minecraft) (Object) this;
        LocalPlayer player = client.player;
        if (player == null) return;

        InteractionHand hand = player.getUsedItemHand();
        if (hand == null) {
            hand = InteractionHand.MAIN_HAND;
        }

        ItemStack stack = player.getItemInHand(hand);
        if (!BlockPickobulusModule.INSTANCE.shouldCancelUse(stack)) {
            return;
        }

        ci.cancel();
    }
}
