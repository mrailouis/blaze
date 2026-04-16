package me.mrai.larpclient.mixin;

import me.mrai.larpclient.features.impl.skyblock.general.blink.BlinkModule;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class BlinkCameraMixin {

    @Invoker("setPosition")
    protected abstract void larpclient$setPosition(Vec3 pos);

    @Invoker("setRotation")
    protected abstract void larpclient$setRotation(float yaw, float pitch);

    @Inject(method = "update", at = @At("TAIL"))
    private void larpclient$overrideBlinkCamera(DeltaTracker deltaTracker, CallbackInfo ci) {
        Vec3 pos = BlinkModule.INSTANCE.detachedCameraPosition();
        if (pos == null) {
            return;
        }
        larpclient$setPosition(pos);
        Float yaw = BlinkModule.INSTANCE.detachedCameraYaw();
        Float pitch = BlinkModule.INSTANCE.detachedCameraPitch();
        if (yaw != null && pitch != null) {
            larpclient$setRotation(yaw, pitch);
        }
    }
}
