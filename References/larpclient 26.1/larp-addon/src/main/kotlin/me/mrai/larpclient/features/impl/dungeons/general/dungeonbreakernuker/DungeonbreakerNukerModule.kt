package me.mrai.larpclient.features.impl.dungeons.general.dungeonbreakernuker

import com.google.gson.GsonBuilder
import com.mojang.blaze3d.platform.InputConstants
import me.mrai.larpclient.module.BoolSetting
import me.mrai.larpclient.module.KeybindSetting
import me.mrai.larpclient.module.ModeSetting
import me.mrai.larpclient.module.Module
import me.mrai.larpclient.module.ModuleCategory
import me.mrai.larpclient.module.SliderSetting
import me.mrai.larpclient.render.WorldBoxRenderer
import me.mrai.larpclient.util.LarpLog
import me.mrai.larpclient.util.TextSanitizer
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.ClipContext
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import java.nio.file.Files
import java.nio.file.Path
import kotlin.math.sin
import kotlin.math.sqrt

object DungeonbreakerNukerModule : Module(
    name = "Dungeonbreaker Nuker",
    description = "Captures dungeon blocks and breaks them in range using a hotbar item named dungeonbreaker. Auto 3x3 can target the fixed arrow stack area by default.",
    category = ModuleCategory.DUNGEONS_GENERAL
) {
    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val configPath: Path =
        FabricLoader.getInstance().configDir.resolve("larpclient/dungeonbreaker_nuker_blocks.json")

    private val addKey = KeybindSetting("Add Key")
    private val removeKey = KeybindSetting("Remove Key")
    private val renderStyle = ModeSetting("Render Style", listOf("Outline", "Filled", "Both"), "Both")
    private val fillRed = SliderSetting("Fill Red", 255.0, 0.0, 255.0, 1.0)
    private val fillGreen = SliderSetting("Fill Green", 80.0, 0.0, 255.0, 1.0)
    private val fillBlue = SliderSetting("Fill Blue", 80.0, 0.0, 255.0, 1.0)
    private val fillAlpha = SliderSetting("Fill Alpha", 70.0, 0.0, 255.0, 1.0)
    private val outlineRed = SliderSetting("Outline Red", 255.0, 0.0, 255.0, 1.0)
    private val outlineGreen = SliderSetting("Outline Green", 255.0, 0.0, 255.0, 1.0)
    private val outlineBlue = SliderSetting("Outline Blue", 255.0, 0.0, 255.0, 1.0)
    private val auto3x3 = BoolSetting("Auto 3x3", false)
    private val autoGold = BoolSetting("Auto Gold", false)

    private val auto3x3Targets = linkedSetOf<BlockPos>().apply {
        for (x in 53..55) {
            for (z in 113..115) {
                add(BlockPos(x, 63, z))
            }
        }
    }
    private val autoGoldTargets = linkedSetOf<BlockPos>().apply {
        for (x in 51..57) {
            for (z in 111..117) {
                add(BlockPos(x, 113, z))
            }
        }
    }

    private val selectedBlocks = linkedSetOf<BlockPos>()
    private var currentBreakTarget: BlockPos? = null
    private var addWasPressed = false
    private var removeWasPressed = false
    private var latchedDungeonbreakerSlot: Int? = null
    private var returnSlot: Int? = null

    private const val BREAK_RANGE = 4.5
    private const val SELECT_RANGE = 6.0

    private data class ManualTargetConfig(
        var blocks: MutableList<BlockPosEntry> = mutableListOf()
    )

    private data class BlockPosEntry(
        var x: Int,
        var y: Int,
        var z: Int
    )

    init {
        settings += listOf(
            addKey,
            removeKey,
            renderStyle,
            fillRed, fillGreen, fillBlue, fillAlpha,
            outlineRed, outlineGreen, outlineBlue,
            auto3x3,
            autoGold
        )
        loadManualTargets()
    }

    override fun onDisable() {
        stopBreaking()
        releaseDungeonbreakerSlot()
    }

    override fun onTick() {
        handleSelectionKeys()
        if (!enabled) return

        val client = Minecraft.getInstance()
        val player = client.player ?: return
        val level = client.level ?: return
        val gameMode = client.gameMode ?: return
        if (client.screen != null) return

        val targets = currentTargets(level).filter { !level.getBlockState(it).isAir }
        val target = targets
            .filter { isWithinBreakRange(player.eyePosition.distanceToSqr(centerOf(it))) }
            .minByOrNull { player.eyePosition.distanceToSqr(centerOf(it)) }

        if (target == null) {
            stopBreaking()
            releaseDungeonbreakerSlot()
            return
        }

        val slot = findDungeonbreakerSlot(player)
        if (slot == -1) {
            stopBreaking()
            releaseDungeonbreakerSlot()
            return
        }
        if (!ensureDungeonbreakerHeld(player, slot)) {
            stopBreaking()
            return
        }

        if (currentBreakTarget != target) {
            gameMode.stopDestroyBlock()
            if (gameMode.startDestroyBlock(target, Direction.UP)) {
                player.swing(net.minecraft.world.InteractionHand.MAIN_HAND)
            }
            currentBreakTarget = target
        } else {
            if (gameMode.continueDestroyBlock(target, Direction.UP)) {
                player.swing(net.minecraft.world.InteractionHand.MAIN_HAND)
            }
        }
    }

    fun render(context: LevelRenderContext) {
        if (!enabled) return

        val boxes = mutableListOf<Pair<AABB, WorldBoxRenderer.BoxStyle>>()

        if (auto3x3.value) {
            boxes += auto3x3Targets.mapIndexed { index, pos ->
                AABB(pos).inflate(0.002) to rainbowStyle(index, auto3x3Targets.size)
            }
        }
        if (autoGold.value) {
            val visibleGoldTargets = autoGoldTargets.filter { isAutoGoldBlock(level = Minecraft.getInstance().level, pos = it) }
            boxes += visibleGoldTargets.mapIndexed { index, pos ->
                AABB(pos).inflate(0.002) to rainbowStyle(index, visibleGoldTargets.size, 0.33f)
            }
        }

        val manualTargets = selectedBlocks.toList()
        if (manualTargets.isNotEmpty()) {
            val fillAlphaValue = when (renderStyle.selected) {
                "Outline" -> 0f
                else -> (fillAlpha.value / 255.0).toFloat()
            }
            val outlineAlphaValue = if (renderStyle.selected == "Filled") 0f else 1.0f

            val style = WorldBoxRenderer.BoxStyle(
                fillRed = (fillRed.value / 255.0).toFloat(),
                fillGreen = (fillGreen.value / 255.0).toFloat(),
                fillBlue = (fillBlue.value / 255.0).toFloat(),
                fillAlpha = fillAlphaValue,
                outlineRed = (outlineRed.value / 255.0).toFloat(),
                outlineGreen = (outlineGreen.value / 255.0).toFloat(),
                outlineBlue = (outlineBlue.value / 255.0).toFloat(),
                outlineAlpha = outlineAlphaValue,
                outlineThickness = 0.035f
            )
            boxes += manualTargets.map { AABB(it).inflate(0.002) to style }
        }

        if (boxes.isNotEmpty()) {
            WorldBoxRenderer.render(context, boxes)
        }
    }

    private fun handleSelectionKeys() {
        val client = Minecraft.getInstance()
        val lookedPos = resolveLookedBlock(client)

        val addPressed = addKey.key != InputConstants.UNKNOWN.getValue() &&
            InputConstants.isKeyDown(client.window, addKey.key)
        if (addPressed && !addWasPressed) {
            lookedPos?.let {
                selectedBlocks += it
                saveManualTargets()
            }
        }
        addWasPressed = addPressed

        val removePressed = removeKey.key != InputConstants.UNKNOWN.getValue() &&
            InputConstants.isKeyDown(client.window, removeKey.key)
        if (removePressed && !removeWasPressed) {
            if (lookedPos != null) {
                if (selectedBlocks.removeIf { it == lookedPos }) {
                    saveManualTargets()
                }
            }
            if (selectedBlocks.isEmpty() && !auto3x3.value) {
                currentBreakTarget = null
            }
        }
        removeWasPressed = removePressed
    }

    private fun resolveLookedBlock(client: Minecraft): BlockPos? {
        val player = client.player ?: return null
        val level = client.level ?: return null
        val start = client.gameRenderer.mainCamera.position()
        val look = player.getViewVector(1.0f)
        val end = start.add(
            look.x * SELECT_RANGE,
            look.y * SELECT_RANGE,
            look.z * SELECT_RANGE
        )
        val hit = level.clip(
            ClipContext(
                start,
                end,
                ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE,
                player
            )
        )
        if (hit.type != HitResult.Type.BLOCK) return null
        val pos = hit.blockPos
        return pos.takeIf { !level.getBlockState(it).isAir }
    }

    private fun currentTargets(level: net.minecraft.client.multiplayer.ClientLevel): LinkedHashSet<BlockPos> {
        val targets = linkedSetOf<BlockPos>()
        if (auto3x3.value) {
            targets += auto3x3Targets
        }
        if (autoGold.value) {
            targets += autoGoldTargets.filter { isAutoGoldBlock(level, it) }
        }
        targets += selectedBlocks
        return targets
    }

    private fun isAutoGoldBlock(level: net.minecraft.client.multiplayer.ClientLevel?, pos: BlockPos): Boolean {
        return level != null && level.getBlockState(pos).block == Blocks.GOLD_BLOCK
    }

    private fun rainbowStyle(index: Int, size: Int, fillAlpha: Float = 0.33f): WorldBoxRenderer.BoxStyle {
        val time = System.currentTimeMillis() / 900.0
        val phase = (index.toDouble() / size.coerceAtLeast(1).toDouble()) * Math.PI * 2.0
        val red = ((sin(time + phase) + 1.0) * 0.5).toFloat()
        val green = ((sin(time + phase + (Math.PI * 2.0 / 3.0)) + 1.0) * 0.5).toFloat()
        val blue = ((sin(time + phase + (Math.PI * 4.0 / 3.0)) + 1.0) * 0.5).toFloat()
        return WorldBoxRenderer.BoxStyle(
            fillRed = red,
            fillGreen = green,
            fillBlue = blue,
            fillAlpha = fillAlpha,
            outlineRed = red,
            outlineGreen = green,
            outlineBlue = blue,
            outlineAlpha = 1.0f,
            outlineThickness = 0.035f
        )
    }

    private fun findDungeonbreakerSlot(player: net.minecraft.client.player.LocalPlayer): Int {
        for (slot in 0 until 9) {
            val stack = player.inventory.getItem(slot)
            if (stack.isEmpty) continue
            val name = TextSanitizer.stripFormatting(stack.displayName.string).lowercase()
            if (name.contains("dungeonbreaker")) {
                return slot
            }
        }
        return -1
    }

    private fun centerOf(pos: BlockPos) = Vec3(
        pos.x + 0.5,
        pos.y + 0.5,
        pos.z + 0.5
    )

    private fun isWithinBreakRange(distanceSq: Double): Boolean {
        return sqrt(distanceSq) <= BREAK_RANGE
    }

    private fun ensureDungeonbreakerHeld(player: net.minecraft.client.player.LocalPlayer, slot: Int): Boolean {
        if (latchedDungeonbreakerSlot == null) {
            latchedDungeonbreakerSlot = slot
            returnSlot = player.inventory.selectedSlot.takeIf { it != slot }
        }

        if (player.inventory.selectedSlot == slot) {
            return true
        }

        player.inventory.selectedSlot = slot
        Minecraft.getInstance().connection?.send(ServerboundSetCarriedItemPacket(slot))
        return false
    }

    private fun releaseDungeonbreakerSlot() {
        val player = Minecraft.getInstance().player ?: run {
            latchedDungeonbreakerSlot = null
            returnSlot = null
            return
        }
        val restoreSlot = returnSlot
        latchedDungeonbreakerSlot = null
        returnSlot = null

        if (restoreSlot != null && player.inventory.selectedSlot != restoreSlot) {
            player.inventory.selectedSlot = restoreSlot
            Minecraft.getInstance().connection?.send(ServerboundSetCarriedItemPacket(restoreSlot))
        }
    }

    private fun stopBreaking() {
        currentBreakTarget = null
        Minecraft.getInstance().gameMode?.stopDestroyBlock()
    }

    private fun loadManualTargets() {
        try {
            Files.createDirectories(configPath.parent)
            if (!Files.exists(configPath)) {
                saveManualTargets()
                return
            }

            val config = gson.fromJson(Files.readString(configPath), ManualTargetConfig::class.java) ?: ManualTargetConfig()
            selectedBlocks.clear()
            config.blocks.forEach { selectedBlocks += BlockPos(it.x, it.y, it.z) }
        } catch (throwable: Throwable) {
            LarpLog.warn("Failed to load dungeonbreaker targets from $configPath: ${throwable.message ?: throwable.javaClass.simpleName}")
            selectedBlocks.clear()
        }
    }

    private fun saveManualTargets() {
        try {
            Files.createDirectories(configPath.parent)
            val config = ManualTargetConfig(
                selectedBlocks.map { BlockPosEntry(it.x, it.y, it.z) }.toMutableList()
            )
            Files.writeString(configPath, gson.toJson(config))
        } catch (throwable: Throwable) {
            LarpLog.warn("Failed to save dungeonbreaker targets to $configPath: ${throwable.message ?: throwable.javaClass.simpleName}")
        }
    }
}
