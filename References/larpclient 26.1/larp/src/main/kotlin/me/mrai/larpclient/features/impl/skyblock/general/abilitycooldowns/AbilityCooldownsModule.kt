package me.mrai.larpclient.features.impl.skyblock.general.abilitycooldowns

import me.mrai.larpclient.module.BoolSetting
import me.mrai.larpclient.module.Module
import me.mrai.larpclient.module.ModuleCategory
import me.mrai.larpclient.util.TextSanitizer
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.core.component.DataComponents
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.component.CustomData
import net.minecraft.world.item.component.ItemLore
import kotlin.math.max

object AbilityCooldownsModule : Module(
    name = "Ability Cooldowns",
    description = "Shows cooldown timers and ready overlays for hotbar abilities.",
    category = ModuleCategory.SKYBLOCK_GENERAL
) {
    val cooldownBackground = BoolSetting("Ability Cooldown Background", true)
    val showWhenReady = BoolSetting("Show When Ready", true)

    private val loreCooldownRegex = Regex("""Cooldown:\s*(.+)""", RegexOption.IGNORE_CASE)
    private val secondsTokenRegex = Regex("""(\d+(?:\.\d+)?)\s*(s|sec|secs|second|seconds)""", RegexOption.IGNORE_CASE)
    private val minutesTokenRegex = Regex("""(\d+(?:\.\d+)?)\s*(m|min|mins|minute|minutes)""", RegexOption.IGNORE_CASE)

    private val activeCooldowns = linkedMapOf<String, CooldownEntry>()

    init {
        settings += listOf(cooldownBackground, showWhenReady)
    }

    override fun onDisable() {
        activeCooldowns.clear()
    }

    override fun onTick() {
        val iterator = activeCooldowns.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next().value
            entry.remainingTicks--
            if (entry.remainingTicks <= 0) {
                iterator.remove()
            }
        }
    }

    fun onItemUse(stack: ItemStack?) {
        if (!enabled) return
        if (Minecraft.getInstance().screen != null) return
        if (stack == null || stack.isEmpty) return
        val metadata = stack.cooldownMetadata() ?: return
        val existing = activeCooldowns[metadata.key]
        if (existing != null && existing.remainingTicks > 0) return

        activeCooldowns[metadata.key] = CooldownEntry(
            remainingTicks = metadata.cooldownTicks
        )
    }

    fun onActionBar(message: String) {
        // Intentionally unused for starting cooldowns now. Kept so existing packet hook stays harmless.
    }

    fun drawBackground(graphics: GuiGraphicsExtractor, stack: ItemStack, x: Int, y: Int) {
        if (!enabled || !cooldownBackground.value) return
        if (Minecraft.getInstance().screen != null) return

        val overlay = overlayFor(stack) ?: return
        val color = if (overlay.ready) 0xFF2F9E44.toInt() else 0xFFAA3333.toInt()
        val alpha = if (overlay.ready) 80 else 130
        drawColoredBackground(graphics, x, y, color, alpha)
    }

    fun drawText(graphics: GuiGraphicsExtractor, font: Font, stack: ItemStack, x: Int, y: Int) {
        if (!enabled) return
        if (Minecraft.getInstance().screen != null) return

        val overlay = overlayFor(stack) ?: return
        val width = font.width(overlay.text)
        val drawX = x + max(0, 17 - width)
        val drawY = y + 9
        val color = if (overlay.ready) 0xFF55FF55.toInt() else 0xFFFF5555.toInt()
        graphics.text(font, overlay.text, drawX, drawY, color, true)
    }

    private fun overlayFor(stack: ItemStack): OverlayState? {
        val metadata = stack.cooldownMetadata() ?: return null
        val active = activeCooldowns[metadata.key]
        if (active == null) {
            return if (showWhenReady.value) OverlayState("R", true) else null
        }

        val remainingTicks = active.remainingTicks
        if (remainingTicks <= 0) {
            activeCooldowns.remove(metadata.key)
            return if (showWhenReady.value) OverlayState("R", true) else null
        }

        return OverlayState(formatRemainingTicks(remainingTicks), false)
    }

    private fun ItemStack.cooldownMetadata(): CooldownMetadata? {
        if (isEmpty) return null

        val cooldownMs = parseCooldownFromLore(this) ?: return null
        val key = cooldownKey(this) ?: return null
        return CooldownMetadata(key, max(1, ((cooldownMs + 49L) / 50L).toInt()))
    }

    private fun parseCooldownFromLore(stack: ItemStack): Long? {
        val lore: ItemLore = stack.get(DataComponents.LORE) ?: return null
        val lines = lore.lines().map { TextSanitizer.stripFormatting(it.string).trim() }
        val cooldownLine = lines.firstOrNull { loreCooldownRegex.containsMatchIn(it) } ?: return null
        val raw = loreCooldownRegex.find(cooldownLine)?.groupValues?.getOrNull(1)?.trim().orEmpty()
        if (raw.isBlank()) return null

        val lower = raw.lowercase()
        if (lower == "ready" || lower == "available") return null

        var totalMs = 0L
        minutesTokenRegex.findAll(lower).forEach { match ->
            totalMs += ((match.groupValues[1].toDoubleOrNull() ?: 0.0) * 60_000.0).toLong()
        }
        secondsTokenRegex.findAll(lower).forEach { match ->
            totalMs += ((match.groupValues[1].toDoubleOrNull() ?: 0.0) * 1_000.0).toLong()
        }

        if (totalMs > 0L) return totalMs

        val numberOnly = lower.toDoubleOrNull()
        return if (numberOnly != null && numberOnly > 0.0) {
            (numberOnly * 1_000.0).toLong()
        } else {
            null
        }
    }

    private fun cooldownKey(stack: ItemStack): String? {
        val extra = stack.extraAttributes()
        val uuid = extra?.getString("uuid")?.orElse(null)
        if (!uuid.isNullOrBlank()) return "uuid:$uuid"

        val itemId = extra?.getString("id")?.orElse(null)
        if (!itemId.isNullOrBlank()) return "id:$itemId"

        val name = TextSanitizer.stripFormatting(stack.hoverName.string)
        if (name.isNotBlank()) return "name:$name"

        return null
    }

    private fun ItemStack.extraAttributes(): CompoundTag? {
        val customData: CustomData = get(DataComponents.CUSTOM_DATA) ?: return null
        return customData.copyTag().getCompound("ExtraAttributes").orElse(null)
    }

    private fun drawColoredBackground(graphics: GuiGraphicsExtractor, x: Int, y: Int, baseColor: Int, alpha: Int) {
        val base = baseColor and 0x00FFFFFF
        val outer = ((alpha * 0.65f).toInt()).coerceIn(0, 255)
        val mid1 = ((alpha * 0.78f).toInt()).coerceIn(0, 255)
        val mid2 = ((alpha * 0.90f).toInt()).coerceIn(0, 255)

        graphics.fill(x, y, x + 16, y + 16, base or (outer shl 24))
        graphics.fill(x + 1, y + 1, x + 15, y + 15, base or (mid1 shl 24))
        graphics.fill(x + 2, y + 2, x + 14, y + 14, base or (mid2 shl 24))
        graphics.fill(x + 3, y + 3, x + 13, y + 13, base or (alpha shl 24))
    }

    private fun formatRemainingTicks(remainingTicks: Int): String {
        val remainingMs = remainingTicks * 50L
        return if (remainingTicks < 32) {
            val tenths = max(1L, (remainingMs + 99L) / 100L)
            "${tenths / 10}.${tenths % 10}"
        } else {
            ((remainingMs + 999L) / 1_000L).toString()
        }
    }

    private data class CooldownMetadata(
        val key: String,
        val cooldownTicks: Int
    )

    private data class CooldownEntry(
        var remainingTicks: Int
    )

    private data class OverlayState(
        val text: String,
        val ready: Boolean
    )
}
