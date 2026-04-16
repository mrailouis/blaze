package me.mrai.larpclient.mixin;

import me.mrai.larpclient.features.impl.skyblock.general.abilitycooldowns.AbilityCooldownsModule;
import me.mrai.larpclient.features.impl.kuudra.p1.kuudrawaypoints.KuudraWaypointModule;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public class TitlePacketCaptureMixin {

    @Inject(method = "setTitleText", at = @At("TAIL"))
    private void larpclient$captureTitle(ClientboundSetTitleTextPacket packet, CallbackInfo ci) {
        if (packet == null || packet.text() == null) return;
        KuudraWaypointModule.INSTANCE.onTitleMessage(packet.text().getString());
    }

    @Inject(method = "setSubtitleText", at = @At("TAIL"))
    private void larpclient$captureSubtitle(ClientboundSetSubtitleTextPacket packet, CallbackInfo ci) {
        if (packet == null || packet.text() == null) return;
        KuudraWaypointModule.INSTANCE.onTitleMessage(packet.text().getString());
    }

    @Inject(method = "setActionBarText", at = @At("TAIL"))
    private void larpclient$captureActionBar(ClientboundSetActionBarTextPacket packet, CallbackInfo ci) {
        if (packet == null || packet.text() == null) return;
        String message = packet.text().getString();
        KuudraWaypointModule.INSTANCE.onTitleMessage(message);
        AbilityCooldownsModule.INSTANCE.onActionBar(message);
    }
}
