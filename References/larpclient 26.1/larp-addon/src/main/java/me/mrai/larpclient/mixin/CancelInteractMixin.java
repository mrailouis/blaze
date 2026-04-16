package me.mrai.larpclient.mixin;

import me.mrai.larpclient.features.impl.skyblock.general.cancelinteract.CancelInteractModule;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MultiPlayerGameMode.class)
public abstract class CancelInteractMixin {

    @Inject(
            method = "useItemOn(Lnet/minecraft/client/player/LocalPlayer;Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/phys/BlockHitResult;)Lnet/minecraft/world/InteractionResult;",
            at = @At("HEAD"),
            cancellable = true
    )
    private void larpclient$cancelBlockInteraction(
            LocalPlayer player,
            InteractionHand hand,
            BlockHitResult hitResult,
            CallbackInfoReturnable<InteractionResult> cir
    ) {
        ItemStack stack = player.getItemInHand(hand);

        if (!CancelInteractModule.INSTANCE.shouldUseItemInstead(stack)) {
            return;
        }

        cir.setReturnValue(InteractionResult.PASS);
        cir.cancel();
    }
}