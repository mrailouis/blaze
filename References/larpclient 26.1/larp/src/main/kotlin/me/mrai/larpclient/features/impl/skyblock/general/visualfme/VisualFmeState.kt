package me.mrai.larpclient.features.impl.skyblock.general.visualfme

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import me.mrai.larpclient.features.impl.skyblock.general.visualfme.util.LegacyStateParser
import me.mrai.larpclient.util.LarpBranding
import me.mrai.larpclient.util.LarpLog
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.Items
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.level.block.*
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.properties.Property
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3
import org.lwjgl.glfw.GLFW
import java.nio.file.Files
import java.nio.file.Path
import java.util.Comparator
import java.util.Properties
import kotlin.random.Random

object VisualFmeState {
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

    private val configDir = FabricLoader.getInstance().configDir.resolve("larpclient/visualfme")
    private val configFile = configDir.resolve("floorsConfig.json")
    private val schitzoFile = configDir.resolve("schitzo.json")
    private val stateFile = configDir.resolve("visualfme.properties")
    private const val defaultConfigResource = "/assets/larpclient/default/visualfme/floorsConfig.json"

    data class VisualPlacement(
        val pos: BlockPos,
        val state: BlockState
    )

    private enum class BlockFamily {
        FULL_CUBE,
        LIQUID_PORTAL,
        GLASS_BLOCK,
        PANE,
        FENCE,
        FENCE_GATE,
        WALL,
        STAIRS,
        SLAB,
        TRAPDOOR,
        DOOR,
        LEAVES,
        BUTTON,
        PRESSURE_PLATE,
        CARPET,
        TORCH,
        PLANT,
        GENERIC
    }

    private enum class BiomeTheme(val displayName: String) {
        PLAINS("Plains"),
        DESERT("Desert"),
        JUNGLE("Jungle"),
        TAIGA("Taiga"),
        SNOWY("Snowy"),
        SWAMP("Swamp"),
        OCEAN("Ocean"),
        MUSHROOM("Mushroom"),
        CHERRY("Cherry"),
        NETHER("Nether"),
        END("End")
    }

    private var initialized = false
    private var enabled = false
    private var editMode = false
    private var schitzoMode = false
    private var selectedFloor = "7"
    private var maxRenderDistance = 128
    private var selectedBlockId = "minecraft:glass"

    private var placements: MutableList<VisualPlacement> = mutableListOf()
    private var placementMap: MutableMap<BlockPos, BlockState> = mutableMapOf()
    private var schitzoMappings: MutableMap<String, String> = mutableMapOf()
    private var schitzoTheme: BiomeTheme? = null

    private var lastAttack = false
    private var lastUse = false
    private var lastPick = false
    private var lastRotate = false
    private val overrideWorldLookups = ThreadLocal.withInitial { false }
    private var needsFullStateRebuild = true

    private val random = Random(System.currentTimeMillis())
    private val familyCandidates by lazy(::buildFamilyCandidates)
    private val biomeFamilyCandidates by lazy(::buildBiomeFamilyCandidates)

    fun initialize() {
        if (initialized) return
        initialized = true
        runCatching {
            Files.createDirectories(configDir)
            loadSettings()
            ensureConfigExists()
            loadSchitzoMappings()
            reload()
        }.onFailure { throwable ->
            LarpLog.error("Failed to initialize Visual FME.", throwable)
        }
    }

    fun setEnabled(value: Boolean) {
        initialize()
        if (enabled == value) return
        enabled = value
        if (enabled) {
            if (schitzoMode) {
                ensureSchitzoMappingsReady()
            } else {
                refreshLoadedStates()
            }
        }
        saveSettings()
        refreshWorldRenderer()
    }

    fun isEnabled(): Boolean = enabled

    fun setEditMode(value: Boolean) {
        initialize()
        if (editMode == value) return
        editMode = value
        saveSettings()
    }

    fun isEditMode(): Boolean = editMode

    fun setSchitzoMode(value: Boolean) {
        initialize()
        if (schitzoMode == value) return
        schitzoMode = value
        if (schitzoMode) {
            ensureSchitzoMappingsReady()
        } else {
            needsFullStateRebuild = true
        }
        saveSettings()
        refreshWorldRenderer()
    }

