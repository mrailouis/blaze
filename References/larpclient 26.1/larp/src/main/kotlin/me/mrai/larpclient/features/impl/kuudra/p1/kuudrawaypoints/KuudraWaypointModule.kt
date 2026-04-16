package me.mrai.larpclient.features.impl.kuudra.p1.kuudrawaypoints

import com.mojang.blaze3d.buffers.GpuBuffer
import com.mojang.blaze3d.pipeline.RenderPipeline
import com.mojang.blaze3d.systems.CommandEncoder
import com.mojang.blaze3d.systems.RenderPass
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.BufferBuilder
import com.mojang.blaze3d.vertex.ByteBufferBuilder
import com.mojang.blaze3d.vertex.MeshData
import com.mojang.blaze3d.vertex.VertexFormat
import me.mrai.larpclient.module.BoolSetting
import me.mrai.larpclient.module.ModeSetting
import me.mrai.larpclient.module.Module
import me.mrai.larpclient.module.ModuleCategory
import me.mrai.larpclient.module.SliderSetting
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.player.LocalPlayer
import net.minecraft.client.renderer.MappableRingBuffer
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.world.entity.projectile.ProjectileUtil
import net.minecraft.world.level.ClipContext
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import org.joml.Matrix4f
import org.joml.Matrix4fc
import org.joml.Vector3f
import org.joml.Vector4f
import org.lwjgl.system.MemoryUtil
import java.nio.ByteBuffer
import java.util.ArrayDeque
import java.util.OptionalDouble
import java.util.OptionalInt
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

