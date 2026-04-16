package me.mrai.larpclient.playeroverride;

public interface PlayerScaleRenderStateAccess {
    void larpclient$setPlayerScale(float scaleX, float scaleY, float scaleZ);

    float larpclient$getPlayerScaleX();

    float larpclient$getPlayerScaleY();

    float larpclient$getPlayerScaleZ();
}
