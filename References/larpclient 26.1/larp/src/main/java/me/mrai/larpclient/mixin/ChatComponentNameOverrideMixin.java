package me.mrai.larpclient.mixin;

import me.mrai.larpclient.playeroverride.PlayerOverrideManager;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessageSource;
import net.minecraft.client.multiplayer.chat.GuiMessageTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatComponent.class)
public abstract class ChatComponentNameOverrideMixin {
    @Unique
    private static final ThreadLocal<Boolean> larpclient$rewriteGuard = ThreadLocal.withInitial(() -> false);

    @Shadow
    private void addMessage(Component content, MessageSignature signature, GuiMessageSource source, GuiMessageTag tag) {
    }

    @Inject(
        method = "addMessage",
        at = @At("HEAD"),
        cancellable = true
    )
    private void larpclient$rewriteChatMessage(
        Component content,
        MessageSignature signature,
        GuiMessageSource source,
        GuiMessageTag tag,
        CallbackInfo ci
    ) {
        if (larpclient$rewriteGuard.get()) {
            return;
        }

        Component rewritten = PlayerOverrideManager.rewriteComponent(content);
        if (rewritten == content) {
            return;
        }

        larpclient$rewriteGuard.set(true);
        try {
            addMessage(rewritten, signature, source, tag);
        } finally {
            larpclient$rewriteGuard.set(false);
        }

        ci.cancel();
    }
}