    fun isSchitzoMode(): Boolean = schitzoMode

    fun randomizeSchitzoMappings() {
        initialize()
        schitzoMappings = buildRandomMappings().toMutableMap()
        saveSchitzoMappings()
        refreshWorldRenderer()
        display("Randomized schitzo mappings for ${schitzoMappings.size} block types.", LarpBranding.AQUA)
    }

    fun niceRandomizeSchitzoMappings() {
        initialize()
        val (theme, mappings) = buildNiceRandomMappings()
        schitzoTheme = theme
        schitzoMappings = mappings.toMutableMap()
        saveSchitzoMappings()
        refreshWorldRenderer()
        display("Nice-randomized schitzo mappings using ${theme.displayName}.", LarpBranding.AQUA)
    }

    fun schitzoStatusLine(): String {
        return if (schitzoMode) {
            "Schitzo active | ${schitzoTheme?.displayName ?: "Random"} | Mappings ${schitzoMappings.size}"
        } else {
            "Schitzo off | Mappings ${schitzoMappings.size}"
        }
    }

    fun setFloor(value: String) {
        initialize()
        val sanitized = value.trim().ifBlank { "7" }
        if (selectedFloor == sanitized) return
        selectedFloor = sanitized
        needsFullStateRebuild = true
        saveSettings()
        reload()
    }

    fun floor(): String = selectedFloor

    fun setMaxRenderDistance(value: Int) {
        initialize()
        val clamped = value.coerceIn(16, 512)
        if (maxRenderDistance == clamped) return
        maxRenderDistance = clamped
        saveSettings()
        refreshWorldRenderer()
    }

    fun maxRenderDistance(): Int = maxRenderDistance

    fun setSelectedBlock(id: String): Boolean {
        initialize()
        val normalized = normalizeBlockId(id)
        val resource = Identifier.tryParse(normalized) ?: return false
        if (!BuiltInRegistries.BLOCK.containsKey(resource)) return false
        if (selectedBlockId == normalized) return true
        selectedBlockId = normalized
        saveSettings()
        return true
    }

    fun selectedBlockId(): String = selectedBlockId

    fun getReplacementState(pos: BlockPos): BlockState? {
        if (!enabled) return null
        return if (schitzoMode) getSchitzoReplacementState(pos) else placementMap[pos.immutable()]
    }

    fun getWorldLookupOverride(pos: BlockPos): BlockState? {
        if (!overrideWorldLookups.get()) return null
        return if (schitzoMode) getSchitzoReplacementState(pos) else placementMap[pos.immutable()]
    }

    fun statusLine(): String {
        return if (schitzoMode) {
            "Floor $selectedFloor | Schitzo | Mappings ${schitzoMappings.size}"
        } else {
            "Floor $selectedFloor | Block $selectedBlockId | Range all | Loaded ${placements.size}"
        }
    }

    fun controlsLine(): String = "Edit: RMB place ghost, LMB air, MMB pick, . rotate"

