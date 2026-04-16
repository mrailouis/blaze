package me.mrai.larpclient.mixin;

import me.mrai.larpclient.features.impl.skyblock.general.visualfme.VisualFmeState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.world.LevelSlice", remap = false)
public class SodiumLevelSliceMixin {

    @Dynamic("Sodium class is not on compile classpath")
    @Inject(method = "getBlockState", at = @At("RETURN"), cancellable = true, remap = false, require = 0)
    private void larpclient$replaceRenderedBlockState(int x, int y, int z, CallbackInfoReturnable<BlockState> cir) {
        BlockPos pos = new BlockPos(x, y, z);
        BlockState original = cir.getReturnValue();
        BlockState replacement = VisualFmeState.INSTANCE.getReplacementState(pos);

        if (replacement == null) return;
        if (replacement == original) return;

        cir.setReturnValue(replacement);
    }
}