object KuudraWaypointModule : Module(
    name = "Kuudra Waypoints",
    description = "IQ-like Kuudra pearl helper with dynamic sky/flat calculations.",
    category = ModuleCategory.KUUDRA_P1
) {
    data class PriorityHudSnapshot(val title: String, val preLine: String, val goToLine: String)
    data class SpawnedCrateHudEntry(val label: String, val spawned: Boolean)
    data class SpawnedCratesHudSnapshot(val entries: List<SpawnedCrateHudEntry>)
    data class AutoPearlHint(
        val key: String,
        val yaw: Float,
        val pitch: Float,
        val throwAtMs: Long,
        val travelMs: Long,
        val group: String,
        val label: String
    )

    private const val THROW_SPEED = 1.5
    private const val DRAG = 0.99
    private const val GRAVITY = 0.03
    private const val MAX_SIM_TICKS = 240
    private const val RECOMPUTE_INTERVAL = 8L
    private const val PLAYER_MOVE_RECOMPUTE_DISTANCE = 0.30
    private const val AIM_MARKER_DISTANCE = 22.0
    private const val PEARL_RADIUS = 0.125
    private const val PILE_HIGHLIGHT_RADIUS = 2.5

    private const val SUPPLY_STEP_TIME_MS = 300L
    private const val TIMER_UPDATE_STEP_MS = 5L
    private const val READY_RESET_TIMEOUT_MS = 900L

    private val SUPPLY_TICK_PERCENTAGES = listOf(5, 11, 17, 23, 29, 35, 41, 47, 53, 59, 65, 71, 77, 83, 89, 95, 100)
    private val SUPPLY_PROGRESS_REGEX = Regex(""".*?(\d+)%.*""")
    private val NO_WORD_REGEX = Regex("""\bno\b""", RegexOption.IGNORE_CASE)

    private const val ELLE_START_MESSAGE = "[NPC] Elle: Okay adventurers, I will go and fish up Kuudra!"
    private const val ELLE_HEAD_OVER_MESSAGE = "[NPC] Elle: Head over to the main platform"
    private const val ELLE_NOT_AGAIN_MESSAGE = "[NPC] Elle: Not again!"
    private const val ELLE_END_MESSAGE = "[NPC] Elle: OMG! Great work collecting my supplies!"
    private const val SUPPLY_PICKUP_MESSAGE = "You retrieved some of Elle's supplies from the Lava!"
    private const val SUPPLY_SLIP_MESSAGE = "You moved and the Chest slipped out of your hands!"

    private val forceKuudra = BoolSetting("Force Kuudra", false)
    private val showPileSpots = BoolSetting("Show Pile Spots", true)
    private val showFlatPearls = BoolSetting("Flat Pearls", true)
    private val showSkyPearls = BoolSetting("Sky Pearls", true)
    private val prePearlDelayMs = SliderSetting("Pre Pearl Delay", 400.0, 0.0, 2000.0, 50.0)
    private val xFlatXcPrePearl = BoolSetting("X Flat: XC", false)
    private val xFlatSquarePrePearl = BoolSetting("X Flat: Square", false)
    private val xSkyXcPrePearl = BoolSetting("X Sky: XC", false)
    private val xSkySquarePrePearl = BoolSetting("X Sky: Square", false)
    private val slashFlatSquarePrePearl = BoolSetting("/ Flat: Square", false)
    private val slashSkySquarePrePearl = BoolSetting("/ Sky: Square", false)
    private val equalsFlatShopPrePearl = BoolSetting("= Flat: Shop", false)
    private val equalsFlatXcPrePearl = BoolSetting("= Flat: XC", false)
    private val equalsSkyShopPrePearl = BoolSetting("= Sky: Shop", false)
    private val equalsSkyXcPrePearl = BoolSetting("= Sky: XC", false)
    private val showFastestSecondaryOnly = BoolSetting("Fastest Only On Secondary", false)
    private val showPriority = BoolSetting("Show Priority", true)
    private val showSpawnedCrates = BoolSetting("Show Spawned Crates", true)
    private val renderStyle = ModeSetting("Render Style", listOf("Outline", "Filled", "Both"), "Both")

    private val pickupTalismanTier = ModeSetting(
        "Pickup Talisman",
        listOf("Heart", "Lung", "Kidney", "Custom"),
        "Heart"
    )
    private val customTimerTickOffset = SliderSetting("Custom Timer Tick Offset", 0.0, -4.0, 4.0, 1.0)

    private val flatSize = SliderSetting("Flat Size", 0.64, 0.0, 1.0, 0.01)
    private val flatRed = SliderSetting("Flat Red", 255.0, 0.0, 255.0, 1.0)
    private val flatGreen = SliderSetting("Flat Green", 222.0, 0.0, 255.0, 1.0)
    private val flatBlue = SliderSetting("Flat Blue", 89.0, 0.0, 255.0, 1.0)
    private val flatAlpha = SliderSetting("Flat Alpha", 100.0, 0.0, 255.0, 1.0)

    private val skySize = SliderSetting("Sky Size", 0.64, 0.0, 1.0, 0.01)
    private val skyRed = SliderSetting("Sky Red", 0.0, 0.0, 255.0, 1.0)
    private val skyGreen = SliderSetting("Sky Green", 255.0, 0.0, 255.0, 1.0)
    private val skyBlue = SliderSetting("Sky Blue", 255.0, 0.0, 255.0, 1.0)
    private val skyAlpha = SliderSetting("Sky Alpha", 100.0, 0.0, 255.0, 1.0)

    private val prePearlSize = SliderSetting("Pre Pearl Size", 0.64, 0.0, 1.0, 0.01)
    private val prePearlRed = SliderSetting("Pre Pearl Red", 255.0, 0.0, 255.0, 1.0)
    private val prePearlGreen = SliderSetting("Pre Pearl Green", 160.0, 0.0, 255.0, 1.0)
    private val prePearlBlue = SliderSetting("Pre Pearl Blue", 0.0, 0.0, 255.0, 1.0)
    private val prePearlAlpha = SliderSetting("Pre Pearl Alpha", 120.0, 0.0, 255.0, 1.0)

    private val fastestRed = SliderSetting("Fastest Red", 255.0, 0.0, 255.0, 1.0)
    private val fastestGreen = SliderSetting("Fastest Green", 255.0, 0.0, 255.0, 1.0)
    private val fastestBlue = SliderSetting("Fastest Blue", 255.0, 0.0, 255.0, 1.0)
    private val fastestAlpha = SliderSetting("Fastest Alpha", 120.0, 0.0, 255.0, 1.0)

    private val pileRed = SliderSetting("Pile Red", 255.0, 0.0, 255.0, 1.0)
    private val pileGreen = SliderSetting("Pile Green", 255.0, 0.0, 255.0, 1.0)
    private val pileBlue = SliderSetting("Pile Blue", 255.0, 0.0, 255.0, 1.0)
    private val pileAlpha = SliderSetting("Pile Alpha", 70.0, 0.0, 255.0, 1.0)

    private val pipeline: RenderPipeline = RenderPipelines.register(
        RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath("larpclient", "pipeline/kuudra_waypoints"))
            .build()
    )

    private val allocator = ByteBufferBuilder(262144)
    private var buffer: BufferBuilder? = null
    private var vertexBuffer: MappableRingBuffer? = null
    private val colorModulator = Vector4f(1f, 1f, 1f, 1f)
    private val modelOffset = Vector3f()
    private val textureMatrix = Matrix4f()

    private var kuudraActive = false
    private var tickCounter = 0L
    private var currentOriginPre: Pre? = null
    private var missingSupply: Supply = Supply.UNKNOWN
    private val missingSupplies = linkedSetOf<Supply>()

    private var lastSupplyProgressIndex = -1
    private var supplyProgressStartMs = -1L
    private var lastSupplyProgressUpdateMs = -1L
    private var activeSupplyPhase = SupplyPhase.NONE
    private var simulatedPickupArea: AreaKind? = null

    private var cachedRender: CachedRender? = null
    private var pendingBuild: PendingBuild? = null

    private var simulatePickupStartMs: Long = -1L

    private val X_PILE = Destination("X", Vec3(-106.0, 78.0, -113.0))
    private val XC_PILE = Destination("XC", Vec3(-110.0, 78.0, -106.0))
    private val SQUARE_PILE = Destination("Square", Vec3(-110.0, 78.0, -106.0))
    private val SHOP_PILE = Destination("Shop", Vec3(-98.0, 78.0, -113.0))
    private val SLASH_PILE = Destination("Slash", Vec3(-106.0, 78.0, -99.0))
    private val EQUALS_PILE = Destination("Equals", Vec3(-98.0, 78.0, -99.0))
    private val TRI_PILE = Destination("Tri", Vec3(-94.0, 78.0, -106.0))
    private val XC_CRATE = Destination("XC", Vec3(-133.0, 79.0, -125.0), "XC Crate")
    private val SQUARE_CRATE = Destination("Square", Vec3(-138.5, 78.0, -87.0), "Square Crate")
    private val SHOP_CRATE = Destination("Shop", Vec3(-75.5, 77.5, -136.5), "Shop Crate")

    private val ALL_DESTINATIONS = listOf(X_PILE, XC_PILE, SQUARE_PILE, SHOP_PILE, SLASH_PILE, EQUALS_PILE, TRI_PILE)
    private val SECONDARY_DESTINATIONS = listOf(SHOP_PILE, XC_PILE, SQUARE_PILE)
    private val HUD_SUPPLY_ORDER = listOf(
        Supply.X,
        Supply.TRI,
        Supply.SLASH,
        Supply.EQUALS,
        Supply.SHOP,
        Supply.SQUARE,
        Supply.XC
    )
    private val CHAT_SUPPLY_ALIASES = listOf(
        "x cannon" to Supply.XC,
        "xcannon" to Supply.XC,
        "xc" to Supply.XC,
        "triangle" to Supply.TRI,
        "tri" to Supply.TRI,
        "equals" to Supply.EQUALS,
        "eq" to Supply.EQUALS,
        "=" to Supply.EQUALS,
        "slash" to Supply.SLASH,
        "/" to Supply.SLASH,
        "square" to Supply.SQUARE,
        "shop" to Supply.SHOP,
        " x " to Supply.X,
        "x!" to Supply.X,
        "x." to Supply.X,
        "x," to Supply.X,
        "x" to Supply.X
    )

    private val AREAS = listOf(
        AreaRegion(AreaKind.SECOND_SQUARE, -142, -135, -88, -82),
        AreaRegion(AreaKind.SECOND_SQUARE, -143, -135, -93, -88),
        AreaRegion(AreaKind.SECOND_XC, -140, -126, -120, -104),
        AreaRegion(AreaKind.MAIN_X, -150, -123, -170, -134),
        AreaRegion(AreaKind.MAIN_TRI, -80, -50, -126, -109),
        AreaRegion(AreaKind.MAIN_EQUALS, -80, -45, -108, -65),
        AreaRegion(AreaKind.MAIN_SLASH, -114, -108, -80, -66),
        AreaRegion(AreaKind.MAIN_SLASH, -117, -114, -80, -66),
        AreaRegion(AreaKind.SECOND_SHOP, -94, -72, -131, -123),
        AreaRegion(AreaKind.SECOND_SHOP, -80, -73, -142, -132),
        AreaRegion(AreaKind.SECOND_SHOP, -75, -70, -135, -131),
        AreaRegion(AreaKind.SECOND_SHOP, -72, -69, -138, -135)
    )

    private val SWEEP_OFFSETS = listOf(
        Vec3.ZERO,
        Vec3(PEARL_RADIUS, 0.0, 0.0),
        Vec3(-PEARL_RADIUS, 0.0, 0.0),
        Vec3(0.0, 0.0, PEARL_RADIUS),
        Vec3(0.0, 0.0, -PEARL_RADIUS),
        Vec3(0.0, -PEARL_RADIUS, 0.0),
        Vec3(PEARL_RADIUS * 0.7071, 0.0, PEARL_RADIUS * 0.7071),
        Vec3(-PEARL_RADIUS * 0.7071, 0.0, PEARL_RADIUS * 0.7071),
        Vec3(PEARL_RADIUS * 0.7071, 0.0, -PEARL_RADIUS * 0.7071),
        Vec3(-PEARL_RADIUS * 0.7071, 0.0, -PEARL_RADIUS * 0.7071)
    )

    init {
        settings += listOf(
            forceKuudra,
            showPileSpots,
            showFlatPearls,
            showSkyPearls,
            prePearlDelayMs,
            xFlatXcPrePearl,
            xFlatSquarePrePearl,
            xSkyXcPrePearl,
            xSkySquarePrePearl,
            slashFlatSquarePrePearl,
            slashSkySquarePrePearl,
            equalsFlatShopPrePearl,
            equalsFlatXcPrePearl,
            equalsSkyShopPrePearl,
            equalsSkyXcPrePearl,
            showFastestSecondaryOnly,
            showPriority,
            showSpawnedCrates,
            renderStyle,
            pickupTalismanTier,
            customTimerTickOffset,
            flatSize, flatRed, flatGreen, flatBlue, flatAlpha,
            skySize, skyRed, skyGreen, skyBlue, skyAlpha,
            prePearlSize, prePearlRed, prePearlGreen, prePearlBlue, prePearlAlpha,
            fastestRed, fastestGreen, fastestBlue, fastestAlpha,
            pileRed, pileGreen, pileBlue, pileAlpha
        )
    }

    fun toggleForceKuudra(): Boolean {
        forceKuudra.value = !forceKuudra.value
        invalidateSolvedWaypoints()
        return forceKuudra.value
    }

    fun onWorldChange() {
        kuudraActive = false
        resetDetectionState()
    }

    fun resetKuudraState() {
        resetDetectionState(clearActive = false)
    }

    fun simulatePickup() {
        kuudraActive = true
        simulatePickupStartMs = System.currentTimeMillis()
        val player = Minecraft.getInstance().player
        simulatedPickupArea = player?.let { detectArea(it.x, it.z)?.kind }
        resetTimerState()
        invalidateSolvedWaypoints()
    }

    fun onTitleMessage(rawMessage: String) {
        val stripped = ChatFormatting.stripFormatting(rawMessage)?.trim().orEmpty()
        val match = SUPPLY_PROGRESS_REGEX.matchEntire(stripped) ?: return
        val progress = match.groupValues[1].toIntOrNull() ?: return
        onSupplyProgressPercent(progress)
    }

    fun onChatMessage(rawMessage: String) {
        val stripped = ChatFormatting.stripFormatting(rawMessage)?.trim().orEmpty()
        if (stripped.isEmpty()) return

        when {
            stripped.contains(ELLE_START_MESSAGE) -> {
                kuudraActive = true
                resetDetectionState(clearActive = false)
                return
            }
            stripped.contains(ELLE_HEAD_OVER_MESSAGE) || stripped.contains(ELLE_NOT_AGAIN_MESSAGE) -> {
                val mc = Minecraft.getInstance()
                val player = mc.player ?: return
                detectMainPre(player.x, player.z)?.let {
                    currentOriginPre = it
                    invalidateSolvedWaypoints()
                }
                return
            }
            stripped.contains(ELLE_END_MESSAGE) || stripped.contains(SUPPLY_PICKUP_MESSAGE) || stripped.contains(SUPPLY_SLIP_MESSAGE) -> {
                resetTimerState()
                return
            }
        }

        detectMissingSupplyFromChat(stripped)?.let { detected ->
            if (currentOriginPre == null) {
                val mc = Minecraft.getInstance()
                val player = mc.player
                if (player != null) {
                    currentOriginPre = detectMainPre(player.x, player.z)
                }
            }
            recordMissingSupply(detected)
            invalidateSolvedWaypoints()
        }
    }

    override fun onTick() {
        tickCounter++

        if (!enabled || !shouldRun()) {
            invalidateSolvedWaypoints()
            return
        }

        if (simulatePickupStartMs > 0L) {
            val elapsed = max(0L, System.currentTimeMillis() - simulatePickupStartMs)
            val stepTimeMs = currentSupplyStepTimeMs()
            val step = (elapsed / stepTimeMs).toInt()
            val progress = when {
                step < 0 -> 0
                step >= SUPPLY_TICK_PERCENTAGES.size -> 100
                else -> SUPPLY_TICK_PERCENTAGES[step]
            }
            onSupplyProgressPercent(progress)
            Minecraft.getInstance().gui.setOverlayMessage(Component.literal("$progress%"), false)
            if (progress >= 100) {
                simulatePickupStartMs = -1L
            }
        }

        if (lastSupplyProgressIndex >= 0 &&
            lastSupplyProgressUpdateMs > 0L &&
            System.currentTimeMillis() - lastSupplyProgressUpdateMs > READY_RESET_TIMEOUT_MS
        ) {
            resetTimerState()
        }

        val mc = Minecraft.getInstance()
        val player = mc.player ?: run {
            invalidateSolvedWaypoints()
            return
        }
        val level = mc.level ?: run {
            invalidateSolvedWaypoints()
            return
        }

        if (currentOriginPre == null) {
            currentOriginPre = detectMainPre(player.x, player.z)
        }

        val area = detectArea(player.x, player.z) ?: run {
            invalidateSolvedWaypoints()
            return
        }

        if (needsNewBuild(player, area)) {
            startPendingBuild(player, area)
        }

        continuePendingBuild(level, player)
    }

    fun render(context: LevelRenderContext) {
        if (!enabled) return

        val mc = Minecraft.getInstance()
        val cached = cachedRender
        val cameraPos = mc.gameRenderer.mainCamera.position()
        val matrices = context.poseStack()
        matrices.pushPose()
        matrices.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z)

        var wroteAny = false
        val builder = ensureBuffer()
        val pose = matrices.last().pose()

        if (showPileSpots.value) {
            val player = mc.player
            for (pile in ALL_DESTINATIONS.distinctBy { it.physicalPos }) {
                val baseColor = pileColor()
                val color = if (player != null && horizontalDistanceTo(pile.physicalPos, player.position()) <= PILE_HIGHLIGHT_RADIUS) {
                    baseColor.copy(red = 0.05f, green = 0.88f, blue = 0.2f)
                } else {
                    baseColor
                }
                val lifted = pile.physicalPos.add(0.0, 1.0, 0.0)
                renderMarker(pose, builder, lifted, color, 1.18)
                renderBeaconBeam(pose, builder, lifted, color)
                wroteAny = true
            }
        }

        if (cached != null) {
            for (hint in cached.hints) {
                renderMarker(pose, builder, hint.displayTarget, hint.color, hint.size)
                renderMarker(
                    pose,
                    builder,
                    hint.target,
                    hint.color.copy(alpha = min(1f, hint.color.alpha + 0.12f)),
                    0.36
                )
                wroteAny = true
            }
        }

        matrices.popPose()

        if (wroteAny) drawBuiltBuffer(mc) else buffer = null

        if (cached != null) {
            for (hint in cached.hints) {
                renderTimerText(context, hint)
            }
        }
    }

    fun getTimerHudLines(): List<String> {
        val cached = cachedRender ?: return emptyList()
        val lines = arrayListOf("Kuudra Timers")
        for (hint in cached.hints) {
            val remaining = getRemainingTimerMs(hint.targetIndex)
            val isReady = hint.targetIndex >= 0 && lastSupplyProgressIndex >= hint.targetIndex
            when {
                remaining > 0L -> lines.add("${hint.label}: ${remaining}ms")
                isReady -> lines.add("${hint.label}: READY")
            }
        }
        return lines
    }

    fun getPileTargets(): List<Pair<String, Vec3>> {
        return ALL_DESTINATIONS
            .distinctBy { it.physicalPos }
            .map { it.displayLabel to it.physicalPos }
    }

    fun getAutoPearlHints(): List<AutoPearlHint> {
        val cached = cachedRender ?: return emptyList()
        return cached.hints.map { hint ->
            AutoPearlHint(
                key = "${hint.group.name}:${hint.label}",
                yaw = hint.solution.yaw.toFloat(),
                pitch = hint.solution.pitch.toFloat(),
                throwAtMs = hint.throwAtMs,
                travelMs = hint.solution.travelTicks * 50L,
                group = hint.group.name,
                label = hint.label
            )
        }.filter { hint ->
            hint.group != AutoPearlGroup.SECONDARY.name || hasActiveSecondaryPickup()
        }
    }

    fun getElapsedSupplyMs(): Long? {
        if (lastSupplyProgressIndex < 0 || supplyProgressStartMs < 0L) return null
        return max(0L, System.currentTimeMillis() - supplyProgressStartMs)
    }

    fun getCurrentSecondaryPriorityLabel(): String? {
        if (!hasResolvedMissingSupply()) return null

        val player = Minecraft.getInstance().player ?: return null
        if (currentOriginPre == null || !hasResolvedMissingSupply()) {
            currentOriginPre = detectMainPre(player.x, player.z)
        }
        val origin = currentOriginPre ?: return null
        val missing = missingSupplies.firstOrNull() ?: return null
        return priorityFor(origin, missing)?.destination?.label
    }

    fun getSecondaryPriorityLabelForOrigin(originLabel: String): String? {
        if (!hasResolvedMissingSupply()) return null

        val origin = when (originLabel.trim().uppercase()) {
            "X" -> Pre.X
            "/" , "SLASH" -> Pre.SLASH
            "=" , "EQUALS" -> Pre.EQUALS
            "TRI", "TRIANGLE" -> Pre.TRI
            else -> null
        } ?: return null

        val missing = missingSupplies.firstOrNull() ?: return null
        return priorityFor(origin, missing)?.destination?.label
    }

    fun isInMainArea(): Boolean {
        val player = Minecraft.getInstance().player ?: return false
        return when (detectArea(player.x, player.z)?.kind) {
            AreaKind.MAIN_X, AreaKind.MAIN_SLASH, AreaKind.MAIN_EQUALS, AreaKind.MAIN_TRI -> true
            else -> false
        }
    }

    fun isInSecondaryArea(): Boolean {
        val player = Minecraft.getInstance().player ?: return false
        return when (detectArea(player.x, player.z)?.kind) {
            AreaKind.SECOND_SHOP, AreaKind.SECOND_SQUARE, AreaKind.SECOND_XC -> true
            else -> false
        }
    }

    fun shouldShowPriorityHud(): Boolean {
        return enabled && showPriority.value && shouldRun()
    }

    fun shouldShowSpawnedCratesHud(): Boolean {
        return enabled && showSpawnedCrates.value && shouldRun()
    }

    fun getPriorityHudSnapshot(preview: Boolean = false): PriorityHudSnapshot {
        if (preview) {
            return PriorityHudSnapshot(
                title = "Kuudra Priority:",
                preLine = "Pre: Tri",
                goToLine = "Go to: Shop"
            )
        }

        return PriorityHudSnapshot(
            title = "Kuudra Priority:",
            preLine = "Pre: ${currentOriginPre?.display ?: "Detecting..."}",
            goToLine = "Go to: ${currentPriorityLabel()}"
        )
    }

    fun getSpawnedCratesHudSnapshot(preview: Boolean = false): SpawnedCratesHudSnapshot {
        if (preview) {
            return SpawnedCratesHudSnapshot(
                entries = HUD_SUPPLY_ORDER.map { supply ->
                    SpawnedCrateHudEntry(supply.display, supply != Supply.SLASH)
                }
            )
        }

        val resolvedMissing = missingSupplies.firstOrNull().takeIf { hasResolvedMissingSupply() }
        return SpawnedCratesHudSnapshot(
            entries = HUD_SUPPLY_ORDER.map { supply ->
                SpawnedCrateHudEntry(supply.display, resolvedMissing != null && supply != resolvedMissing)
            }
        )
    }

    private fun shouldRun(): Boolean = forceKuudra.value || kuudraActive

    private fun getTimerTickOffset(): Int {
        return when (pickupTalismanTier.selected) {
            "Heart" -> 0
            "Lung" -> 1
            "Kidney" -> 2
            "Custom" -> customTimerTickOffset.value.toInt()
            else -> 0
        }
    }

    private fun onSupplyProgressPercent(progress: Int) {
        val previousIndex = lastSupplyProgressIndex
        lastSupplyProgressIndex = getProgressIndex(progress)
        val now = System.currentTimeMillis()
        lastSupplyProgressUpdateMs = now

        if (previousIndex < 0 && lastSupplyProgressIndex >= 0) {
            val startedArea = simulatedPickupArea
                ?: Minecraft.getInstance().player?.let { detectArea(it.x, it.z)?.kind }
            activeSupplyPhase = when (startedArea) {
                AreaKind.SECOND_SHOP, AreaKind.SECOND_SQUARE, AreaKind.SECOND_XC -> SupplyPhase.SECONDARY
                AreaKind.MAIN_X, AreaKind.MAIN_SLASH, AreaKind.MAIN_EQUALS, AreaKind.MAIN_TRI -> SupplyPhase.PRIMARY
                else -> SupplyPhase.NONE
            }
            simulatedPickupArea = null
        }

        if (progress <= 0) {
            supplyProgressStartMs = now
        } else if (
            lastSupplyProgressIndex >= 0 &&
            (supplyProgressStartMs < 0L || previousIndex != lastSupplyProgressIndex)
        ) {
            supplyProgressStartMs = now - getTargetTimeMs(lastSupplyProgressIndex)
        }
    }

    private fun resetTimerState() {
        lastSupplyProgressIndex = -1
        supplyProgressStartMs = -1L
        lastSupplyProgressUpdateMs = -1L
        activeSupplyPhase = SupplyPhase.NONE
        simulatedPickupArea = null
    }

    private fun resetDetectionState(clearActive: Boolean = true) {
        if (clearActive) {
            kuudraActive = false
        }
        currentOriginPre = null
        missingSupply = Supply.UNKNOWN
        missingSupplies.clear()
        resetTimerState()
        invalidateSolvedWaypoints()
    }

    private fun recordMissingSupply(supply: Supply) {
        if (supply == Supply.UNKNOWN) return
        missingSupplies.clear()
        missingSupplies += supply
        missingSupply = supply
    }

    private fun getProgressIndex(progress: Int): Int {
        if (progress <= 0) return -1
        for (i in SUPPLY_TICK_PERCENTAGES.indices) {
            if (SUPPLY_TICK_PERCENTAGES[i] >= progress) return i
        }
        return SUPPLY_TICK_PERCENTAGES.lastIndex
    }

    private fun getTargetTimeMs(targetIndex: Int): Long = targetIndex * currentSupplyStepTimeMs()

    private fun getRemainingTimerMs(targetIndex: Int): Long {
        if (targetIndex < 0 || lastSupplyProgressIndex < 0 || supplyProgressStartMs < 0L) return -1L
        if (lastSupplyProgressIndex >= targetIndex) return -1L

        val elapsedMs = max(0L, System.currentTimeMillis() - supplyProgressStartMs)
        val remainingMs = getTargetTimeMs(targetIndex) - elapsedMs
        if (remainingMs <= 0L) return -1L

        val rounded = (remainingMs / TIMER_UPDATE_STEP_MS) * TIMER_UPDATE_STEP_MS
        return if (rounded > 0L) rounded else -1L
    }

    private fun invalidateSolvedWaypoints() {
        cachedRender = null
        pendingBuild = null
    }

    private fun startPendingBuild(player: LocalPlayer, area: AreaRegion) {
        val playerPos = vec(player.x, player.y, player.z)
        val tasks = ArrayDeque<PendingTask>()

        when (area.kind) {
            AreaKind.MAIN_X, AreaKind.MAIN_SLASH, AreaKind.MAIN_EQUALS, AreaKind.MAIN_TRI -> {
                val origin = currentOriginPre ?: preFromArea(area.kind) ?: detectMainPre(player.x, player.z)
                if (origin != null) {
                    currentOriginPre = origin
                    val mainMissing = origin.mainSupply
                    if (mainMissing !in missingSupplies) {
                        val dest = origin.mainDestination
                        if (showFlatPearls.value) {
                            tasks.add(PendingTask(dest, "${origin.display} Flat", HintKind.FLAT, flatColor(), flatSize.value, group = AutoPearlGroup.MAIN))
                        }
                        if (showSkyPearls.value) {
                            tasks.add(PendingTask(dest, "${origin.display} Sky", HintKind.SKY, skyColor(), skySize.value, group = AutoPearlGroup.MAIN))
                        }
                        addPrePearlTask(tasks, origin)
                    }
                }
            }

            AreaKind.SECOND_SHOP, AreaKind.SECOND_SQUARE, AreaKind.SECOND_XC -> {
                val origin = currentOriginPre
                if (origin != null) {
                    if (!hasResolvedMissingSupply()) {
                        pendingBuild = PendingBuild(area, playerPos, tasks, mutableListOf(), tickCounter)
                        return
                    }
                    val priority = highestPriorityFor(origin, area.kind)
                    if (priority == null || missingSupplies.isEmpty()) {
                        val candidates = uniqueSecondaryDestinationsFor(origin)
                        for (dest in candidates) {
                            addFastestTask(tasks, dest, "Fastest ${dest.label}")
                        }
                    } else if (priority.requiredArea == area.kind) {
                        if (showFastestSecondaryOnly.value) {
                            addFastestTask(tasks, priority.destination, "Fastest ${priority.destination.label}")
                        } else {
                            if (showFlatPearls.value) {
                                tasks.add(PendingTask(priority.destination, "${priority.destination.label} Flat", HintKind.FLAT, flatColor(), flatSize.value, group = AutoPearlGroup.SECONDARY))
                            }
                            if (showSkyPearls.value) {
                                tasks.add(PendingTask(priority.destination, "${priority.destination.label} Sky", HintKind.SKY, skyColor(), skySize.value, group = AutoPearlGroup.SECONDARY))
                            }
                        }
                    }
                } else {
                    for (dest in SECONDARY_DESTINATIONS.distinctBy { it.label }) {
                        addFastestTask(tasks, dest, "Fastest ${dest.label}")
                    }
                }
            }
        }

        pendingBuild = PendingBuild(area, playerPos, tasks, mutableListOf(), tickCounter)
    }

    private fun addFastestTask(tasks: ArrayDeque<PendingTask>, destination: Destination, label: String) {
        tasks.add(PendingTask(destination, label, HintKind.FASTEST, fastestColor(), flatSize.value, group = AutoPearlGroup.SECONDARY))
    }

    private fun addPrePearlTask(tasks: ArrayDeque<PendingTask>, origin: Pre) {
        if (!hasResolvedMissingSupply()) return
        val missing = missingSupplies.firstOrNull() ?: return
        prePearlSelectionsFor(origin, missing).forEach { selection ->
            val color = prePearlColor()
            val size = prePearlSize.value
            val arcLabel = if (selection.preferHighArc) "Sky" else "Flat"
            tasks.add(PendingTask(selection.destination, "Pre ${selection.destination.label} $arcLabel", HintKind.PREPEARL, color, size, selection.preferHighArc, AutoPearlGroup.PRE))
        }
    }

    private fun continuePendingBuild(level: ClientLevel, player: LocalPlayer) {
        val pending = pendingBuild ?: return

        if (pending.tasks.isEmpty()) {
            cachedRender = CachedRender(pending.area, pending.playerPos, pending.hints.toList(), tickCounter)
            pendingBuild = null
            return
        }

        val task = pending.tasks.removeFirst()
        val start = pearlStart(player)

        when (task.kind) {
            HintKind.FLAT -> createArcHint(level, player, start, task.destination, false, task.label, task.color, task.size)
            HintKind.SKY -> createArcHint(level, player, start, task.destination, true, task.label, task.color, task.size)
            HintKind.FASTEST -> createFastestHint(level, player, start, task.destination, task.label, task.color, task.size)
            HintKind.PREPEARL -> createPrePearlHint(level, player, start, task.destination, task.preferHighArc, task.label, task.color, task.size)
        }?.let(pending.hints::add)

        if (pending.tasks.isEmpty()) {
            cachedRender = CachedRender(pending.area, pending.playerPos, pending.hints.toList(), tickCounter)
            pendingBuild = null
        }
    }

    private fun createArcHint(
        level: ClientLevel,
        player: LocalPlayer,
        start: Vec3,
        destination: Destination,
        preferHighArc: Boolean,
        label: String,
        color: ColorDef,
        size: Double
    ): RenderHint? {
        val solution = solvePearl(level, player, start, destination.physicalPos, preferHighArc)
            ?: approximateSolution(level, player, start, destination.physicalPos, preferHighArc)
            ?: return null

        val throwAtMs = defaultPickupDurationMs() - solution.travelTicks * 50L
        val baseIndex = msToTargetIndex(throwAtMs)
        val targetIndex = (baseIndex + getTimerTickOffset()).coerceIn(0, SUPPLY_TICK_PERCENTAGES.lastIndex)

        return RenderHint(label, destination.displayLabel, solution, destination.physicalPos, color, size.coerceIn(0.0, 1.0), targetIndex, throwAtMs, AutoPearlGroup.MAIN)
    }

    private fun createFastestHint(
        level: ClientLevel,
        player: LocalPlayer,
        start: Vec3,
        destination: Destination,
        label: String,
        color: ColorDef,
        size: Double
    ): RenderHint? {
        val flat = solvePearl(level, player, start, destination.physicalPos, false)
            ?: approximateSolution(level, player, start, destination.physicalPos, false)
        val sky = solvePearl(level, player, start, destination.physicalPos, true)
            ?: approximateSolution(level, player, start, destination.physicalPos, true)
        val best = listOfNotNull(flat, sky).minByOrNull { it.travelTicks } ?: return null

        val throwAtMs = defaultPickupDurationMs() - best.travelTicks * 50L
        val baseIndex = msToTargetIndex(throwAtMs)
        val targetIndex = (baseIndex + getTimerTickOffset()).coerceIn(0, SUPPLY_TICK_PERCENTAGES.lastIndex)

        return RenderHint(label, destination.displayLabel, best, destination.physicalPos, color, size.coerceIn(0.0, 1.0), targetIndex, throwAtMs, AutoPearlGroup.SECONDARY)
    }

    private fun createPrePearlHint(
        level: ClientLevel,
        player: LocalPlayer,
        start: Vec3,
        destination: Destination,
        preferHighArc: Boolean,
        label: String,
        color: ColorDef,
        size: Double
    ): RenderHint? {
        val solution = solvePearl(level, player, start, destination.physicalPos, preferHighArc)
            ?: approximateSolution(level, player, start, destination.physicalPos, preferHighArc)
            ?: return null

        val throwAtMs = defaultPickupDurationMs() + prePearlDelayMs.value.toLong() - solution.travelTicks * 50L
        val targetIndex = (msToTargetIndex(throwAtMs) + getTimerTickOffset()).coerceIn(0, SUPPLY_TICK_PERCENTAGES.lastIndex)

        return RenderHint(label, destination.displayLabel, solution, destination.physicalPos, color, size.coerceIn(0.0, 1.0), targetIndex, throwAtMs, AutoPearlGroup.PRE)
    }

    private fun msToTargetIndex(throwAtMs: Long): Int {
        val clamped = throwAtMs.coerceAtLeast(0L)
        val raw = (clamped.toDouble() / currentSupplyStepTimeMs().toDouble()).roundToInt()
        return raw.coerceIn(0, SUPPLY_TICK_PERCENTAGES.lastIndex)
    }

    private fun detectMainPre(x: Double, z: Double): Pre? {
        return Pre.entries.minByOrNull { squaredHorizontalDistance(x, z, it.center.x, it.center.z) }
            ?.takeIf { squaredHorizontalDistance(x, z, it.center.x, it.center.z) <= it.radius * it.radius }
    }

    private fun detectArea(x: Double, z: Double): AreaRegion? {
        return AREAS.firstOrNull { it.contains(x, z) }
    }

    private fun needsNewBuild(player: LocalPlayer, area: AreaRegion): Boolean {
        val playerPos = vec(player.x, player.y, player.z)
        val pending = pendingBuild
        if (pending != null) {
            if (pending.area != area) return true
            if (pending.playerPos.distanceToSqr(playerPos) >= PLAYER_MOVE_RECOMPUTE_DISTANCE * PLAYER_MOVE_RECOMPUTE_DISTANCE) return true
            return false
        }
        return needsRecompute(player, area)
    }

    private fun needsRecompute(player: LocalPlayer, area: AreaRegion): Boolean {
        val cached = cachedRender ?: return true
        if (cached.area != area) return true
        if (tickCounter - cached.tickBuilt >= RECOMPUTE_INTERVAL) return true
        return cached.playerPos.distanceToSqr(vec(player.x, player.y, player.z)) >=
                PLAYER_MOVE_RECOMPUTE_DISTANCE * PLAYER_MOVE_RECOMPUTE_DISTANCE
    }

    private fun pearlStart(player: LocalPlayer): Vec3 {
        val yawRad = Math.toRadians(player.yRot.toDouble())
        return Vec3(
            player.x - cos(yawRad) * 0.16,
            player.eyeY - 0.1,
            player.z - sin(yawRad) * 0.16
        )
    }

    private fun solvePearl(level: ClientLevel, player: LocalPlayer, start: Vec3, target: Vec3, preferHighArc: Boolean): PearlSolution? {
        val dx = target.x - start.x
        val dz = target.z - start.z
        val yaw = wrapDegrees(Math.toDegrees(atan2(dz, dx)) - 90.0)

        val range = if (preferHighArc) -89.0..-20.0 else -35.0..25.0
        val coarse = bestSolutionInRange(level, player, start, target, yaw, range.start, range.endInclusive, 1.0)
        val fineStart = (coarse?.pitch?.minus(1.0) ?: range.start).coerceAtLeast(range.start)
        val fineEnd = (coarse?.pitch?.plus(1.0) ?: range.endInclusive).coerceAtMost(range.endInclusive)
        val best = bestSolutionInRange(level, player, start, target, yaw, fineStart, fineEnd, 0.25) ?: coarse
        val bestScore = best?.let { scoreSolution(it.hit, target) } ?: Double.POSITIVE_INFINITY
        return best?.takeIf { bestScore <= 2.5 }
    }

    private fun approximateSolution(level: ClientLevel, player: LocalPlayer, start: Vec3, target: Vec3, preferHighArc: Boolean): PearlSolution? {
        val dx = target.x - start.x
        val dz = target.z - start.z
        val yaw = wrapDegrees(Math.toDegrees(atan2(dz, dx)) - 90.0)

        val range = if (preferHighArc) -89.0..-20.0 else -35.0..25.0
        val coarse = bestSolutionInRange(level, player, start, target, yaw, range.start, range.endInclusive, 2.0)
        val fineStart = (coarse?.pitch?.minus(2.0) ?: range.start).coerceAtLeast(range.start)
        val fineEnd = (coarse?.pitch?.plus(2.0) ?: range.endInclusive).coerceAtMost(range.endInclusive)
        return bestSolutionInRange(level, player, start, target, yaw, fineStart, fineEnd, 0.5) ?: coarse
    }

    private fun bestSolutionInRange(
        level: ClientLevel,
        player: LocalPlayer,
        start: Vec3,
        target: Vec3,
        yaw: Double,
        minPitch: Double,
        maxPitch: Double,
        step: Double
    ): PearlSolution? {
        var best: PearlSolution? = null
        var bestScore = Double.POSITIVE_INFINITY
        var pitch = minPitch

        while (pitch <= maxPitch + 1.0e-6) {
            val sim = simulatePearl(level, player, start, yaw, pitch)
            if (sim != null) {
                val score = scoreSolution(sim.hit, target)
                if (score < bestScore) {
                    bestScore = score
                    best = PearlSolution(yaw, pitch, sim.travelTicks, findAimMarker(start, yaw, pitch), sim.hit)
                }
            }
            pitch += step
        }

        return best
    }

    private fun scoreSolution(hit: BlockHitResult, target: Vec3): Double {
        val hitCenter = Vec3.atCenterOf(hit.blockPos)
        return hitCenter.distanceTo(target) + if (hit.blockPos == blockPos(target)) 0.0 else 2.0
    }

    private fun simulatePearl(level: ClientLevel, player: LocalPlayer, start: Vec3, yaw: Double, pitch: Double): Simulation? {
        var pos = start
        var velocity = initialVelocity(yaw, pitch)

        repeat(MAX_SIM_TICKS) { tick ->
            val nextPos = pos.add(velocity)
            val hit = findEarliestHit(level, player, pos, nextPos)
            if (hit != null && hit.type != HitResult.Type.MISS) {
                return Simulation(hit, tick + 1)
            }
            pos = nextPos
            velocity = velocity.scale(DRAG).add(0.0, -GRAVITY, 0.0)
            if (velocity.lengthSqr() < 1.0e-7) return null
        }

        return null
    }

    private fun initialVelocity(yaw: Double, pitch: Double): Vec3 {
        val yawRad = Math.toRadians(yaw)
        val pitchRad = Math.toRadians(pitch)
        return Vec3(
            -sin(yawRad) * cos(pitchRad) * THROW_SPEED,
            -sin(pitchRad) * THROW_SPEED,
            cos(yawRad) * cos(pitchRad) * THROW_SPEED
        )
    }

    private fun findEarliestHit(level: ClientLevel, player: LocalPlayer, start: Vec3, end: Vec3): BlockHitResult? {
        var bestBlock: BlockHitResult? = null
        var bestDistSq = Double.POSITIVE_INFINITY

        for (offset in SWEEP_OFFSETS) {
            val rayStart = start.add(offset)
            val rayEnd = end.add(offset)
            val blockHit = level.clip(
                ClipContext(rayStart, rayEnd, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player)
            )
            if (blockHit.type != HitResult.Type.MISS) {
                val distSq = rayStart.distanceToSqr(blockHit.location)
                if (distSq < bestDistSq) {
                    bestDistSq = distSq
                    bestBlock = blockHit
                }
            }
        }

        val sweptBox = AABB(start, end).inflate(PEARL_RADIUS + 0.3)
        val entityHit = ProjectileUtil.getEntityHitResult(
            player,
            start,
            end,
            sweptBox,
            { entity -> !entity.isSpectator && entity.isPickable },
            sweptBox.size
        )
        if (entityHit != null) {
            val distSq = start.distanceToSqr(entityHit.location)
            if (distSq < bestDistSq) {
                return BlockHitResult(entityHit.location, net.minecraft.core.Direction.UP, entityHit.entity.blockPosition(), false)
            }
        }

        return bestBlock
    }

    private fun findAimMarker(start: Vec3, yaw: Double, pitch: Double): Vec3 {
        return start.add(lookVector(yaw, pitch).scale(AIM_MARKER_DISTANCE))
    }

    private fun lookVector(yaw: Double, pitch: Double): Vec3 {
        val yawRad = Math.toRadians(yaw)
        val pitchRad = Math.toRadians(pitch)
        return Vec3(-sin(yawRad) * cos(pitchRad), -sin(pitchRad), cos(yawRad) * cos(pitchRad))
    }

    private fun pickupSpeedMultiplier(): Double {
        return when (pickupTalismanTier.selected) {
            "Heart" -> 1.30
            "Lung" -> 1.20
            "Kidney" -> 1.10
            "Custom" -> 1.00
            else -> 1.00
        }
    }

    private fun currentSupplyStepTimeMs(): Long {
        return (SUPPLY_STEP_TIME_MS.toDouble() / pickupSpeedMultiplier()).roundToInt().coerceAtLeast(1).toLong()
    }

    private fun defaultPickupDurationMs(): Long = currentSupplyStepTimeMs() * (SUPPLY_TICK_PERCENTAGES.size - 1)

    private fun renderTimerText(context: LevelRenderContext, hint: RenderHint) {
        val remainingMs = getRemainingTimerMs(hint.targetIndex)
        val isReady = hint.targetIndex >= 0 && lastSupplyProgressIndex >= hint.targetIndex
        val timerText = when {
            remainingMs > 0L -> "${remainingMs}ms"
            isReady -> "READY"
            else -> null
        }

        val mc = Minecraft.getInstance()
        val camera = mc.gameRenderer.mainCamera
        val font = mc.font
        val pos = hint.displayTarget.add(0.0, 1.2, 0.0)

        val matrices = context.poseStack()
        matrices.pushPose()
        matrices.translate(pos.x - camera.position().x, pos.y - camera.position().y, pos.z - camera.position().z)
        matrices.mulPose(camera.rotation())
        val scale = 0.05f
        matrices.scale(scale, -scale, scale)

        val matrix = matrices.last().pose()
        val buffer = mc.renderBuffers().bufferSource()
        timerText?.let { text ->
            val width = font.width(text)
            font.drawInBatch(
                text,
                -width / 2f,
                -10f,
                0xFFFFFFFF.toInt(),
                true,
                matrix,
                buffer,
                Font.DisplayMode.SEE_THROUGH,
                0,
                15728880
            )
        }
        val landingWidth = font.width(hint.destinationLabel)
        font.drawInBatch(
            hint.destinationLabel,
            -landingWidth / 2f,
            2f,
            0xFF55FFFF.toInt(),
            true,
            matrix,
            buffer,
            Font.DisplayMode.SEE_THROUGH,
            0,
            15728880
        )
        buffer.endBatch()
        matrices.popPose()
    }

    private fun ensureBuffer(): BufferBuilder {
        return buffer ?: BufferBuilder(allocator, pipeline.vertexFormatMode, pipeline.vertexFormat).also { buffer = it }
    }

    private fun renderMarker(positionMatrix: Matrix4fc, buffer: BufferBuilder, center: Vec3, color: ColorDef, size: Double) {
        val half = size.coerceIn(0.0, 1.0) / 2.0
        if (renderStyle.selected == "Filled" || renderStyle.selected == "Both") {
            renderFilledBox(
                positionMatrix, buffer,
                (center.x - half).toFloat(), (center.y - half).toFloat(), (center.z - half).toFloat(),
                (center.x + half).toFloat(), (center.y + half).toFloat(), (center.z + half).toFloat(),
                color.red, color.green, color.blue, color.alpha
            )
        }
        if (renderStyle.selected == "Outline" || renderStyle.selected == "Both") {
            renderOutlineBox(positionMatrix, buffer, center, half + 0.02, color.copy(alpha = 1f))
        }
    }

    private fun renderOutlineBox(positionMatrix: Matrix4fc, buffer: BufferBuilder, center: Vec3, half: Double, color: ColorDef) {
        val thickness = 0.035f
        val minX = (center.x - half).toFloat()
        val minY = (center.y - half).toFloat()
        val minZ = (center.z - half).toFloat()
        val maxX = (center.x + half).toFloat()
        val maxY = (center.y + half).toFloat()
        val maxZ = (center.z + half).toFloat()

        fun lineBox(x1: Float, y1: Float, z1: Float, x2: Float, y2: Float, z2: Float) {
            val minBx = min(x1, x2) - thickness / 2f
            val minBy = min(y1, y2) - thickness / 2f
            val minBz = min(z1, z2) - thickness / 2f
            val maxBx = max(x1, x2) + thickness / 2f
            val maxBy = max(y1, y2) + thickness / 2f
            val maxBz = max(z1, z2) + thickness / 2f
            renderFilledBox(positionMatrix, buffer, minBx, minBy, minBz, maxBx, maxBy, maxBz, color.red, color.green, color.blue, color.alpha)
        }

        lineBox(minX, minY, minZ, maxX, minY, minZ)
        lineBox(minX, minY, maxZ, maxX, minY, maxZ)
        lineBox(minX, maxY, minZ, maxX, maxY, minZ)
        lineBox(minX, maxY, maxZ, maxX, maxY, maxZ)
        lineBox(minX, minY, minZ, minX, maxY, minZ)
        lineBox(maxX, minY, minZ, maxX, maxY, minZ)
        lineBox(minX, minY, maxZ, minX, maxY, maxZ)
        lineBox(maxX, minY, maxZ, maxX, maxY, maxZ)
        lineBox(minX, minY, minZ, minX, minY, maxZ)
        lineBox(maxX, minY, minZ, maxX, minY, maxZ)
        lineBox(minX, maxY, minZ, minX, maxY, maxZ)
        lineBox(maxX, maxY, minZ, maxX, maxY, maxZ)
    }

    private fun renderBeaconBeam(positionMatrix: Matrix4fc, buffer: BufferBuilder, center: Vec3, color: ColorDef) {
        val beamHalf = 0.08f
        renderFilledBox(
            positionMatrix, buffer,
            center.x.toFloat() - beamHalf, center.y.toFloat(), center.z.toFloat() - beamHalf,
            center.x.toFloat() + beamHalf, (center.y + 80.0).toFloat(), center.z.toFloat() + beamHalf,
            color.red, color.green, color.blue, color.alpha
        )
    }

    private fun renderFilledBox(
        positionMatrix: Matrix4fc,
        buffer: BufferBuilder,
        minX: Float, minY: Float, minZ: Float,
        maxX: Float, maxY: Float, maxZ: Float,
        red: Float, green: Float, blue: Float, alpha: Float
    ) {
        buffer.addVertex(positionMatrix, minX, minY, maxZ).setColor(red, green, blue, alpha)
        buffer.addVertex(positionMatrix, maxX, minY, maxZ).setColor(red, green, blue, alpha)
        buffer.addVertex(positionMatrix, maxX, maxY, maxZ).setColor(red, green, blue, alpha)
        buffer.addVertex(positionMatrix, minX, maxY, maxZ).setColor(red, green, blue, alpha)

        buffer.addVertex(positionMatrix, maxX, minY, minZ).setColor(red, green, blue, alpha)
        buffer.addVertex(positionMatrix, minX, minY, minZ).setColor(red, green, blue, alpha)
        buffer.addVertex(positionMatrix, minX, maxY, minZ).setColor(red, green, blue, alpha)
        buffer.addVertex(positionMatrix, maxX, maxY, minZ).setColor(red, green, blue, alpha)

        buffer.addVertex(positionMatrix, minX, minY, minZ).setColor(red, green, blue, alpha)
        buffer.addVertex(positionMatrix, minX, minY, maxZ).setColor(red, green, blue, alpha)
        buffer.addVertex(positionMatrix, minX, maxY, maxZ).setColor(red, green, blue, alpha)
        buffer.addVertex(positionMatrix, minX, maxY, minZ).setColor(red, green, blue, alpha)

        buffer.addVertex(positionMatrix, maxX, minY, maxZ).setColor(red, green, blue, alpha)
        buffer.addVertex(positionMatrix, maxX, minY, minZ).setColor(red, green, blue, alpha)
        buffer.addVertex(positionMatrix, maxX, maxY, minZ).setColor(red, green, blue, alpha)
        buffer.addVertex(positionMatrix, maxX, maxY, maxZ).setColor(red, green, blue, alpha)

        buffer.addVertex(positionMatrix, minX, maxY, maxZ).setColor(red, green, blue, alpha)
        buffer.addVertex(positionMatrix, maxX, maxY, maxZ).setColor(red, green, blue, alpha)
        buffer.addVertex(positionMatrix, maxX, maxY, minZ).setColor(red, green, blue, alpha)
        buffer.addVertex(positionMatrix, minX, maxY, minZ).setColor(red, green, blue, alpha)

        buffer.addVertex(positionMatrix, minX, minY, minZ).setColor(red, green, blue, alpha)
        buffer.addVertex(positionMatrix, maxX, minY, minZ).setColor(red, green, blue, alpha)
        buffer.addVertex(positionMatrix, maxX, minY, maxZ).setColor(red, green, blue, alpha)
        buffer.addVertex(positionMatrix, minX, minY, maxZ).setColor(red, green, blue, alpha)
    }

    private fun drawBuiltBuffer(client: Minecraft) {
        val built = buffer?.buildOrThrow() ?: return
        val drawState = built.drawState()
        val format = drawState.format()
        val vertices = upload(drawState, format, built)
        draw(client, built, drawState, vertices)
        vertexBuffer?.rotate()
        buffer = null
    }

    private fun upload(drawState: MeshData.DrawState, format: VertexFormat, built: MeshData): GpuBuffer {
        val vertexBufferSizeInt = drawState.vertexCount() * format.vertexSize
        val vertexBufferSizeLong = vertexBufferSizeInt.toLong()
        val vertexData: ByteBuffer = built.vertexBuffer()

        if (vertexBuffer == null || vertexBuffer!!.size() < vertexBufferSizeLong) {
            vertexBuffer?.close()
            vertexBuffer = MappableRingBuffer(
                { "larpclient kuudra waypoint renderer" },
                GpuBuffer.USAGE_VERTEX or GpuBuffer.USAGE_MAP_WRITE,
                vertexBufferSizeInt
            )
        }

        val commandEncoder: CommandEncoder = RenderSystem.getDevice().createCommandEncoder()
        commandEncoder.mapBuffer(vertexBuffer!!.currentBuffer().slice(0, vertexData.remaining().toLong()), false, true).use { mappedView ->
            val mappedData: ByteBuffer = mappedView.data()
            MemoryUtil.memCopy(MemoryUtil.memAddress(vertexData), MemoryUtil.memAddress(mappedData), vertexData.remaining().toLong())
        }

        return vertexBuffer!!.currentBuffer()
    }

    private fun draw(client: Minecraft, built: MeshData, drawState: MeshData.DrawState, vertices: GpuBuffer) {
        val indices: GpuBuffer
        val indexType: VertexFormat.IndexType

        if (pipeline.vertexFormatMode == VertexFormat.Mode.QUADS) {
            built.sortQuads(allocator, RenderSystem.getProjectionType().vertexSorting())
            indices = pipeline.vertexFormat.uploadImmediateIndexBuffer(
                built.indexBuffer() ?: throw IllegalStateException("Index buffer was null")
            )
            indexType = built.drawState().indexType()
        } else {
            val shapeIndexBuffer = RenderSystem.getSequentialBuffer(pipeline.vertexFormatMode)
            indices = shapeIndexBuffer.getBuffer(drawState.indexCount())
            indexType = shapeIndexBuffer.type()
        }

        val dynamicTransforms = RenderSystem.getDynamicUniforms()
            .writeTransform(RenderSystem.getModelViewMatrix(), colorModulator, modelOffset, textureMatrix)

        val colorView = requireNotNull(client.mainRenderTarget.colorTextureView)
        val depthView = requireNotNull(client.mainRenderTarget.depthTextureView)

        RenderSystem.getDevice().createCommandEncoder().createRenderPass(
            { "larpclient kuudra waypoint render pass" },
            colorView,
            OptionalInt.empty(),
            depthView,
            OptionalDouble.empty()
        ).use { renderPass: RenderPass ->
            renderPass.setPipeline(pipeline)
            RenderSystem.bindDefaultUniforms(renderPass)
            renderPass.setUniform("DynamicTransforms", dynamicTransforms)
            renderPass.setVertexBuffer(0, vertices)
            renderPass.setIndexBuffer(indices, indexType)
            renderPass.drawIndexed(0, 0, drawState.indexCount(), 1)
        }

        built.close()
    }

    private fun flatColor() = colorFrom(flatRed, flatGreen, flatBlue, flatAlpha)
    private fun skyColor() = colorFrom(skyRed, skyGreen, skyBlue, skyAlpha)
    private fun prePearlColor() = colorFrom(prePearlRed, prePearlGreen, prePearlBlue, prePearlAlpha)
    private fun fastestColor() = colorFrom(fastestRed, fastestGreen, fastestBlue, fastestAlpha)
    private fun pileColor() = colorFrom(pileRed, pileGreen, pileBlue, pileAlpha)

    private fun colorFrom(r: SliderSetting, g: SliderSetting, b: SliderSetting, a: SliderSetting) = ColorDef(
        (r.value / 255.0).toFloat(),
        (g.value / 255.0).toFloat(),
        (b.value / 255.0).toFloat(),
        (a.value / 255.0).toFloat()
    )

    private fun squaredHorizontalDistance(x1: Double, z1: Double, x2: Double, z2: Double): Double {
        val dx = x1 - x2
        val dz = z1 - z2
        return dx * dx + dz * dz
    }

    private fun horizontalDistanceTo(a: Vec3, b: Vec3): Double {
        return kotlin.math.sqrt(squaredHorizontalDistance(a.x, a.z, b.x, b.z))
    }

    private fun wrapDegrees(value: Double): Double {
        var wrapped = value % 360.0
        if (wrapped >= 180.0) wrapped -= 360.0
        if (wrapped < -180.0) wrapped += 360.0
        return wrapped
    }

    private fun prePearlSelectionsFor(origin: Pre, missing: Supply): List<PrePearlSelection> {
        if (missing == Supply.UNKNOWN) return emptyList()
        val destination = when (origin) {
            Pre.X -> when (missing) {
                Supply.XC -> SQUARE_CRATE
                Supply.TRI, Supply.SLASH, Supply.EQUALS, Supply.SHOP, Supply.SQUARE -> XC_CRATE
                Supply.X, Supply.UNKNOWN -> null
            }

            Pre.SLASH -> when (missing) {
                Supply.X, Supply.TRI, Supply.EQUALS, Supply.SHOP, Supply.XC -> SQUARE_CRATE
                Supply.SLASH, Supply.SQUARE, Supply.UNKNOWN -> null
            }

            Pre.EQUALS -> when (missing) {
                Supply.EQUALS, Supply.SQUARE -> SHOP_CRATE
                Supply.XC -> SHOP_CRATE
                Supply.X, Supply.SLASH, Supply.TRI, Supply.SHOP, Supply.UNKNOWN -> null
            }

            Pre.TRI -> null
        } ?: return emptyList()

        return when (origin) {
            Pre.X -> when (destination) {
                XC_CRATE -> listOfNotNull(
                    xFlatXcPrePearl.takeIf { it.value }?.let { PrePearlSelection(destination, false) },
                    xSkyXcPrePearl.takeIf { it.value }?.let { PrePearlSelection(destination, true) }
                )
                SQUARE_CRATE -> listOfNotNull(
                    xFlatSquarePrePearl.takeIf { it.value }?.let { PrePearlSelection(destination, false) },
                    xSkySquarePrePearl.takeIf { it.value }?.let { PrePearlSelection(destination, true) }
                )
                else -> emptyList()
            }

            Pre.SLASH -> if (destination == SQUARE_CRATE) {
                listOfNotNull(
                    slashFlatSquarePrePearl.takeIf { it.value }?.let { PrePearlSelection(destination, false) },
                    slashSkySquarePrePearl.takeIf { it.value }?.let { PrePearlSelection(destination, true) }
                )
            } else {
                emptyList()
            }

            Pre.EQUALS -> when (destination) {
                SHOP_CRATE -> listOfNotNull(
                    equalsFlatShopPrePearl.takeIf { it.value }?.let { PrePearlSelection(destination, false) },
                    equalsSkyShopPrePearl.takeIf { it.value }?.let { PrePearlSelection(destination, true) }
                )
                XC_CRATE -> listOfNotNull(
                    equalsFlatXcPrePearl.takeIf { it.value }?.let { PrePearlSelection(destination, false) },
                    equalsSkyXcPrePearl.takeIf { it.value }?.let { PrePearlSelection(destination, true) }
                )
                else -> emptyList()
            }

            Pre.TRI -> emptyList()
        }
    }

    private fun priorityFor(origin: Pre, missing: Supply): PriorityRule? {
        if (missing == Supply.UNKNOWN) return null
        return when (origin) {
            Pre.SLASH -> when (missing) {
                Supply.SLASH -> PriorityRule(AreaKind.SECOND_SHOP, SHOP_PILE)
                Supply.X, Supply.TRI, Supply.EQUALS, Supply.SHOP, Supply.XC -> PriorityRule(AreaKind.SECOND_SQUARE, SQUARE_PILE)
                Supply.SQUARE -> PriorityRule(AreaKind.SECOND_XC, XC_PILE)
                else -> null
            }

            Pre.X -> when (missing) {
                Supply.X -> PriorityRule(AreaKind.SECOND_SHOP, SHOP_PILE)
                Supply.TRI, Supply.SLASH, Supply.EQUALS, Supply.SHOP, Supply.SQUARE -> PriorityRule(AreaKind.SECOND_XC, XC_PILE)
                Supply.XC -> PriorityRule(AreaKind.SECOND_SQUARE, SQUARE_PILE)
                else -> null
            }

            Pre.TRI -> when (missing) {
                Supply.TRI -> PriorityRule(AreaKind.SECOND_SHOP, SHOP_PILE)
                Supply.X, Supply.SHOP -> PriorityRule(AreaKind.SECOND_XC, XC_PILE)
                Supply.SLASH, Supply.EQUALS -> PriorityRule(AreaKind.SECOND_SQUARE, SQUARE_PILE)
                Supply.SQUARE, Supply.XC -> PriorityRule(AreaKind.SECOND_SHOP, SHOP_PILE)
                else -> null
            }

            Pre.EQUALS -> when (missing) {
                Supply.EQUALS, Supply.SQUARE, Supply.XC -> PriorityRule(AreaKind.SECOND_SHOP, SHOP_PILE)
                Supply.X, Supply.TRI, Supply.SLASH, Supply.SHOP -> PriorityRule(AreaKind.SECOND_SQUARE, SQUARE_PILE)
                else -> null
            }
        }
    }

    private fun uniqueSecondaryDestinationsFor(origin: Pre): List<Destination> {
        return when (origin) {
            Pre.X -> listOf(SHOP_PILE, XC_PILE, SQUARE_PILE)
            Pre.SLASH -> listOf(SHOP_PILE, SQUARE_PILE, XC_PILE)
            Pre.EQUALS -> listOf(SHOP_PILE, SQUARE_PILE)
            Pre.TRI -> listOf(SHOP_PILE, XC_PILE, SQUARE_PILE)
        }
    }

    private fun preFromArea(kind: AreaKind): Pre? = when (kind) {
        AreaKind.MAIN_X -> Pre.X
        AreaKind.MAIN_SLASH -> Pre.SLASH
        AreaKind.MAIN_EQUALS -> Pre.EQUALS
        AreaKind.MAIN_TRI -> Pre.TRI
        else -> null
    }

    private fun vec(x: Double, y: Double, z: Double): Vec3 = Vec3(x, y, z)
    private fun blockPos(vec: Vec3) = net.minecraft.core.BlockPos.containing(vec.x, vec.y, vec.z)

    private fun highestPriorityFor(origin: Pre, areaKind: AreaKind): PriorityRule? {
        for (missing in missingSupplies) {
            val rule = priorityFor(origin, missing) ?: continue
            if (rule.requiredArea == areaKind) {
                return rule
            }
        }
        return null
    }

    private fun currentPriorityLabel(): String {
        val player = Minecraft.getInstance().player ?: return "Waiting"
        if (currentOriginPre == null) {
            currentOriginPre = detectMainPre(player.x, player.z)
        }
        val origin = currentOriginPre ?: return "Detecting pre..."

        if (!hasResolvedMissingSupply()) {
            return uniqueSecondaryDestinationsFor(origin).firstOrNull()?.label ?: "Awaiting no pre"
        }

        val missing = missingSupplies.firstOrNull() ?: return "Awaiting no pre"

        return priorityFor(origin, missing)?.destination?.label ?: "No priority"
    }

    private fun hasResolvedMissingSupply(): Boolean = missingSupplies.size == 1

    private fun hasActiveSecondaryPickup(): Boolean {
        return activeSupplyPhase == SupplyPhase.SECONDARY && lastSupplyProgressIndex >= 0
    }

    private fun detectMissingSupplyFromChat(message: String): Supply? {
        val noMatch = NO_WORD_REGEX.find(message) ?: return null
        val afterNo = message.substring(noMatch.range.last + 1)

        for ((alias, supply) in CHAT_SUPPLY_ALIASES) {
            if (afterNo.contains(alias, ignoreCase = true)) {
                return supply
            }
        }

        return null
    }

    private data class Simulation(val hit: BlockHitResult, val travelTicks: Int)
    private data class PearlSolution(val yaw: Double, val pitch: Double, val travelTicks: Int, val aimPoint: Vec3, val hit: BlockHitResult)
    private data class RenderHint(
        val label: String,
        val destinationLabel: String,
        val solution: PearlSolution,
        val target: Vec3,
        val color: ColorDef,
        val size: Double,
        val targetIndex: Int,
        val throwAtMs: Long,
        val group: AutoPearlGroup
    ) {
        val displayTarget: Vec3 get() = solution.aimPoint
    }

    private data class CachedRender(val area: AreaRegion, val playerPos: Vec3, val hints: List<RenderHint>, val tickBuilt: Long)
    private data class PendingTask(
        val destination: Destination,
        val label: String,
        val kind: HintKind,
        val color: ColorDef,
        val size: Double,
        val preferHighArc: Boolean = false,
        val group: AutoPearlGroup = AutoPearlGroup.MAIN
    )
    private data class PendingBuild(val area: AreaRegion, val playerPos: Vec3, val tasks: ArrayDeque<PendingTask>, val hints: MutableList<RenderHint>, val tickStarted: Long)
    private data class ColorDef(val red: Float, val green: Float, val blue: Float, val alpha: Float)
    private data class PrePearlSelection(val destination: Destination, val preferHighArc: Boolean)

    private data class AreaRegion(val kind: AreaKind, val minX: Int, val maxX: Int, val minZ: Int, val maxZ: Int) {
        fun contains(x: Double, z: Double): Boolean {
            val xi = x.toInt()
            val zi = z.toInt()
            return xi in min(minX, maxX)..max(minX, maxX) &&
                    zi in min(minZ, maxZ)..max(minZ, maxZ)
        }
    }

    private data class Destination(val label: String, val physicalPos: Vec3, val displayLabel: String = label)
    private data class PriorityRule(val requiredArea: AreaKind, val destination: Destination)

    private enum class HintKind { FLAT, SKY, FASTEST, PREPEARL }
    private enum class AutoPearlGroup { MAIN, PRE, SECONDARY }
    private enum class SupplyPhase { NONE, PRIMARY, SECONDARY }
    private enum class AreaKind { MAIN_X, MAIN_SLASH, MAIN_EQUALS, MAIN_TRI, SECOND_SQUARE, SECOND_XC, SECOND_SHOP }

    private enum class Supply {
        X, XC, SQUARE, SLASH, EQUALS, TRI, SHOP, UNKNOWN;

        val display: String
            get() = when (this) {
                X -> "X"
                XC -> "XC"
                SQUARE -> "Square"
                SLASH -> "/"
                EQUALS -> "="
                TRI -> "Tri"
                SHOP -> "Shop"
                UNKNOWN -> "Unknown"
            }

        companion object {
            fun fromChat(name: String): Supply {
                return when (name.lowercase().trim()) {
                    "x" -> X
                    "x cannon", "xc", "xcannon" -> XC
                    "square" -> SQUARE
                    "slash" -> SLASH
                    "equals", "eq" -> EQUALS
                    "triangle", "tri" -> TRI
                    "shop" -> SHOP
                    else -> UNKNOWN
                }
            }
        }
    }

    private enum class Pre(
        val display: String,
        val center: Vec3,
        val radius: Double,
        val mainSupply: Supply,
        val mainDestination: Destination
    ) {
        X("X", Vec3(-142.5, 77.0, -151.0), 30.0, Supply.X, X_PILE),
        SLASH("/", Vec3(-113.5, 77.0, -68.5), 15.0, Supply.SLASH, EQUALS_PILE),
        EQUALS("=", Vec3(-65.5, 76.0, -87.5), 15.0, Supply.EQUALS, SLASH_PILE),
        TRI("Tri", Vec3(-67.5, 77.0, -122.5), 15.0, Supply.TRI, TRI_PILE)
    }
}
