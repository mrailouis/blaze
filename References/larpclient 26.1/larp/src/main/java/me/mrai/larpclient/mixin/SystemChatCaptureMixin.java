package me.mrai.larpclient.mixin;

import me.mrai.larpclient.features.impl.kuudra.p1.kuudrawaypoints.KuudraWaypointModule;
import me.mrai.larpclient.features.impl.kuudra.general.blockpickobulus.BlockPickobulusModule;
import me.mrai.larpclient.features.impl.kuudra.p3.stunwaypoint.StunWaypointModule;
import me.mrai.larpclient.features.impl.dungeons.f7.p5.cowhat.CowHatModule;
import me.mrai.larpclient.features.impl.skyblock.golems.GolemTrackerState;
import me.mrai.larpclient.features.impl.skyblock.general.serverlagdetection.ServerLagDetectionModule;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundDisguisedChatPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerChatPacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public class SystemChatCaptureMixin {

        @Inject(method = "handleSystemChat", at = @At("TAIL"))
    private void larpclient$captureSystemChat(ClientboundSystemChatPacket packet, CallbackInfo ci) {
        if (packet == null || packet.content() == null) return;
        String message = packet.content().getString();
        ServerLagDetectionModule.INSTANCE.onSystemChat(message);
        KuudraWaypointModule.INSTANCE.onChatMessage(message);
        BlockPickobulusModule.INSTANCE.onChatMessage(message);
        StunWaypointModule.INSTANCE.onChatMessage(message);
        CowHatModule.INSTANCE.onChatMessage(message);
        GolemTrackerState.INSTANCE.onChatMessage(message);
    }

    @Inject(method = "handlePlayerChat", at = @At("TAIL"))
    private void larpclient$capturePlayerChat(ClientboundPlayerChatPacket packet, CallbackInfo ci) {
        if (packet == null) return;

        Component content = packet.unsignedContent();
        if (content == null) {
            content = Component.literal(packet.body().content());
        }

        String message = packet.chatType().decorate(content).getString();
        KuudraWaypointModule.INSTANCE.onChatMessage(message);
        BlockPickobulusModule.INSTANCE.onChatMessage(message);
        StunWaypointModule.INSTANCE.onChatMessage(message);
        CowHatModule.INSTANCE.onChatMessage(message);
        GolemTrackerState.INSTANCE.onChatMessage(message);
    }

    @Inject(method = "handleDisguisedChat", at = @At("TAIL"))
    private void larpclient$captureDisguisedChat(ClientboundDisguisedChatPacket packet, CallbackInfo ci) {
        if (packet == null || packet.message() == null) return;
        String message = packet.chatType().decorate(packet.message()).getString();
        KuudraWaypointModule.INSTANCE.onChatMessage(message);
        BlockPickobulusModule.INSTANCE.onChatMessage(message);
        StunWaypointModule.INSTANCE.onChatMessage(message);
        CowHatModule.INSTANCE.onChatMessage(message);
        GolemTrackerState.INSTANCE.onChatMessage(message);
    }
}
