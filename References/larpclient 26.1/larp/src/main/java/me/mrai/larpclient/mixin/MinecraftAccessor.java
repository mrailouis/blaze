package me.mrai.larpclient.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Minecraft.class)
public interface MinecraftAccessor {

    @Accessor("rightClickDelay")
    int getRightClickDelay();

    @Accessor("rightClickDelay")
    void setRightClickDelay(int value);

    @Accessor("missTime")
    int getMissTime();

    @Accessor("missTime")
    void setMissTime(int value);

    @Accessor("screen")
    @Nullable Screen getScreenField();

    @Accessor("screen")
    void setScreenField(@Nullable Screen screen);
}