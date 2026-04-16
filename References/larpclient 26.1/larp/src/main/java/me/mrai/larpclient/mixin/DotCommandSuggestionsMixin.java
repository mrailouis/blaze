package me.mrai.larpclient.mixin;

import me.mrai.larpclient.command.DotCommandRouter;
import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.gui.components.EditBox;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(CommandSuggestions.class)
public abstract class DotCommandSuggestionsMixin {

    @Redirect(
        method = "updateCommandInfo",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/components/EditBox;getValue()Ljava/lang/String;"
        )
    )
    private String larpclient$useSlashSuggestionsForDot(EditBox instance) {
        String value = instance.getValue();
        return DotCommandRouter.mapDotInputForSuggestions(value != null ? value : "");
    }
}
