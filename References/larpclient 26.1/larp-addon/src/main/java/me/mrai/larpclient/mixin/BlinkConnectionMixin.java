package me.mrai.larpclient.mixin;

import io.netty.channel.ChannelFutureListener;
import me.mrai.larpclient.features.impl.skyblock.general.blink.BlinkModule;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Connection.class)
public abstract class BlinkConnectionMixin {

    @Inject(
            method = "sendPacket(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;Z)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void larpclient$bufferBlinkPackets(Packet<?> packet, ChannelFutureListener listener, boolean flush, CallbackInfo ci) {
        Connection connection = (Connection) (Object) this;
        if (connection.getSending() != PacketFlow.SERVERBOUND) {
            return;
        }

        if (BlinkModule.INSTANCE.onSendPacket(packet)) {
            ci.cancel();
        }
    }
}
