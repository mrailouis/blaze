package me.mrai.larpclient.mixin;

import me.mrai.larpclient.features.impl.skyblock.general.storagegui.StorageGuiManager;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundContainerClosePacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public class ClientPacketListenerStorageGuiMixin {

    @Inject(method = "handleOpenScreen", at = @At("HEAD"))
    private void larpclient$captureStorageOpen(ClientboundOpenScreenPacket packet, CallbackInfo ci) {
        StorageGuiManager.INSTANCE.onOpenScreen(packet);
    }

    @Inject(method = "handleContainerContent", at = @At("TAIL"))
    private void larpclient$captureStorageContents(ClientboundContainerSetContentPacket packet, CallbackInfo ci) {
        StorageGuiManager.INSTANCE.onContainerContent(packet);
    }

    @Inject(method = "handleContainerSetSlot", at = @At("TAIL"))
    private void larpclient$captureStorageSlot(ClientboundContainerSetSlotPacket packet, CallbackInfo ci) {
        StorageGuiManager.INSTANCE.onContainerSlot(packet);
    }

    @Inject(method = "handleContainerClose", at = @At("HEAD"))
    private void larpclient$captureStorageClose(ClientboundContainerClosePacket packet, CallbackInfo ci) {
        StorageGuiManager.INSTANCE.onContainerClose(packet);
    }
}
