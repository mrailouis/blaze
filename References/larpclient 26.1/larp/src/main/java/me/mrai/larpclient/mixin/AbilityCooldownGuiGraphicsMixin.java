package me.mrai.larpclient.mixin;

import me.mrai.larpclient.features.impl.skyblock.general.abilitycooldowns.AbilityCooldownsModule;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiGraphicsExtractor.class)
public abstract class AbilityCooldownGuiGraphicsMixin {

    @Inject(method = "itemDecorations(Lnet/minecraft/client/gui/Font;Lnet/minecraft/world/item/ItemStack;IILjava/lang/String;)V", at = @At("RETURN"))
    private void larpclient$drawAbilityCooldownText(Font font, ItemStack stack, int x, int y, String countLabel, CallbackInfo ci) {
        AbilityCooldownsModule.INSTANCE.drawText((GuiGraphicsExtractor) (Object) this, font, stack, x, y);
    }
}
