package me.mrai.larpclient.mixin;

import me.mrai.larpclient.features.impl.skyblock.general.abilitycooldowns.AbilityCooldownsModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class AbilityCooldownUseItemMixin {

    @Inject(method = "startUseItem", at = @At("HEAD"))
    private void larpclient$trackAbilityCooldownUse(CallbackInfo ci) {
        Minecraft client = (Minecraft) (Object) this;
        LocalPlayer player = client.player;
        if (player == null) {
            return;
        }

        AbilityCooldownsModule.INSTANCE.onItemUse(player.getMainHandItem());
    }
}
