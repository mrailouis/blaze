package me.mrai.larpclient.mixin;

import me.mrai.larpclient.features.impl.skyblock.general.abilitycooldowns.AbilityCooldownsModule;
import me.mrai.larpclient.features.impl.misc.other.headsscale.HeadScalingFeature;
import me.mrai.larpclient.features.impl.misc.ui.itemrarity.ItemRarityRenderer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3x2fStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiGraphicsExtractor.class)
public abstract class HeadsScaleGuiGraphicsMixin {

    @Unique
    private void larpclient$push(ItemStack stack, int x, int y) {
        Matrix3x2fStack pose = ((GuiGraphicsExtractor) (Object) this).pose();
        ItemRarityRenderer.drawItem((GuiGraphicsExtractor) (Object) this, stack, x, y);
        AbilityCooldownsModule.INSTANCE.drawBackground((GuiGraphicsExtractor) (Object) this, stack, x, y);
        HeadScalingFeature.applyGuiScale(stack, pose, x, y);
    }

    @Unique
    private void larpclient$pop(ItemStack stack) {
        Matrix3x2fStack pose = ((GuiGraphicsExtractor) (Object) this).pose();
        HeadScalingFeature.popGuiScale(stack, pose);
    }

    @Inject(method = "item(Lnet/minecraft/world/item/ItemStack;II)V", at = @At("HEAD"))
    private void larpclient$itemHead(ItemStack stack, int x, int y, CallbackInfo ci) {
        larpclient$push(stack, x, y);
    }

    @Inject(method = "item(Lnet/minecraft/world/item/ItemStack;II)V", at = @At("RETURN"))
    private void larpclient$itemReturn(ItemStack stack, int x, int y, CallbackInfo ci) {
        larpclient$pop(stack);
    }

    @Inject(method = "item(Lnet/minecraft/world/item/ItemStack;III)V", at = @At("HEAD"))
    private void larpclient$itemSeedHead(ItemStack stack, int x, int y, int seed, CallbackInfo ci) {
        larpclient$push(stack, x, y);
    }

    @Inject(method = "item(Lnet/minecraft/world/item/ItemStack;III)V", at = @At("RETURN"))
    private void larpclient$itemSeedReturn(ItemStack stack, int x, int y, int seed, CallbackInfo ci) {
        larpclient$pop(stack);
    }

    @Inject(method = "fakeItem(Lnet/minecraft/world/item/ItemStack;II)V", at = @At("HEAD"))
    private void larpclient$fakeItemHead(ItemStack stack, int x, int y, CallbackInfo ci) {
        larpclient$push(stack, x, y);
    }

    @Inject(method = "fakeItem(Lnet/minecraft/world/item/ItemStack;II)V", at = @At("RETURN"))
    private void larpclient$fakeItemReturn(ItemStack stack, int x, int y, CallbackInfo ci) {
        larpclient$pop(stack);
    }

    @Inject(method = "fakeItem(Lnet/minecraft/world/item/ItemStack;III)V", at = @At("HEAD"))
    private void larpclient$fakeItemSeedHead(ItemStack stack, int x, int y, int seed, CallbackInfo ci) {
        larpclient$push(stack, x, y);
    }

    @Inject(method = "fakeItem(Lnet/minecraft/world/item/ItemStack;III)V", at = @At("RETURN"))
    private void larpclient$fakeItemSeedReturn(ItemStack stack, int x, int y, int seed, CallbackInfo ci) {
        larpclient$pop(stack);
    }

    @Inject(method = "item(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;III)V", at = @At("HEAD"))
    private void larpclient$entityItemHead(LivingEntity entity, ItemStack stack, int x, int y, int seed, CallbackInfo ci) {
        larpclient$push(stack, x, y);
    }

    @Inject(method = "item(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;III)V", at = @At("RETURN"))
    private void larpclient$entityItemReturn(LivingEntity entity, ItemStack stack, int x, int y, int seed, CallbackInfo ci) {
        larpclient$pop(stack);
    }
}
