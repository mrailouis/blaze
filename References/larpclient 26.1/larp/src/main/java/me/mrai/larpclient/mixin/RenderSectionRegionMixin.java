package me.mrai.larpclient.mixin;

import me.mrai.larpclient.features.impl.skyblock.general.visualfme.VisualFmeState;
import net.minecraft.client.renderer.chunk.RenderSectionRegion;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(RenderSectionRegion.class)
public class RenderSectionRegionMixin {

    @Inject(method = "getBlockState", at = @At("RETURN"), cancellable = true)
    private void larpclient$replaceRenderedBlockState(BlockPos pos, CallbackInfoReturnable<BlockState> cir) {
        BlockState original = cir.getReturnValue();
        BlockState replacement = VisualFmeState.INSTANCE.getReplacementState(pos);

        if (replacement == null) return;
        if (replacement == original) return;

        cir.setReturnValue(replacement);
    }
}