    fun onClientTick() {
        initialize()
        if (enabled && !schitzoMode && needsFullStateRebuild) {
            Minecraft.getInstance().level?.let { level ->
                rebuildAllPlacementStates(level)
            }
            needsFullStateRebuild = false
            refreshWorldRenderer()
        }
        if (!enabled || !editMode || schitzoMode) {
            resetInputTracking()
            return
        }

        val client = Minecraft.getInstance()
        if (client.screen != null) {
            resetInputTracking()
            return
        }

        val hit = resolveEditHit(client) ?: run {
            resetInputTracking()
            return
        }
        val window = client.window.handle()

        val attackDown = client.options.keyAttack.isDown
        val useDown = client.options.keyUse.isDown
        val pickDown = GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_MIDDLE) == GLFW.GLFW_PRESS
        val rotateDown = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_PERIOD) == GLFW.GLFW_PRESS

        if (useDown && !lastUse) handlePlace(client, hit)
        if (attackDown && !lastAttack) handleBreak(client, hit)
        if (pickDown && !lastPick) handlePick(client, hit)
        if (rotateDown && !lastRotate) handleRotate(client, hit)

        lastAttack = attackDown
        lastUse = useDown
        lastPick = pickDown
        lastRotate = rotateDown
    }

    fun resetInputTracking() {
        val client = Minecraft.getInstance()
        lastAttack = client.options.keyAttack.isDown
        lastUse = client.options.keyUse.isDown
        lastPick = false
        lastRotate = false
    }

    private fun handlePlace(client: Minecraft, hit: BlockHitResult) {
        val result = createPlacementState(hit) ?: run {
            display("Cannot place a visual block there.", LarpBranding.RED)
            return
        }
        val (pos, state) = result
        setVisualBlock(pos, state)
        client.player?.swing(InteractionHand.MAIN_HAND)
        display("Placed $selectedBlockId at ${pos.x}, ${pos.y}, ${pos.z}.", LarpBranding.GREEN)
    }

    private fun handleBreak(client: Minecraft, hit: BlockHitResult) {
        val pos = hit.blockPos.immutable()
        setVisualBlock(pos, Blocks.AIR.defaultBlockState())
        client.player?.swing(InteractionHand.MAIN_HAND)
        display("Set visual air at ${pos.x}, ${pos.y}, ${pos.z}.", LarpBranding.RED)
    }

    private fun handlePick(client: Minecraft, hit: BlockHitResult) {
        val picked = pickBlockIdAt(hit.blockPos) ?: return
        if (setSelectedBlock(picked)) {
            client.player?.swing(InteractionHand.MAIN_HAND)
            display("Selected $picked.", LarpBranding.AQUA)
        }
    }

    private fun handleRotate(client: Minecraft, hit: BlockHitResult) {
        if (!rotateVisualBlock(hit.blockPos)) return
        client.player?.swing(InteractionHand.MAIN_HAND)
        display("Rotated ${hit.blockPos.x}, ${hit.blockPos.y}, ${hit.blockPos.z}.", LarpBranding.AQUA)
    }

    private fun reload() {
        placements = loadPlacementsForFloor(selectedFloor).toMutableList()
        placementMap = placements.associate { it.pos.immutable() to it.state }.toMutableMap()
        needsFullStateRebuild = true
        if (!schitzoMode) {
            refreshLoadedStates()
        }
        refreshWorldRenderer()
    }

    private fun pickBlockIdAt(pos: BlockPos): String? {
        val immutable = pos.immutable()
        val state = placementMap[immutable] ?: Minecraft.getInstance().level?.getBlockState(immutable) ?: return null
        return BuiltInRegistries.BLOCK.getKey(state.block).toString()
    }

    private fun createPlacementState(hit: BlockHitResult): Pair<BlockPos, BlockState>? {
        val client = Minecraft.getInstance()
        val player = client.player ?: return null
        val level = client.level ?: return null

        val selectedBlock = getSelectedBlock()
        val clickedPos = hit.blockPos.immutable()
        val clickedState = effectiveState(level, clickedPos)
        val targetPos = if (clickedState.canBeReplaced()) clickedPos else clickedPos.relative(hit.direction).immutable()

        if (selectedBlock == Blocks.AIR) {
            return targetPos to Blocks.AIR.defaultBlockState()
        }

        val placementHit = if (targetPos == clickedPos) {
            hit
        } else {
            BlockHitResult(hit.location, hit.direction, clickedPos, hit.isInside)
        }
        val placementContext = BlockPlaceContext(player, InteractionHand.MAIN_HAND, player.mainHandItem, placementHit)
        val state = runCatching {
            selectedBlock.getStateForPlacement(placementContext) ?: selectedBlock.defaultBlockState()
        }.getOrDefault(selectedBlock.defaultBlockState())

        return targetPos to state
    }

    private fun resolveEditHit(client: Minecraft): BlockHitResult? {
        val player = client.player ?: return null
        val level = client.level ?: return null
        val eye = player.eyePosition
        val look = player.lookAngle
        val reach = 6.0
        val step = 0.1

        var distance = step
        var previousPos = BlockPos.containing(eye)
        var fallbackPos = previousPos

        while (distance <= reach) {
            val sample = eye.add(look.scale(distance))
            val pos = BlockPos.containing(sample)
            fallbackPos = pos
            if (pos != previousPos) {
                val effective = effectiveState(level, pos)
                if (!effective.isAir) {
                    val enteredFrom = directionBetween(previousPos, pos)
                    val hitSide = enteredFrom?.opposite ?: closestFacing(look)
                    return BlockHitResult(sample, hitSide, pos.immutable(), false)
                }
                previousPos = pos
            }
            distance += step
        }

        val sample = eye.add(look.scale(reach))
        val missDirection = closestFacing(look)
        return BlockHitResult(sample, missDirection, fallbackPos.immutable(), false)
    }

    private fun effectiveState(level: ClientLevel, pos: BlockPos): BlockState {
        return getReplacementState(pos) ?: level.getBlockState(pos)
    }

    private fun directionBetween(from: BlockPos, to: BlockPos): Direction? {
        val dx = to.x - from.x
        val dy = to.y - from.y
        val dz = to.z - from.z
        return when {
            dx == 1 && dy == 0 && dz == 0 -> Direction.EAST
            dx == -1 && dy == 0 && dz == 0 -> Direction.WEST
            dx == 0 && dy == 1 && dz == 0 -> Direction.UP
            dx == 0 && dy == -1 && dz == 0 -> Direction.DOWN
            dx == 0 && dy == 0 && dz == 1 -> Direction.SOUTH
            dx == 0 && dy == 0 && dz == -1 -> Direction.NORTH
            else -> null
        }
    }

    private fun closestFacing(look: Vec3): Direction {
        val absX = kotlin.math.abs(look.x)
        val absY = kotlin.math.abs(look.y)
        val absZ = kotlin.math.abs(look.z)
        return when {
            absY >= absX && absY >= absZ -> if (look.y >= 0.0) Direction.UP else Direction.DOWN
            absX >= absZ -> if (look.x >= 0.0) Direction.EAST else Direction.WEST
            else -> if (look.z >= 0.0) Direction.SOUTH else Direction.NORTH
        }
    }

    private fun getSelectedBlock(): Block {
        val resource = Identifier.tryParse(selectedBlockId)
        return if (resource != null && BuiltInRegistries.BLOCK.containsKey(resource)) {
            BuiltInRegistries.BLOCK.getValue(resource)
        } else {
            Blocks.GLASS
        }
    }

    private fun setVisualBlock(pos: BlockPos, state: BlockState) {
        val immutable = pos.immutable()
        placementMap[immutable] = state
        placements.removeIf { it.pos == immutable }
        placements.add(VisualPlacement(immutable, state))
        Minecraft.getInstance().level?.let { level ->
            rebuildAllPlacementStates(level)
        }
        saveFloorConfig()
        refreshWorldRenderer()
    }

    private fun rotateVisualBlock(pos: BlockPos): Boolean {
        val immutable = pos.immutable()
        val current = placementMap[immutable] ?: return false
        val rotated = runCatching { current.rotate(Rotation.CLOCKWISE_90) }.getOrDefault(current)
        if (rotated == current) return false
        placementMap[immutable] = rotated
        placements.removeIf { it.pos == immutable }
        placements.add(VisualPlacement(immutable, rotated))
        Minecraft.getInstance().level?.let { level ->
            rebuildAllPlacementStates(level)
        }
        saveFloorConfig()
        refreshWorldRenderer()
        return true
    }

    private fun <T> withEffectiveWorldLookups(action: () -> T): T {
        val previous = overrideWorldLookups.get()
        overrideWorldLookups.set(true)
        return try {
            action()
        } finally {
            overrideWorldLookups.set(previous)
        }
    }

    private fun rebuildAllPlacementStates(level: ClientLevel) {
        repeat(6) {
            var changed = false
            val currentPositions = placements.map { it.pos.immutable() }
            for (pos in currentPositions) {
                val current = placementMap[pos] ?: continue
                if (current.isAir) continue
                val updated = withEffectiveWorldLookups {
                    Block.updateFromNeighbourShapes(current, level, pos)
                }
                if (updated != current) {
                    placementMap[pos] = updated
                    placements.removeIf { it.pos == pos }
                    placements.add(VisualPlacement(pos, updated))
                    changed = true
                }
            }
            if (!changed) return
        }
    }

    private fun refreshLoadedStates() {
        if (schitzoMode) return
        val level = Minecraft.getInstance().level ?: return
        rebuildAllPlacementStates(level)
        saveFloorConfig()
        needsFullStateRebuild = false
    }

    private fun loadPlacementsForFloor(floor: String): List<VisualPlacement> {
        if (!Files.exists(configFile)) return emptyList()
        val root = Files.newBufferedReader(configFile).use { reader ->
            gson.fromJson(reader, JsonObject::class.java)
        } ?: return emptyList()
        val floorObject = root.getAsJsonObject(floor) ?: return emptyList()
        val out = mutableListOf<VisualPlacement>()
        for ((rawState, coordsElement) in floorObject.entrySet()) {
            if (!coordsElement.isJsonArray) continue
            val parsedState = LegacyStateParser.parse(rawState) ?: continue
            for (coordEntry in coordsElement.asJsonArray) {
                val pos = parseCoord(coordEntry) ?: continue
                out += VisualPlacement(pos.immutable(), parsedState)
            }
        }
        return out
    }

    private fun parseCoord(element: JsonElement): BlockPos? {
        if (!element.isJsonPrimitive) return null
        val parts = element.asString.split(",").map { it.trim() }
        if (parts.size != 3) return null
        val x = parts[0].toIntOrNull() ?: return null
        val y = parts[1].toIntOrNull() ?: return null
        val z = parts[2].toIntOrNull() ?: return null
        return BlockPos(x, y, z)
    }

    private fun serializeState(state: BlockState): String {
        val id = BuiltInRegistries.BLOCK.getKey(state.block).toString()
        val props = state.properties
        if (props.isEmpty()) return id

        val propsString = props
            .sortedBy { it.name }
            .joinToString(",") { property ->
                @Suppress("UNCHECKED_CAST")
                val typedProperty = property as Property<Comparable<Any>>
                val value = state.getValue(typedProperty)
                "${typedProperty.name}=${typedProperty.getName(value)}"
            }
        return "$id[$propsString]"
    }

    private fun saveFloorConfig() {
        val root = if (Files.exists(configFile)) {
            Files.newBufferedReader(configFile).use { reader ->
                gson.fromJson(reader, JsonObject::class.java)
            } ?: JsonObject()
        } else {
            JsonObject()
        }

        val floorObject = JsonObject()
        val sortedPlacements = placements.toList().sortedWith(
            Comparator { a, b ->
                val stateCmp = serializeState(a.state).compareTo(serializeState(b.state))
                if (stateCmp != 0) return@Comparator stateCmp
                val yCmp = a.pos.y.compareTo(b.pos.y)
                if (yCmp != 0) return@Comparator yCmp
                val xCmp = a.pos.x.compareTo(b.pos.x)
                if (xCmp != 0) return@Comparator xCmp
                a.pos.z.compareTo(b.pos.z)
            }
        )

        sortedPlacements.groupBy { serializeState(it.state) }.forEach { (stateString, list) ->
            val arr = JsonArray()
            list.forEach { placement ->
                arr.add("${placement.pos.x},${placement.pos.y},${placement.pos.z}")
            }
            floorObject.add(stateString, arr)
        }

        root.add(selectedFloor, floorObject)
        Files.createDirectories(configFile.parent)
        Files.writeString(configFile, gson.toJson(root))
    }

    private fun ensureConfigExists() {
        if (Files.exists(configFile)) return
        Files.createDirectories(configFile.parent)
        val stream = VisualFmeState::class.java.getResourceAsStream(defaultConfigResource)
        if (stream != null) {
            stream.use { input -> Files.copy(input, configFile) }
            return
        }
        Files.writeString(configFile, """{"7":{}}""")
    }

    private fun loadSettings() {
        if (!Files.exists(stateFile)) {
            saveSettings()
            return
        }
        val props = Properties()
        Files.newInputStream(stateFile).use(props::load)
        enabled = props.getProperty("enabled", "false").toBoolean()
        editMode = props.getProperty("editMode", "false").toBoolean()
        schitzoMode = props.getProperty("schitzoMode", "false").toBoolean()
        selectedFloor = props.getProperty("selectedFloor", "7")
        maxRenderDistance = props.getProperty("maxRenderDistance", "128").toIntOrNull() ?: 128
        selectedBlockId = normalizeBlockId(props.getProperty("selectedBlockId", "minecraft:glass"))
    }

    private fun saveSettings() {
        val props = Properties()
        props["enabled"] = enabled.toString()
        props["editMode"] = editMode.toString()
        props["schitzoMode"] = schitzoMode.toString()
        props["selectedFloor"] = selectedFloor
        props["maxRenderDistance"] = maxRenderDistance.toString()
        props["selectedBlockId"] = selectedBlockId
        Files.createDirectories(stateFile.parent)
        Files.newOutputStream(stateFile).use { props.store(it, "Visual FME settings") }
    }

    private fun loadSchitzoMappings() {
        if (!Files.exists(schitzoFile)) {
            schitzoMappings = mutableMapOf()
            return
        }
        schitzoMappings = runCatching {
            val root = Files.newBufferedReader(schitzoFile).use { reader ->
                gson.fromJson(reader, JsonObject::class.java)
            } ?: JsonObject()
            schitzoTheme = root.get("theme")?.asString?.let { raw ->
                BiomeTheme.entries.firstOrNull { it.name.equals(raw, ignoreCase = true) }
            }
            val mappingsObject = root.getAsJsonObject("mappings") ?: JsonObject()
            buildMap {
                for ((source, target) in mappingsObject.entrySet()) {
                    if (!target.isJsonPrimitive) continue
                    put(normalizeBlockId(source), normalizeBlockId(target.asString))
                }
            }.toMutableMap()
        }.getOrElse {
            LarpLog.warn("Failed to load schitzo mappings from $schitzoFile: ${it.message ?: it.javaClass.simpleName}")
            mutableMapOf()
        }
    }

    private fun saveSchitzoMappings() {
        val root = JsonObject()
        root.addProperty("schemaVersion", 1)
        schitzoTheme?.let { root.addProperty("theme", it.name) }
        val mappingsObject = JsonObject()
        schitzoMappings.toSortedMap().forEach { (source, target) ->
            mappingsObject.addProperty(source, target)
        }
        root.add("mappings", mappingsObject)
        Files.createDirectories(schitzoFile.parent)
        Files.writeString(schitzoFile, gson.toJson(root))
    }

    private fun ensureSchitzoMappingsReady() {
        if (schitzoMappings.isNotEmpty()) return
        schitzoMappings = buildRandomMappings().toMutableMap()
        saveSchitzoMappings()
    }

    private fun buildRandomMappings(): Map<String, String> {
        val out = linkedMapOf<String, String>()
        BuiltInRegistries.BLOCK.forEach { block ->
            val sourceId = BuiltInRegistries.BLOCK.getKey(block).toString()
            if (sourceId == "minecraft:air") return@forEach
            val family = classifyBlock(block.defaultBlockState())
            val candidates = familyCandidates[family].orEmpty()
            if (candidates.isEmpty()) {
                out[sourceId] = sourceId
                return@forEach
            }
            val filtered = candidates.filter { it != sourceId }
            val chosenPool = if (filtered.isNotEmpty()) filtered else candidates
            out[sourceId] = chosenPool[random.nextInt(chosenPool.size)]
        }
        return out
    }

    private fun buildNiceRandomMappings(): Pair<BiomeTheme, Map<String, String>> {
        val availableThemes = BiomeTheme.entries.filter { theme ->
            biomeFamilyCandidates[theme]?.values?.any { it.isNotEmpty() } == true
        }
        val theme = availableThemes.randomOrNull(random) ?: BiomeTheme.PLAINS
        val out = linkedMapOf<String, String>()
        BuiltInRegistries.BLOCK.forEach { block ->
            val sourceId = BuiltInRegistries.BLOCK.getKey(block).toString()
            if (sourceId == "minecraft:air") return@forEach
            val family = classifyBlock(block.defaultBlockState())
            val candidates = if (family == BlockFamily.LIQUID_PORTAL) {
                familyCandidates[family].orEmpty()
            } else {
                biomeFamilyCandidates[theme]?.get(family).orEmpty()
            }
            if (candidates.isEmpty()) {
                out[sourceId] = sourceId
                return@forEach
            }
            val preferred = candidates.filter { it != sourceId && sameBlockStem(sourceId, it) }
            val chosenPool = when {
                preferred.isNotEmpty() -> preferred
                candidates.any { it != sourceId } -> candidates.filter { it != sourceId }
                else -> listOf(sourceId)
            }
            out[sourceId] = chosenPool[random.nextInt(chosenPool.size)]
        }
        return theme to out
    }

    private fun buildFamilyCandidates(): Map<BlockFamily, List<String>> {
        val families = mutableMapOf<BlockFamily, MutableList<String>>()
        BuiltInRegistries.BLOCK.forEach { block ->
            val state = block.defaultBlockState()
            val id = BuiltInRegistries.BLOCK.getKey(block).toString()
            if (!isEligibleSchitzoTarget(block, state, id)) return@forEach
            val family = classifyBlock(state)
            families.getOrPut(family) { mutableListOf() }.add(id)
        }
        return families.mapValues { it.value.sorted() }
    }

    private fun buildBiomeFamilyCandidates(): Map<BiomeTheme, Map<BlockFamily, List<String>>> {
        return BiomeTheme.entries.associateWith { theme ->
            val themedFamilies = mutableMapOf<BlockFamily, MutableList<String>>()
            BuiltInRegistries.BLOCK.forEach { block ->
                val state = block.defaultBlockState()
                val id = BuiltInRegistries.BLOCK.getKey(block).toString()
                if (!isEligibleSchitzoTarget(block, state, id)) return@forEach
                if (!matchesBiomeTheme(id, theme)) return@forEach
                val family = classifyBlock(state)
                if (family == BlockFamily.LIQUID_PORTAL) return@forEach
                themedFamilies.getOrPut(family) { mutableListOf() }.add(id)
            }
            themedFamilies.mapValues { it.value.sorted() }
        }
    }

    private fun isEligibleSchitzoTarget(block: Block, state: BlockState, id: String): Boolean {
        if (state.isAir) return false
        if (block.asItem() == Items.AIR) return false
        if (id == "minecraft:barrier") return false
        if (id.contains("moving_piston")) return false
        if (id.contains("end_gateway")) return false
        if (id.contains("nether_portal")) return false
        if (id.contains("command_block")) return false
        if (id.contains("structure_block")) return false
        if (id.contains("jigsaw")) return false
        if (id.contains("barrier")) return false
        return true
    }

    private fun classifyBlock(state: BlockState): BlockFamily {
        val block = state.block
        val id = BuiltInRegistries.BLOCK.getKey(block).toString()
        return when {
            id == "minecraft:water" || id == "minecraft:lava" || id == "minecraft:end_portal" -> BlockFamily.LIQUID_PORTAL
            block is IronBarsBlock -> BlockFamily.PANE
            block is FenceBlock -> BlockFamily.FENCE
            block is FenceGateBlock -> BlockFamily.FENCE_GATE
            block is WallBlock -> BlockFamily.WALL
            block is StairBlock -> BlockFamily.STAIRS
            block is SlabBlock -> BlockFamily.SLAB
            block is TrapDoorBlock -> BlockFamily.TRAPDOOR
            block is DoorBlock -> BlockFamily.DOOR
            block is LeavesBlock -> BlockFamily.LEAVES
            block is ButtonBlock -> BlockFamily.BUTTON
            block is PressurePlateBlock -> BlockFamily.PRESSURE_PLATE
            block is CarpetBlock -> BlockFamily.CARPET
            block is TorchBlock || block is WallTorchBlock -> BlockFamily.TORCH
            block is BushBlock || id.contains("flower") || id.contains("sapling") -> BlockFamily.PLANT
            id.contains("glass") && !id.contains("pane") -> BlockFamily.GLASS_BLOCK
            state.canOcclude() && state.isSolidRender() -> BlockFamily.FULL_CUBE
            else -> BlockFamily.GENERIC
        }
    }

    private fun matchesBiomeTheme(id: String, theme: BiomeTheme): Boolean {
        val path = id.substringAfter(':')
        return when {
            theme == BiomeTheme.PLAINS -> matchesAny(path, "oak", "birch", "grass", "dirt", "wheat", "sunflower", "poppy", "cobblestone", "stone", "moss", "azalea")
            theme == BiomeTheme.DESERT -> matchesAny(path, "sand", "sandstone", "cut_sandstone", "smooth_sandstone", "cactus", "dead_bush", "terracotta", "red_sand", "red_sandstone")
            theme == BiomeTheme.JUNGLE -> matchesAny(path, "jungle", "bamboo", "melon", "cocoa", "vine", "moss", "leaf", "leaves")
            theme == BiomeTheme.TAIGA -> matchesAny(path, "spruce", "fern", "podzol", "sweet_berry", "moss", "stone", "cobblestone")
            theme == BiomeTheme.SNOWY -> matchesAny(path, "snow", "ice", "packed_ice", "blue_ice", "spruce", "powder_snow", "stone", "cobblestone")
            theme == BiomeTheme.SWAMP -> matchesAny(path, "mangrove", "mud", "clay", "vine", "lily_pad", "moss", "leaf", "leaves")
            theme == BiomeTheme.OCEAN -> matchesAny(path, "prismarine", "kelp", "seagrass", "coral", "lapis", "light_blue", "blue", "sand")
            theme == BiomeTheme.MUSHROOM -> matchesAny(path, "mushroom", "mycelium", "stem")
            theme == BiomeTheme.CHERRY -> matchesAny(path, "cherry", "pink", "petal", "quartz", "calcite")
            theme == BiomeTheme.NETHER -> matchesAny(path, "nether", "crimson", "warped", "netherrack", "blackstone", "basalt", "soul", "shroomlight", "magma")
            theme == BiomeTheme.END -> matchesAny(path, "end", "purpur", "chorus", "obsidian")
            else -> false
        }
    }

    private fun matchesAny(path: String, vararg needles: String): Boolean {
        return needles.any { it in path }
    }

    private fun sameBlockStem(sourceId: String, targetId: String): Boolean {
        val source = sourceId.substringAfter(':')
        val target = targetId.substringAfter(':')
        val shared = listOf(
            "planks", "log", "wood", "leaves", "stairs", "slab", "fence", "fence_gate", "door", "trapdoor",
            "pane", "glass", "wall", "button", "pressure_plate", "carpet", "torch", "flower", "sapling"
        )
        return shared.any { source.contains(it) && target.contains(it) }
    }

    private fun getSchitzoReplacementState(pos: BlockPos): BlockState? {
        val level = Minecraft.getInstance().level ?: return null
        val original = level.getBlockState(pos)
        if (original.isAir) return null
        val sourceId = BuiltInRegistries.BLOCK.getKey(original.block).toString()
        if (sourceId == "minecraft:barrier") return null
        ensureSchitzoMappingsReady()
        val targetId = schitzoMappings[sourceId] ?: sourceId
        val targetResource = Identifier.tryParse(targetId) ?: return original
        if (!BuiltInRegistries.BLOCK.containsKey(targetResource)) return original
        val targetBlock = BuiltInRegistries.BLOCK.getValue(targetResource)
        val targetState = copySharedProperties(original, targetBlock.defaultBlockState())
        return if (targetState == original) null else targetState
    }

    private fun copySharedProperties(from: BlockState, target: BlockState): BlockState {
        var out = target
        for (property in from.properties) {
            val targetProperty = out.properties.firstOrNull { it.name == property.name } ?: continue
            val rawValue = from.getValueUntyped(property)
            if (!targetProperty.possibleValues.contains(rawValue)) continue
            out = setComparableValue(out, targetProperty, rawValue) ?: out
        }
        return out
    }

    private fun setComparableValue(state: BlockState, property: Property<*>, value: Any): BlockState? {
        @Suppress("UNCHECKED_CAST")
        val typedProperty = property as? Property<Comparable<Any>> ?: return null
        @Suppress("UNCHECKED_CAST")
        val typedValue = value as? Comparable<Any> ?: return null
        return state.setValue(typedProperty, typedValue)
    }

    private fun BlockState.getValueUntyped(property: Property<*>): Any {
        @Suppress("UNCHECKED_CAST")
        val typedProperty = property as Property<Comparable<Any>>
        return this.getValue(typedProperty)
    }

    private fun refreshWorldRenderer() {
        Minecraft.getInstance().levelRenderer?.allChanged()
    }

    private fun normalizeBlockId(raw: String): String {
        val trimmed = raw.trim().lowercase()
        return if (':' in trimmed) trimmed else "minecraft:$trimmed"
    }

    private fun display(message: String, color: Int = LarpBranding.WHITE) {
        val player = Minecraft.getInstance().player ?: return
        player.sendSystemMessage(
            LarpBranding.prefixed(Component.literal(message).withColor(color))
        )
    }

}
