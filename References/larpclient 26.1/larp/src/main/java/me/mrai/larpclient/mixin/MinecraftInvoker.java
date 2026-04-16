package me.mrai.larpclient.mixin;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Minecraft.class)
public interface MinecraftInvoker {

    @Invoker("startAttack")
    boolean callStartAttack();

    @Invoker("startUseItem")
    void callStartUseItem();
}