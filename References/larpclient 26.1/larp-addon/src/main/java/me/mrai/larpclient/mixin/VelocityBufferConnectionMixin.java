package me.mrai.larpclient.mixin;

import io.netty.channel.ChannelHandlerContext;
import me.mrai.larpclient.features.impl.dungeons.general.velocitybuffer.VelocityBufferModule;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Connection.class)
public abstract class VelocityBufferConnectionMixin {

    @Inject(
            method = "channelRead0(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/protocol/Packet;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void larpclient$bufferVelocityPackets(ChannelHandlerContext context, Packet<?> packet, CallbackInfo ci) {
        if (VelocityBufferModule.INSTANCE.onReceivePacketPre(packet)) {
            ci.cancel();
        }
    }
}
