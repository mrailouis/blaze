package me.mrai.larpclient.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(AbstractContainerScreen.class)
public interface AbstractContainerScreenAccessor {

    @Accessor("leftPos")
    int getLarpclientLeftPos();

    @Accessor("topPos")
    int getLarpclientTopPos();

    @Accessor("hoveredSlot")
    Slot getLarpclientHoveredSlot();

    @Invoker("getHoveredSlot")
    Slot callLarpclientGetHoveredSlot(double mouseX, double mouseY);

    @Accessor("imageWidth")
    int getLarpclientImageWidth();

    @Accessor("imageHeight")
    int getLarpclientImageHeight();
}
