package me.mrai.larpclient.mixin;

import com.mojang.text2speech.Narrator;
import me.mrai.larpclient.util.LarpLog;
import net.minecraft.client.GameNarrator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(GameNarrator.class)
public abstract class GameNarratorMixin {
    @Redirect(
        method = "<init>",
        at = @At(
            value = "INVOKE",
            target = "Lcom/mojang/text2speech/Narrator;getNarrator()Lcom/mojang/text2speech/Narrator;"
        )
    )
    private Narrator larpclient$fallbackToEmptyNarrator() {
        try {
            return Narrator.getNarrator();
        } catch (Throwable throwable) {
            LarpLog.INSTANCE.warn("Narrator library unavailable; continuing with Narrator.EMPTY.");
            LarpLog.INSTANCE.debug("Narrator init failure: " + throwable);
            return Narrator.EMPTY;
        }
    }
}
