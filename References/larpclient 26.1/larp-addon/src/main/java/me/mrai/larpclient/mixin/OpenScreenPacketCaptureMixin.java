package me.mrai.larpclient.mixin;

import me.mrai.larpclient.features.impl.dungeons.f7.p5.autocowhat.AutoCowHatModule;
import me.mrai.larpclient.features.impl.kuudra.p1.tentdangerhorse.TentDangerHorseModule;
import me.mrai.larpclient.features.impl.skyblock.general.cgywardrobe.CgyWardrobeModule;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public class OpenScreenPacketCaptureMixin {

    @Inject(
            method = "handleOpenScreen(Lnet/minecraft/network/protocol/game/ClientboundOpenScreenPacket;)V",
            at = @At("HEAD"),
            require = 0
    )
    private void larpclient$captureOpenScreen(ClientboundOpenScreenPacket packet, CallbackInfo ci) {
        String title = packet.getTitle().getString();

        if (CgyWardrobeModule.INSTANCE.getEnabled()) {
            CgyWardrobeModule.INSTANCE.onOpenScreenCaptured(
                    packet.getContainerId(),
                    title
            );
        }

        if (AutoCowHatModule.INSTANCE.getEnabled()) {
            AutoCowHatModule.INSTANCE.onOpenScreenCaptured(
                    packet.getContainerId(),
                    title
            );
        }

        if (TentDangerHorseModule.INSTANCE.getEnabled()) {
            TentDangerHorseModule.INSTANCE.onOpenScreenCaptured(
                    packet.getContainerId(),
                    title
            );
        }
    }
}
