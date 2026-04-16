package me.mrai.larpclient.mixin;

import io.netty.channel.ChannelHandlerContext;
import me.mrai.larpclient.features.impl.skyblock.general.performancehud.PerformanceStatsTracker;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
import net.minecraft.network.protocol.ping.ClientboundPongResponsePacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Connection.class)
public abstract class PerformanceConnectionMixin {

    @Inject(
            method = "channelRead0(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/protocol/Packet;)V",
            at = @At("HEAD")
    )
    private void larpclient$trackPerformancePackets(ChannelHandlerContext context, Packet<?> packet, CallbackInfo ci) {
        if (packet instanceof ClientboundSetTimePacket) {
            PerformanceStatsTracker.INSTANCE.onTimePacketReceived();
            return;
        }

        if (packet instanceof ClientboundPongResponsePacket pongPacket) {
            PerformanceStatsTracker.INSTANCE.onPongResponse(pongPacket.time());
        }
    }
}
