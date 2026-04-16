package me.mrai.larpclient.mixin;

import me.mrai.larpclient.features.impl.skyblock.general.improvedmenus.ImprovedSkyblockMenusState;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenImprovedMenusMixin {

    @Redirect(
        method = "extractLabels",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;text(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IIIZ)V"
        )
    )
    private void larpclient$overrideImprovedMenuLabelColor(
        GuiGraphicsExtractor graphics,
        Font font,
        Component text,
        int x,
        int y,
        int color,
        boolean shadow
    ) {
        AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) (Object) this;
        int effectiveColor = ImprovedSkyblockMenusState.INSTANCE.shouldOverride(screen)
            ? ImprovedSkyblockMenusState.INSTANCE.textColor(screen)
            : color;
        graphics.text(font, text, x, y, effectiveColor, shadow);
    }

    @Inject(method = "extractSlot", at = @At("HEAD"), cancellable = true)
    private void larpclient$hideImprovedMenuBlankSlots(GuiGraphicsExtractor graphics, Slot slot, int mouseX, int mouseY, CallbackInfo ci) {
        AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) (Object) this;
        if (ImprovedSkyblockMenusState.INSTANCE.shouldHideSlot(screen, slot)) {
            ci.cancel();
        }
    }
}
