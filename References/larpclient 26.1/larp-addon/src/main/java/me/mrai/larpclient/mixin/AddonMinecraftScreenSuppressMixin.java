package me.mrai.larpclient.mixin;

import me.mrai.larpclient.features.impl.dungeons.f7.p5.autocowhat.AutoCowHatModule;
import me.mrai.larpclient.features.impl.kuudra.p1.tentdangerhorse.TentDangerHorseModule;
import me.mrai.larpclient.features.impl.skyblock.general.cgywardrobe.CgyWardrobeModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class AddonMinecraftScreenSuppressMixin {

    @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
    private void larpclient$suppressAddonScreen(Screen screen, CallbackInfo ci) {
        if (screen == null) return;

        String title = screen.getTitle().getString();

        boolean suppressWardrobe =
                CgyWardrobeModule.INSTANCE.getEnabled()
                        && CgyWardrobeModule.suppressWardrobeScreen
                        && CgyWardrobeModule.INSTANCE.isWardrobeTitle(title);

        boolean suppressCowHat =
                AutoCowHatModule.INSTANCE.getEnabled()
                        && AutoCowHatModule.suppressScreen
                        && AutoCowHatModule.INSTANCE.shouldSuppressScreen(title);

        boolean suppressTentHorse =
                TentDangerHorseModule.INSTANCE.getEnabled()
                        && TentDangerHorseModule.suppressPetsScreen
                        && TentDangerHorseModule.INSTANCE.shouldSuppressScreen(title);

        if (suppressWardrobe || suppressCowHat || suppressTentHorse) {
            ci.cancel();
        }
    }
}
