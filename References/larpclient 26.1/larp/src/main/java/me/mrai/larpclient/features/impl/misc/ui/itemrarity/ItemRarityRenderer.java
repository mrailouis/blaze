package me.mrai.larpclient.features.impl.misc.ui.itemrarity;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

public final class ItemRarityRenderer {
    public static final ItemRarityRenderer INSTANCE = new ItemRarityRenderer();
    private boolean initialized;

    private ItemRarityRenderer() {
    }

    public void init() {
        if (initialized) {
            return;
        }
        initialized = true;

        HudElementRegistry.attachElementBefore(
                VanillaHudElements.HOTBAR,
                Identifier.fromNamespaceAndPath("larpclient", "item_rarity_hotbar"),
                (guiGraphics, _) -> {
                    Minecraft mc = Minecraft.getInstance();
                    if (mc.player == null || !ItemRarityModule.INSTANCE.getEnabled() || !ItemRarityModule.INSTANCE.getRenderHotbar().getValue()) {
                        return;
                    }

                    for (int i = 0; i <= 8; i++) {
                        Rarity rarity = RarityDetector.INSTANCE.getRarity(mc.player.getInventory().getItem(i));
                        if (rarity == null) {
                            continue;
                        }

                        int x = mc.getWindow().getGuiScaledWidth() / 2 - 90 + i * 20 + 2;
                        int y = mc.getWindow().getGuiScaledHeight() - 18;
                        drawBackground(guiGraphics, x, y, rarity.getColor());
                    }
                }
        );
    }

    public static void drawItem(GuiGraphicsExtractor guiGraphics, ItemStack stack, int x, int y) {
        if (!ItemRarityModule.INSTANCE.getEnabled()) {
            return;
        }

        Rarity rarity = RarityDetector.INSTANCE.getRarity(stack);
        if (rarity == null) {
            return;
        }

        drawBackground(guiGraphics, x, y, rarity.getColor());
    }

    private static void drawBackground(GuiGraphicsExtractor guiGraphics, int x, int y, int baseColor) {
        int base = baseColor & 0x00FFFFFF;
        int alpha = (int) Math.max(0, Math.min(255, (ItemRarityModule.INSTANCE.getAlphaPercent().getValue() / 100.0) * 255.0));

        int outer = Math.max(0, Math.min(255, (int) (alpha * 0.65f)));
        int mid1 = Math.max(0, Math.min(255, (int) (alpha * 0.78f)));
        int mid2 = Math.max(0, Math.min(255, (int) (alpha * 0.90f)));

        guiGraphics.fill(x, y, x + 16, y + 16, base | (outer << 24));
        guiGraphics.fill(x + 1, y + 1, x + 15, y + 15, base | (mid1 << 24));
        guiGraphics.fill(x + 2, y + 2, x + 14, y + 14, base | (mid2 << 24));
        guiGraphics.fill(x + 3, y + 3, x + 13, y + 13, base | (alpha << 24));
    }
}
