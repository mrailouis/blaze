package me.mrai.larpclient.mixin;

import me.mrai.larpclient.features.impl.dungeons.f7.p5.autocowhat.AutoCowHatModule;
import me.mrai.larpclient.features.impl.skyblock.general.cgywardrobe.CgyWardrobeModule;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public class WardrobeContainerContentCaptureMixin {

    @Inject(method = "handleContainerContent", at = @At("TAIL"), require = 0)
    private void larpclient$captureWardrobeContents(ClientboundContainerSetContentPacket packet, CallbackInfo ci) {
        if (CgyWardrobeModule.INSTANCE.getEnabled() && CgyWardrobeModule.suppressWardrobeScreen) {
            CgyWardrobeModule.INSTANCE.onWardrobeContents(
                    packet.containerId(),
                    packet.items().size()
            );
        }

        if (AutoCowHatModule.INSTANCE.getEnabled() && AutoCowHatModule.suppressScreen) {
            AutoCowHatModule.INSTANCE.onContainerContents(
                    packet.containerId(),
                    packet.items()
            );
        }
    }
}