package me.mrai.larpclient.mixin;

import me.mrai.larpclient.features.impl.dungeons.general.truesplits.TrueSplitsState;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SoundManager.class)
public class TrueSplitsSoundMixin {

    @Inject(method = "play(Lnet/minecraft/client/resources/sounds/SoundInstance;)V", at = @At("HEAD"), require = 0)
    private void larpclient$onPlay(SoundInstance sound, CallbackInfo ci) {
        larpclient$handleSound(sound);
    }

    @Inject(method = "play(Lnet/minecraft/client/resources/sounds/SoundInstance;I)V", at = @At("HEAD"), require = 0)
    private void larpclient$onPlayDelayed(SoundInstance sound, int delay, CallbackInfo ci) {
        larpclient$handleSound(sound);
    }

    private void larpclient$handleSound(SoundInstance sound) {
        if (sound == null) return;

        Identifier id = sound.getIdentifier();
        if (id == null) return;

        String path = id.getPath();

        if ("mob.enderdragon.hit".equals(path)
                || "entity.ender_dragon.hurt".equals(path)
                || "entity.ender_dragon.death".equals(path)) {
            TrueSplitsState.INSTANCE.onWitherDeathSound();
        }

        if ("random.explode".equals(path)
                || "entity.generic.explode".equals(path)) {
            TrueSplitsState.INSTANCE.onMaxorLaserCharged();
        }
    }
}