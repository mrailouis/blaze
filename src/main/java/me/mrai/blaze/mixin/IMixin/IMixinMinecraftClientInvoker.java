package me.mrai.blaze.mixin.IMixin;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Minecraft.class)
public interface IMixinMinecraftClientInvoker {
    @Invoker("startAttack")
    boolean blaze$startAttack();

    @Invoker("startUseItem")
    void blaze$startUseItem();
}
