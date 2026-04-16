package me.mrai.larpclient.mixin;

import me.mrai.larpclient.features.impl.dungeons.f7.p5.autocowhat.AutoCowHatModule;
import me.mrai.larpclient.features.impl.kuudra.p1.tentdangerhorse.TentDangerHorseModule;
import me.mrai.larpclient.features.impl.skyblock.general.cgywardrobe.CgyWardrobeModule;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public class WardrobeOpenScreenCaptureMixin {

    @Inject(method = "handleOpenScreen", at = @At("TAIL"), require = 0)
    private void larpclient$captureWardrobeOpen(ClientboundOpenScreenPacket packet, CallbackInfo ci) {
        String title = packet.getTitle().getString();

        CgyWardrobeModule.INSTANCE.onOpenScreenCaptured(
                packet.getContainerId(),
                title
        );

        AutoCowHatModule.INSTANCE.onOpenScreenCaptured(
                packet.getContainerId(),
                title
        );

        TentDangerHorseModule.INSTANCE.onOpenScreenCaptured(
                packet.getContainerId(),
                title
        );

        boolean shouldSuppressWardrobe =
                CgyWardrobeModule.INSTANCE.getEnabled()
                        && CgyWardrobeModule.suppressWardrobeScreen
                        && CgyWardrobeModule.INSTANCE.isWardrobeTitle(title);

        boolean shouldSuppressCowHat =
                AutoCowHatModule.INSTANCE.getEnabled()
                        && AutoCowHatModule.suppressScreen
                        && AutoCowHatModule.INSTANCE.shouldSuppressScreen(title);

        boolean shouldSuppressTentHorse =
                TentDangerHorseModule.INSTANCE.getEnabled()
                        && TentDangerHorseModule.suppressPetsScreen
                        && TentDangerHorseModule.INSTANCE.shouldSuppressScreen(title);

        if (!shouldSuppressWardrobe && !shouldSuppressCowHat && !shouldSuppressTentHorse) return;

        Minecraft client = Minecraft.getInstance();
        MinecraftAccessor accessor = (MinecraftAccessor) client;

        Screen current = accessor.getScreenField();
        if (current != null) {
            accessor.setScreenField(null);
            client.textInputManager().stopTextInput();
            if (client.level != null) {
                KeyMapping.restoreToggleStatesOnScreenClosed();
            }
            client.getSoundManager().resume();
            client.mouseHandler.grabMouse();
            client.updateTitle();
        }
    }
}
