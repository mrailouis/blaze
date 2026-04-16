package me.mrai.larpclient.mixin;

import me.mrai.larpclient.playeroverride.PlayerScaleRenderStateAccess;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(LivingEntityRenderState.class)
public class LivingEntityRenderStateMixin implements PlayerScaleRenderStateAccess {
    @Unique
    private float larpclient$scaleX = 1.0f;
    @Unique
    private float larpclient$scaleY = 1.0f;
    @Unique
    private float larpclient$scaleZ = 1.0f;

    @Override
    public void larpclient$setPlayerScale(float scaleX, float scaleY, float scaleZ) {
        this.larpclient$scaleX = scaleX;
        this.larpclient$scaleY = scaleY;
        this.larpclient$scaleZ = scaleZ;
    }

    @Override
    public float larpclient$getPlayerScaleX() {
        return larpclient$scaleX;
    }

    @Override
    public float larpclient$getPlayerScaleY() {
        return larpclient$scaleY;
    }

    @Override
    public float larpclient$getPlayerScaleZ() {
        return larpclient$scaleZ;
    }
}
