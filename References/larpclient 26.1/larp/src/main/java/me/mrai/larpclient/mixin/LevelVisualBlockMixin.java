package me.mrai.larpclient.mixin;

import me.mrai.larpclient.features.impl.skyblock.general.visualfme.VisualFmeState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Level.class)
public class LevelVisualBlockMixin {

    @Inject(method = "getBlockState", at = @At("RETURN"), cancellable = true)
    private void larpclient$overrideVisualBlockState(BlockPos pos, CallbackInfoReturnable<BlockState> cir) {
        BlockState replacement = VisualFmeState.INSTANCE.getWorldLookupOverride(pos);
        if (replacement == null) return;
        cir.setReturnValue(replacement);
    }
}
