package me.mrai.larpclient.mixin;

import me.mrai.larpclient.features.impl.dungeons.f7.p3.autoss.AutoSSModule;
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
public class AddonSystemChatCaptureMixin {

    @Inject(method = "handleSystemChat", at = @At("TAIL"))
    private void larpclient$captureAddonSystemChat(ClientboundSystemChatPacket packet, CallbackInfo ci) {
        if (packet == null || packet.content() == null) return;
        AutoSSModule.INSTANCE.onChatMessage(packet.content().getString());
    }

    @Inject(method = "handlePlayerChat", at = @At("TAIL"))
    private void larpclient$captureAddonPlayerChat(ClientboundPlayerChatPacket packet, CallbackInfo ci) {
        if (packet == null) return;

        Component content = packet.unsignedContent();
        if (content == null) {
            content = Component.literal(packet.body().content());
        }

        AutoSSModule.INSTANCE.onChatMessage(packet.chatType().decorate(content).getString());
    }

    @Inject(method = "handleDisguisedChat", at = @At("TAIL"))
    private void larpclient$captureAddonDisguisedChat(ClientboundDisguisedChatPacket packet, CallbackInfo ci) {
        if (packet == null || packet.message() == null) return;
        AutoSSModule.INSTANCE.onChatMessage(packet.chatType().decorate(packet.message()).getString());
    }
}
