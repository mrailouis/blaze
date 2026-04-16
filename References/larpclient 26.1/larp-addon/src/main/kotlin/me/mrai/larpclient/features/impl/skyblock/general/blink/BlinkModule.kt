package me.mrai.larpclient.features.impl.skyblock.general.blink

import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import com.mojang.blaze3d.platform.InputConstants
import me.mrai.larpclient.mixin.ClientInputAccessor
import me.mrai.larpclient.module.ComponentSetting
import me.mrai.larpclient.module.KeybindSetting
import me.mrai.larpclient.module.Module
import me.mrai.larpclient.module.ModuleCategory
import me.mrai.larpclient.module.SliderSetting
import me.mrai.larpclient.render.WorldRingRenderer
import me.mrai.larpclient.util.LarpChat
import me.mrai.larpclient.util.LarpLog
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.Minecraft
import net.minecraft.client.player.ClientInput
import net.minecraft.core.BlockPos
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket
import net.minecraft.network.protocol.game.ServerboundClientTickEndPacket
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket
import net.minecraft.network.protocol.game.ServerboundPlayerInputPacket
import net.minecraft.world.entity.player.Input
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec2
import net.minecraft.world.phys.Vec3
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.io.path.extension
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.notExists

object BlinkModule : Module(
    name = "Blink",
    description = "Records blink inputs and replays them through buffered packets.",
    category = ModuleCategory.SKYBLOCK_GENERAL
) {
    private const val TRAIL_STEP = 0.18
    private const val TRAIL_HALF = 0.03
    private const val ENTER_COOLDOWN_MS = 250L

    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val blinkDir: Path = FabricLoader.getInstance().configDir.resolve("larpclient/blink")
    private val routesDir: Path = blinkDir.resolve("routes")
    private val ringsFile: Path = blinkDir.resolve("rings.json")

    private val recordKey = KeybindSetting("Record Key")
    private val routePrefix = ComponentSetting("Route Prefix", "larp")
    private val maxRoutePackets = ComponentSetting("Max Route Packets", "17")
    private val ringRed = SliderSetting("Ring Red", 255.0, 0.0, 255.0, 1.0)
    private val ringGreen = SliderSetting("Ring Green", 105.0, 0.0, 255.0, 1.0)
    private val ringBlue = SliderSetting("Ring Blue", 180.0, 0.0, 255.0, 1.0)
    private val ringAlpha = SliderSetting("Ring Alpha", 220.0, 0.0, 255.0, 1.0)
    private val lineRed = SliderSetting("Line Red", 120.0, 0.0, 255.0, 1.0)
    private val lineGreen = SliderSetting("Line Green", 220.0, 0.0, 255.0, 1.0)
    private val lineBlue = SliderSetting("Line Blue", 255.0, 0.0, 255.0, 1.0)
    private val lineAlpha = SliderSetting("Line Alpha", 190.0, 0.0, 255.0, 1.0)

    private val routes = linkedMapOf<String, BlinkRoute>()
    private val rings = mutableListOf<BlinkRing>()
    private val queue = ConcurrentLinkedQueue<Packet<*>>()

    private var recordWasPressed = false
    private var armedRingId: String? = null
    private var recording: ActiveRecording? = null
    private var previewPlayback: PreviewPlayback? = null
    private var activePlayback: ActivePlayback? = null
    private var bufferingPackets = false
    private var flushingPackets = false
    private var lastInputPacket: ServerboundPlayerInputPacket? = null
    private var packetCount = 0
    private var lastTriggerMs = 0L
    private var detachedCameraState: DetachedCameraState? = null

    init {
        settings += listOf(
            recordKey,
            routePrefix,
            maxRoutePackets,
            ringRed, ringGreen, ringBlue, ringAlpha,
            lineRed, lineGreen, lineBlue, lineAlpha
        )
        load()
    }

    override fun onDisable() {
        resetRuntime(flushBufferedPackets = true)
    }

    override fun onTick() {
        val client = Minecraft.getInstance()
        flushPlaybackIfQueued()
        handleRecordKey(client)
        tickPreviewState()
        tickPlaybackState()
        handleRingInteraction(client)
    }

    fun onInputTick(clientInput: ClientInput) {
        val player = Minecraft.getInstance().player ?: return
        val accessor = clientInput as ClientInputAccessor
        val currentInput = accessor.`larpclient$getKeyPresses`()

        recording?.let { active ->
            if (activePlayback == null) {
                active.frames += BlinkFrame(
                    yaw = player.yRot,
                    pitch = player.xRot,
                    forward = currentInput.forward(),
                    backward = currentInput.backward(),
                    left = currentInput.left(),
                    right = currentInput.right(),
                    jump = currentInput.jump(),
                    sneak = currentInput.shift(),
                    sprint = currentInput.sprint(),
                    x = player.x,
                    y = player.y,
                    z = player.z
                )
                val maxPackets = configuredMaxPackets()
                if (active.frames.size >= maxPackets) {
                    stopRecording(save = true, reason = "Reached $maxPackets packets and saved")
                }
            }
        }

        val playback = activePlayback ?: return
        if (hasManualMovement(currentInput)) {
            LarpChat.send("Cancelling blink movement.")
            resetRuntime(flushBufferedPackets = false)
            return
        }

        if (playback.paused) {
            accessor.`larpclient$setKeyPresses`(Input.EMPTY)
            accessor.`larpclient$setMoveVector`(Vec2.ZERO)
            return
        }

        if (playback.tickIndex >= playback.frames.size) {
            accessor.`larpclient$setKeyPresses`(Input.EMPTY)
            accessor.`larpclient$setMoveVector`(Vec2.ZERO)
            return
        }

        val frame = playback.frames[playback.tickIndex]
        accessor.`larpclient$setKeyPresses`(frame.toInput())
        accessor.`larpclient$setMoveVector`(frame.toMoveVector())
        player.setYRot(frame.yaw)
        player.setXRot(frame.pitch)
        player.setYHeadRot(frame.yaw)
        player.setYBodyRot(frame.yaw)
        playback.tickIndex++

        if (playback.tickIndex >= playback.frames.size) {
            accessor.`larpclient$setKeyPresses`(Input.EMPTY)
            accessor.`larpclient$setMoveVector`(Vec2.ZERO)
        }
    }

    fun onSendPacket(packet: Packet<*>): Boolean {
        if (!enabled || !bufferingPackets || flushingPackets) {
            return false
        }

        if (packet is ServerboundPlayerInputPacket) {
            val last = lastInputPacket
            if (last != null && last.input() == packet.input()) {
                return true
            }
            lastInputPacket = packet
        }

        val playback = activePlayback
        if (playback != null && (packetCount >= playback.packetLimit || playback.isDonePlaying())) {
            if (packet is ServerboundMovePlayerPacket || packet is ServerboundPlayerInputPacket) {
                return true
            }

            if (packet is ServerboundAcceptTeleportationPacket) {
                flushPlayback(playback)
                return false
            }
        }

        var shouldBuffer = true
        if (packet is ServerboundClientTickEndPacket) {
            packetCount++
            if (playback != null) {
                if (packetCount >= playback.packetLimit) {
                    shouldBuffer = false
                    packetCount--
                }
            } else if (packetCount >= configuredMaxPackets()) {
                flushQueue()
                bufferingPackets = false
                packetCount = 0
                return false
            }
        }

        if (!shouldBuffer) {
            return false
        }
        queue.add(packet)
        return true
    }

    fun shouldCancelAttack(): Boolean {
        if (!enabled || activePlayback != null || flushingPackets) {
            return false
        }

        val preview = previewPlayback
        if (preview != null && !preview.isFinished()) {
            return true
        }

        val ring = currentArmedRing() ?: return false
        val now = System.currentTimeMillis()
        if (now - lastTriggerMs < ENTER_COOLDOWN_MS) {
            return true
        }

        if (preview != null) {
            detachedCameraState = preview.currentCameraState()
            previewPlayback = null
        }
        triggerRing(ring)
        lastTriggerMs = now
        return true
    }

    fun shouldShowBlinkHud(): Boolean {
        return enabled && (bufferingPackets || queue.isNotEmpty())
    }

    fun getBlinkHudText(preview: Boolean = false): String {
        return if (preview) "Blinking" else "Blinking"
    }

    fun render(context: LevelRenderContext) {
        if (!enabled || rings.isEmpty()) return

        val renderRings = mutableListOf<WorldRingRenderer.RingSpec>()
        val ringStyle = ringStyle()

        for (ring in rings) {
            val route = routes[ring.routeName] ?: continue
            if (route.frames.isEmpty()) continue

            val frameCount = ring.packetCount.coerceIn(1, route.frames.size)
            val path = route.frames.take(frameCount)
            val endBlock = floorBlock(path.last())

            renderRings += ringSpec(ring.triggerBlock(), ringStyle)
            renderRings += ringSpec(endBlock, ringStyle)
        }

        if (renderRings.isNotEmpty()) {
            WorldRingRenderer.render(context, renderRings)
        }
    }

    fun onWorldChange() {
        resetRuntime(flushBufferedPackets = false)
    }

    fun detachedCameraPosition(): Vec3? = previewPlayback?.currentCameraState()?.position ?: detachedCameraState?.position

    fun detachedCameraYaw(): Float? = previewPlayback?.currentCameraState()?.yaw ?: detachedCameraState?.yaw

    fun detachedCameraPitch(): Float? = previewPlayback?.currentCameraState()?.pitch ?: detachedCameraState?.pitch

    fun addRingAtPlayer(routeInput: String, requestedPackets: Int): Boolean {
        val client = Minecraft.getInstance()
        val player = client.player ?: return false
        val routeName = normalizeRouteName(routeInput)
        val route = routes[routeName] ?: return false
        if (route.frames.isEmpty()) return false

        val packetCount = requestedPackets.coerceAtLeast(1).coerceAtMost(configuredMaxPackets()).coerceAtMost(route.frames.size)
        val triggerBlock = standingBlock(player)

        rings.removeIf { it.triggerBlock() == triggerBlock }
        rings += BlinkRing(
            id = UUID.randomUUID().toString(),
            triggerX = triggerBlock.x,
            triggerY = triggerBlock.y,
            triggerZ = triggerBlock.z,
            routeName = routeName,
            packetCount = packetCount
        )
        saveRings()
        LarpChat.send("Saved blink ring for ${routeName}.json with $packetCount packets.")
        return true
    }

    fun addRingFromCommand(routeInput: String, requestedPackets: Int): Boolean {
        return addRingAtPlayer(routeInput, requestedPackets)
    }

    fun availableRoutes(): List<String> = routes.keys.sorted().map { "$it.json" }

    private fun handleRecordKey(client: Minecraft) {
        val key = recordKey.key
        val pressed = key != InputConstants.UNKNOWN.getValue() &&
            client.screen == null &&
            InputConstants.isKeyDown(client.window, key)

        if (pressed && !recordWasPressed) {
            if (recording == null) {
                startRecording()
            } else {
                stopRecording(save = true, reason = "Saved")
            }
        }

        recordWasPressed = pressed
    }

    private fun startRecording() {
        val routeName = nextRouteName()
        recording = ActiveRecording(routeName, mutableListOf())
        LarpChat.send("Started blink recording ${routeName}.json")
    }

    private fun stopRecording(save: Boolean, reason: String) {
        val active = recording ?: return
        recording = null

        if (!save) {
            LarpChat.send("Blink recording cancelled.")
            return
        }

        if (active.frames.isEmpty()) {
            LarpChat.send("Blink route discarded, move at least once before stopping.")
            return
        }

        val route = BlinkRoute(active.name, active.frames.toMutableList())
        routes[route.name] = route
        saveRoute(route)
        LarpChat.send("$reason blink route ${route.name}.json with ${route.frames.size} packets.")
    }

    private fun handleRingInteraction(client: Minecraft) {
        val player = client.player ?: run {
            armedRingId = null
            previewPlayback = null
            detachedCameraState = null
            return
        }
        if (client.screen != null) {
            armedRingId = null
            previewPlayback = null
            detachedCameraState = null
            return
        }
        if (activePlayback != null) {
            return
        }

        val currentRing = rings.firstOrNull { it.triggerBlock() == standingBlock(player) }
        if (currentRing == null) {
            armedRingId = previewPlayback?.ringId
            if (previewPlayback == null) {
                detachedCameraState = null
            }
            return
        }

        armedRingId = currentRing.id
        if (previewPlayback?.ringId == currentRing.id) {
            return
        }

        val route = routes[currentRing.routeName] ?: return
        if (route.frames.isEmpty()) {
            return
        }

        val packetLimit = currentRing.packetCount.coerceIn(1, route.frames.size)
        previewPlayback = PreviewPlayback(
            ringId = currentRing.id,
            frames = route.frames.take(packetLimit).map { it.copy() },
            tickIndex = 0
        )
        detachedCameraState = null
    }

    private fun triggerRing(ring: BlinkRing) {
        val route = routes[ring.routeName] ?: run {
            LarpChat.send("Missing blink route ${ring.routeName}.json")
            return
        }
        if (activePlayback != null || flushingPackets) {
            return
        }

        val packetLimit = ring.packetCount.coerceIn(1, route.frames.size)
        queue.clear()
        lastInputPacket = null
        bufferingPackets = true
        packetCount = 0
        activePlayback = ActivePlayback(
            routeName = ring.routeName,
            frames = route.frames.take(packetLimit).map { it.copy() },
            packetLimit = packetLimit,
            tickIndex = 0,
            ticks = 0
        )
        armedRingId = null
        LarpChat.send("Blinking ${ring.routeName}.json with $packetLimit packets.")
    }

    private fun tickPlaybackState() {
        val playback = activePlayback ?: return
        val player = Minecraft.getInstance().player ?: return

        playback.ticks++
        if (playback.ticks <= playback.packetLimit + 1) {
            playback.endPos = player.position()
            playback.endVelocity = player.deltaMovement
        }

        if (playback.ticks == playback.packetLimit + 1) {
            player.setPos(playback.endPos.x, playback.endPos.y, playback.endPos.z)
            player.setOldPosAndRot()
            player.setDeltaMovement(Vec3.ZERO)
            playback.paused = true
            playback.flushQueued = true
            return
        }
    }

    private fun tickPreviewState() {
        val preview = previewPlayback ?: return
        if (preview.tickIndex < preview.frames.lastIndex) {
            preview.tickIndex++
        }
    }

    private fun flushQueue() {
        val connection = Minecraft.getInstance().connection ?: run {
            queue.clear()
            lastInputPacket = null
            return
        }

        flushingPackets = true
        try {
            while (queue.isNotEmpty()) {
                val packet = queue.poll() ?: break
                connection.connection.send(packet, null, true)
            }
        } finally {
            flushingPackets = false
            lastInputPacket = null
        }
    }

    private fun flushPlaybackIfQueued() {
        val playback = activePlayback ?: return
        if (!playback.flushQueued) {
            return
        }
        flushPlayback(playback)
    }

    private fun flushPlayback(playback: ActivePlayback) {
        val player = Minecraft.getInstance().player
        activePlayback = null
        bufferingPackets = false
        packetCount = 0
        playback.flushQueued = false
        detachedCameraState = null

        if (player != null) {
            player.setPos(playback.endPos.x, playback.endPos.y, playback.endPos.z)
            player.setOldPosAndRot()
            player.setDeltaMovement(playback.endVelocity)
        }

        flushQueue()
        LarpChat.send("Blinked ${playback.routeName}.json with ${playback.packetLimit} packets.")
    }

    private fun resetRuntime(flushBufferedPackets: Boolean) {
        recording = null
        armedRingId = null
        recordWasPressed = false
        previewPlayback = null
        activePlayback = null
        bufferingPackets = false
        packetCount = 0
        detachedCameraState = null

        if (flushBufferedPackets) {
            flushQueue()
        } else {
            queue.clear()
            lastInputPacket = null
            flushingPackets = false
        }
    }

    private fun ringStyle(): RingStyle {
        val r = (ringRed.value / 255.0).toFloat()
        val g = (ringGreen.value / 255.0).toFloat()
        val b = (ringBlue.value / 255.0).toFloat()
        val a = (ringAlpha.value / 255.0).toFloat()
        return RingStyle(
            red = r,
            green = g,
            blue = b,
            fillAlpha = a * 0.22f,
            outlineAlpha = a
        )
    }

    private fun ringSpec(block: BlockPos, style: RingStyle): WorldRingRenderer.RingSpec {
        return WorldRingRenderer.RingSpec(
            centerX = block.x + 0.5,
            centerY = block.y + 1.01,
            centerZ = block.z + 0.5,
            radius = 0.46,
            thickness = 0.06,
            height = 0.0,
            red = style.red,
            green = style.green,
            blue = style.blue,
            fillAlpha = style.fillAlpha,
            outlineAlpha = style.outlineAlpha
        )
    }

    private fun standingBlock(player: net.minecraft.client.player.LocalPlayer): BlockPos {
        return BlockPos.containing(player.x, player.y - 0.2, player.z)
    }

    private fun floorBlock(frame: BlinkFrame): BlockPos {
        return BlockPos.containing(frame.x, frame.y - 0.2, frame.z)
    }

    private fun hasManualMovement(input: Input): Boolean {
        return input.forward() || input.backward() || input.left() || input.right() || input.jump() || input.shift()
    }

    private fun normalizeRouteName(name: String): String {
        return name.trim().removeSuffix(".json")
    }

    private fun nextRouteName(): String {
        val prefix = normalizedPrefix()
        val pattern = Regex("^${Regex.escape(prefix)}-(\\d+)$")
        val next = routes.keys.asSequence()
            .mapNotNull { name -> pattern.matchEntire(name)?.groupValues?.get(1)?.toIntOrNull() }
            .maxOrNull()
            ?.plus(1)
            ?: 1
        return "$prefix-$next"
    }

    private fun normalizedPrefix(): String {
        val raw = routePrefix.text.trim()
        val sanitized = raw.ifBlank { "larp" }
            .lowercase()
            .map { ch ->
                when {
                    ch.isLetterOrDigit() -> ch
                    ch == '-' || ch == '_' -> ch
                    else -> '-'
                }
            }
            .joinToString("")
            .replace(Regex("-+"), "-")
            .trim('-')

        val value = sanitized.ifBlank { "larp" }
        if (routePrefix.text != value) {
            routePrefix.text = value
        }
        return value
    }

    private fun configuredMaxPackets(): Int {
        val parsed = maxRoutePackets.text.trim().toIntOrNull()?.coerceAtLeast(1) ?: 17
        val normalized = parsed.toString()
        if (maxRoutePackets.text != normalized) {
            maxRoutePackets.text = normalized
        }
        return parsed
    }

    private fun load() {
        try {
            if (blinkDir.notExists()) Files.createDirectories(blinkDir)
            if (routesDir.notExists()) Files.createDirectories(routesDir)
        } catch (throwable: Throwable) {
            LarpLog.warn("Failed to initialise blink storage under $blinkDir: ${throwable.message ?: throwable.javaClass.simpleName}")
        }

        loadRoutes()
        loadRings()
    }

    private fun loadRoutes() {
        routes.clear()
        try {
            if (routesDir.notExists()) return
            Files.list(routesDir).use { stream ->
                stream.filter { Files.isRegularFile(it) && it.extension.equals("json", ignoreCase = true) }
                    .forEach { path ->
                        runCatching {
                            val route = gson.fromJson(Files.readString(path), BlinkRoute::class.java) ?: return@runCatching
                            val name = normalizeRouteName(route.name.ifBlank { path.nameWithoutExtension })
                            if (route.frames.isNotEmpty()) {
                                routes[name] = route.copy(name = name, frames = route.frames.toMutableList())
                            }
                        }
                    }
            }
        } catch (throwable: Throwable) {
            LarpLog.warn("Failed to load blink routes from $routesDir: ${throwable.message ?: throwable.javaClass.simpleName}")
        }
    }

    private fun loadRings() {
        rings.clear()
        try {
            if (ringsFile.notExists()) return
            val type = object : TypeToken<List<BlinkRing>>() {}.type
            val loaded: List<BlinkRing> = gson.fromJson(Files.readString(ringsFile), type) ?: emptyList()
            rings += loaded.map {
                it.copy(
                    routeName = normalizeRouteName(it.routeName),
                    packetCount = it.packetCount.coerceAtLeast(1).coerceAtMost(configuredMaxPackets())
                )
            }
        } catch (throwable: Throwable) {
            LarpLog.warn("Failed to load blink rings from $ringsFile: ${throwable.message ?: throwable.javaClass.simpleName}")
        }
    }

    private fun saveRoute(route: BlinkRoute) {
        try {
            if (routesDir.notExists()) Files.createDirectories(routesDir)
            Files.writeString(routesDir.resolve("${route.name}.json"), gson.toJson(route))
        } catch (throwable: Throwable) {
            LarpLog.warn("Failed to save blink route '${route.name}' to $routesDir: ${throwable.message ?: throwable.javaClass.simpleName}")
        }
    }

    private fun saveRings() {
        try {
            if (blinkDir.notExists()) Files.createDirectories(blinkDir)
            Files.writeString(ringsFile, gson.toJson(rings))
        } catch (throwable: Throwable) {
            LarpLog.warn("Failed to save blink rings to $ringsFile: ${throwable.message ?: throwable.javaClass.simpleName}")
        }
    }

    data class BlinkFrame(
        var yaw: Float = 0f,
        var pitch: Float = 0f,
        var forward: Boolean = false,
        var backward: Boolean = false,
        var left: Boolean = false,
        var right: Boolean = false,
        var jump: Boolean = false,
        var sneak: Boolean = false,
        var sprint: Boolean = false,
        var x: Double = 0.0,
        var y: Double = 0.0,
        var z: Double = 0.0
    ) {
        fun toInput(): Input = Input(forward, backward, left, right, jump, sneak, sprint)

        fun toMoveVector(): Vec2 = Vec2(impulse(left, right), impulse(forward, backward))

        fun position(): Vec3 = Vec3(x, y, z)

        private fun impulse(positive: Boolean, negative: Boolean): Float {
            return when {
                positive == negative -> 0f
                positive -> 1f
                else -> -1f
            }
        }
    }

    data class BlinkRoute(
        var name: String = "",
        var frames: MutableList<BlinkFrame> = mutableListOf()
    )

    data class BlinkRing(
        var id: String = UUID.randomUUID().toString(),
        var triggerX: Int = 0,
        var triggerY: Int = 0,
        var triggerZ: Int = 0,
        var routeName: String = "",
        var packetCount: Int = 1
    ) {
        fun triggerBlock(): BlockPos = BlockPos(triggerX, triggerY, triggerZ)
    }

    private data class ActiveRecording(
        val name: String,
        val frames: MutableList<BlinkFrame>
    )

    private data class ActivePlayback(
        val routeName: String,
        val frames: List<BlinkFrame>,
        val packetLimit: Int,
        var tickIndex: Int,
        var ticks: Int,
        var paused: Boolean = false,
        var flushQueued: Boolean = false,
        var endPos: Vec3 = Vec3.ZERO,
        var endVelocity: Vec3 = Vec3.ZERO
    ) {
        fun isDonePlaying(): Boolean = ticks > packetLimit
    }

    private data class PreviewPlayback(
        val ringId: String,
        val frames: List<BlinkFrame>,
        var tickIndex: Int
    ) {
        fun isFinished(): Boolean = tickIndex >= frames.lastIndex

        fun currentCameraState(): DetachedCameraState? {
            val frame = frames.getOrNull(tickIndex) ?: return null
            return DetachedCameraState(
                position = frame.cameraPosition(),
                yaw = frame.yaw,
                pitch = frame.pitch
            )
        }
    }

    private data class DetachedCameraState(
        val position: Vec3,
        val yaw: Float,
        val pitch: Float
    )

    private data class RingStyle(
        val red: Float,
        val green: Float,
        val blue: Float,
        val fillAlpha: Float,
        val outlineAlpha: Float
    )

    private fun currentArmedRing(): BlinkRing? {
        val armedId = armedRingId ?: return null
        return rings.firstOrNull { it.id == armedId }
    }

    private fun List<BlinkFrame>.velocityAt(index: Int): Vec3 {
        val current = getOrNull(index)?.position() ?: return Vec3.ZERO
        val previous = getOrNull(index - 1)?.position() ?: return Vec3.ZERO
        return current.subtract(previous)
    }

    private fun BlinkFrame.cameraPosition(): Vec3 {
        return position().add(0.0, previewEyeHeight(), 0.0)
    }

    private fun previewEyeHeight(): Double {
        return Minecraft.getInstance().player?.eyeHeight?.toDouble() ?: 1.62
    }
}
