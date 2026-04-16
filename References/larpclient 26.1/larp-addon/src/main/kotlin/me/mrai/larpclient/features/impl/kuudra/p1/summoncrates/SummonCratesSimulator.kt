package me.mrai.larpclient.features.impl.kuudra.p1.summoncrates

import me.mrai.larpclient.features.impl.kuudra.p1.kuudrawaypoints.KuudraWaypointModule
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.Entity.RemovalReason
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.Display
import net.minecraft.world.entity.monster.Giant
import net.minecraft.world.entity.monster.zombie.Zombie
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl
import net.minecraft.world.level.ClipContext
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

object SummonCratesSimulator {
    private data class PreRoute(
        val display: String,
        val center: Vec3,
        val radius: Double
    )

    private data class SupplySpawn(val label: String, val chestPos: Vec3)

    private data class SimCrate(
        val label: String,
        val chestPos: Vec3,
        val giant: Giant,
        val chestDisplay: Display.ItemDisplay,
        val zombies: List<Zombie>
    ) {
        fun containsEntity(entityId: Int): Boolean {
            return giant.id == entityId || chestDisplay.id == entityId || zombies.any { it.id == entityId }
        }

        fun remove(level: net.minecraft.client.multiplayer.ClientLevel) {
            zombies.forEach { level.removeEntity(it.id, RemovalReason.DISCARDED) }
            level.removeEntity(chestDisplay.id, RemovalReason.DISCARDED)
            level.removeEntity(giant.id, RemovalReason.DISCARDED)
        }

        fun chestBox(): AABB {
            return AABB(
                chestPos.x - 0.55, chestPos.y - 0.35, chestPos.z - 0.55,
                chestPos.x + 0.55, chestPos.y + 0.35, chestPos.z + 0.55
            )
        }

        fun zombieBoxes(): List<AABB> {
            return zombies.map { zombie ->
                AABB(
                    zombie.x - 0.32, zombie.y - 0.15, zombie.z - 0.32,
                    zombie.x + 0.32, zombie.y + 0.75, zombie.z + 0.32
                )
            }
        }
    }

    data class SummonResult(val pre: String, val missing: String, val spawned: List<String>)

    data class RenderState(val giantHeadBoxes: List<AABB>, val chestBoxes: List<AABB>, val zombieBoxes: List<AABB>)

    private const val ELLE_START_MESSAGE = "[NPC] Elle: Okay adventurers, I will go and fish up Kuudra!"
    private const val ELLE_HEAD_OVER_MESSAGE = "[NPC] Elle: Head over to the main platform"
    private const val SUPPLY_PHASE_MESSAGE =
        "[NPC] Elle: ARGH! All of the supplies fell into the lava! You need to retrieve them quickly!"
    private const val PICKUP_DURATION_MS = 5_000L
    private const val PICKUP_RANGE_SQ = 25.0
    private const val CHEST_SLOT = 8
    private const val ZOMBIE_RING_RADIUS = 0.95
    private const val GIANT_YAW = 90f
    private const val PILE_CLEAR_RADIUS_SQ = 2.5 * 2.5
    private const val PICKUP_REACH = 6.0
    private const val PULL_STEP = 0.55
    private const val PEARL_DRAG = 0.99
    private const val PEARL_GRAVITY = 0.03
    private const val MAX_PEARL_SIM_TICKS = 120
    private val HELD_CHEST_OFFSET = Vec3(-0.95, 6.9, 0.0)

    private val preRoutes = listOf(
        PreRoute("Tri", Vec3(-67.5, 77.0, -122.5), 15.0),
        PreRoute("X", Vec3(-142.5, 77.0, -151.0), 30.0),
        PreRoute("Equals", Vec3(-65.5, 76.0, -87.5), 15.0),
        PreRoute("Slash", Vec3(-113.5, 77.0, -68.5), 15.0)
    )

    private val spawns = listOf(
        SupplySpawn("X", Vec3(-133.0, 70.85, -143.0)),
        SupplySpawn("XC", Vec3(-132.0, 70.85, -116.0)),
        SupplySpawn("Square", Vec3(-142.0, 70.85, -86.0)),
        SupplySpawn("Equals", Vec3(-64.0, 70.85, -82.0)),
        SupplySpawn("Slash", Vec3(-117.0, 70.85, -68.0)),
        SupplySpawn("Tri", Vec3(-66.0, 70.85, -122.0)),
        SupplySpawn("Shop", Vec3(-76.0, 70.85, -140.0))
    )

    private var nextFakeId = -30_000
    private val activeCrates = mutableListOf<SimCrate>()
    private var activePickupEntityId: Int? = null
    private var pickupStartedAt = -1L
    private var carryingChest = false
    private var hookAttachedCrateId: Int? = null
    private val trackedPearlLandings = hashMapOf<Int, Vec3>()

    fun summonRandomScenario(): SummonResult? {
        val mc = Minecraft.getInstance()
        val player = mc.player ?: return null
        val level = mc.level ?: return null

        val pre = detectPre(player.position()) ?: return null

        clear()

        val missingSpawn = spawns.random(Random(System.nanoTime()))
        val spawnedLabels = spawns.filterNot { it.label == missingSpawn.label }.map { it.label }

        KuudraWaypointModule.resetKuudraState()
        KuudraWaypointModule.onChatMessage(ELLE_START_MESSAGE)
        KuudraWaypointModule.onChatMessage(ELLE_HEAD_OVER_MESSAGE)
        KuudraWaypointModule.onChatMessage(SUPPLY_PHASE_MESSAGE)
        KuudraWaypointModule.onChatMessage("No ${missingSpawn.label}")

        spawns.forEach { spawn ->
            if (spawn.label != missingSpawn.label) {
                spawnCrate(level, spawn)
            }
        }

        mc.gui.setOverlayMessage(Component.literal("No ${missingSpawn.label}"), false)
        return SummonResult(pre.display, missingSpawn.label, spawnedLabels)
    }

    fun onTick() {
        val mc = Minecraft.getInstance()
        val player = mc.player ?: return
        val level = mc.level ?: return

        updateFishingHookAttachment(player)
        updatePearlLandings(player, level)

        if (activePickupEntityId == null) {
            pullCratesWithRod(player, level)
        }

        if (activePickupEntityId != null && pickupStartedAt > 0L) {
            if (System.currentTimeMillis() - pickupStartedAt >= PICKUP_DURATION_MS) {
                finishPickup()
            }
        }

        if (carryingChest && isOnPile(player.position())) {
            player.inventory.setItem(CHEST_SLOT, ItemStack.EMPTY)
            carryingChest = false
            KuudraWaypointModule.onChatMessage("You retrieved some of Elle's supplies from the Lava!")
        }
    }

    fun tryStartPickup(entity: Entity): Boolean {
        if (activePickupEntityId != null || carryingChest) return false
        val mc = Minecraft.getInstance()
        val player = mc.player ?: return false
        if (player.distanceToSqr(entity) > PICKUP_RANGE_SQ) return false

        val crate = activeCrates.firstOrNull { it.containsEntity(entity.id) } ?: return false
        startPickup(crate)
        return true
    }

    fun onWorldChange() {
        clear()
    }

    fun isFakeEntity(entityId: Int): Boolean {
        return activeCrates.any { it.containsEntity(entityId) }
    }

    fun renderState(): RenderState {
        return RenderState(
            giantHeadBoxes = activeCrates.map(::giantHeadBox),
            chestBoxes = activeCrates.map { it.chestBox() },
            zombieBoxes = activeCrates.flatMap(SimCrate::zombieBoxes)
        )
    }

    fun handleUseItem(mc: Minecraft): Boolean {
        val player = mc.player ?: return false
        if (activePickupEntityId != null || carryingChest) return false

        val eyePos = player.getEyePosition()
        val reachEnd = eyePos.add(player.getViewVector(1f).scale(PICKUP_REACH))
        val hitCrate = activeCrates
            .asSequence()
            .flatMap { crate -> crate.zombieBoxes().asSequence().map { box -> crate to box } }
            .mapNotNull { (crate, box) ->
                box.clip(eyePos, reachEnd).orElse(null)?.let { hitPos ->
                    Triple(crate, box, eyePos.distanceToSqr(hitPos))
                }
            }
            .minByOrNull { it.third }
            ?.first
            ?: return false

        startPickup(hitCrate)
        return true
    }

    private fun detectPre(pos: Vec3): PreRoute? {
        return preRoutes.firstOrNull { squaredHorizontalDistance(pos, it.center) <= it.radius * it.radius }
    }

    private fun clear() {
        Minecraft.getInstance().level?.let { level ->
            activeCrates.forEach { it.remove(level) }
        }
        activeCrates.clear()
        activePickupEntityId = null
        pickupStartedAt = -1L
        carryingChest = false
        hookAttachedCrateId = null
        trackedPearlLandings.clear()
    }

    private fun spawnCrate(level: net.minecraft.client.multiplayer.ClientLevel, spawn: SupplySpawn) {
        val giantPos = giantPositionForChest(spawn.chestPos)
        val giant = Giant(EntityType.GIANT, level).apply {
            setId(nextFakeId--)
            setPos(giantPos.x, giantPos.y, giantPos.z)
            yRot = GIANT_YAW
            yRotO = GIANT_YAW
            setInvisible(true)
            setInvulnerable(true)
            setNoGravity(true)
            setNoAi(true)
            setSilent(true)
            setItemSlot(EquipmentSlot.MAINHAND, ItemStack(Items.CHEST))
        }
        level.addEntity(giant)

        val chestDisplay = Display.ItemDisplay(EntityType.ITEM_DISPLAY, level).apply {
            setId(nextFakeId--)
            setPos(spawn.chestPos.x, spawn.chestPos.y, spawn.chestPos.z)
            setInvisible(false)
            setInvulnerable(true)
            setNoGravity(true)
            setSilent(true)
            getSlot(0)?.set(ItemStack(Items.CHEST))
        }
        level.addEntity(chestDisplay)

        val zombies = List(8) { index ->
            val angle = (Math.PI * 2.0 * index) / 8.0
            val x = spawn.chestPos.x + cos(angle) * ZOMBIE_RING_RADIUS
            val z = spawn.chestPos.z + sin(angle) * ZOMBIE_RING_RADIUS

            Zombie(level).apply {
                setId(nextFakeId--)
                setPos(x, zombieYForChest(spawn.chestPos), z)
                setBaby(true)
                setInvisible(true)
                setInvulnerable(true)
                setNoGravity(true)
                setNoAi(true)
                setSilent(true)
                setCustomName(Component.literal("Supply ${spawn.label}"))
                setCustomNameVisible(false)
                level.addEntity(this)
            }
        }

        activeCrates += SimCrate(spawn.label, spawn.chestPos, giant, chestDisplay, zombies)
    }

    private fun pullCratesWithRod(
        player: net.minecraft.client.player.LocalPlayer,
        level: net.minecraft.client.multiplayer.ClientLevel
    ) {
        val hook = player.fishing ?: return
        val crate = activeCrates.firstOrNull { it.giant.id == hookAttachedCrateId } ?: return

        val current = crate.chestPos
        val delta = Vec3(player.x - current.x, 0.0, player.z - current.z)
        val length = kotlin.math.sqrt(delta.x * delta.x + delta.z * delta.z)
        if (length <= 0.05) return

        val step = minOf(PULL_STEP, length)
        val move = Vec3(delta.x / length * step, 0.0, delta.z / length * step)
        val nextChest = current.add(move)
        val nextZombieBoxes = crate.zombieBoxes().map { it.move(move) }
        val nextGiantBox = crate.giant.boundingBox.move(move)

        if (!level.noCollision(nextGiantBox)) return
        if (!nextZombieBoxes.all { level.noCollision(it) }) return

        crate.giant.setPos(crate.giant.x + move.x, crate.giant.y, crate.giant.z + move.z)
        crate.chestDisplay.setPos(crate.chestDisplay.x + move.x, crate.chestDisplay.y, crate.chestDisplay.z + move.z)
        crate.zombies.forEach { zombie -> zombie.setPos(zombie.x + move.x, zombie.y, zombie.z + move.z) }
        val crateIndex = activeCrates.indexOf(crate)
        if (crateIndex >= 0) {
            activeCrates[crateIndex] = crate.copy(chestPos = nextChest)
        }

        hook.setPos(nextChest.x, nextChest.y + 0.45, nextChest.z)
        hook.setDeltaMovement(Vec3.ZERO)
        hook.setNoGravity(true)
    }

    private fun updateFishingHookAttachment(player: net.minecraft.client.player.LocalPlayer) {
        val hook = player.fishing ?: run {
            hookAttachedCrateId = null
            return
        }

        val hookPos = hook.position()
        val attached = activeCrates.firstOrNull { it.giant.id == hookAttachedCrateId }
        val crate = attached ?: activeCrates.firstOrNull { sim ->
            sim.chestBox().inflate(1.2).contains(hookPos) ||
                giantHeadBox(sim).inflate(1.2).contains(hookPos) ||
                sim.zombieBoxes().any { it.inflate(0.5).contains(hookPos) } ||
                sim.giant.boundingBox.inflate(2.5).contains(hookPos)
        } ?: return

        hookAttachedCrateId = crate.giant.id
        hook.setPos(crate.chestPos.x, crate.chestPos.y + 0.45, crate.chestPos.z)
        hook.setDeltaMovement(Vec3.ZERO)
        hook.setNoGravity(true)
        hook.setOwner(player)
    }

    private fun startPickup(crate: SimCrate) {
        if (activePickupEntityId != null) return

        activePickupEntityId = crate.giant.id
        pickupStartedAt = System.currentTimeMillis()
        KuudraWaypointModule.simulatePickup()

        Minecraft.getInstance().gui.apply {
            setTitle(Component.literal("Picking Up"))
            setSubtitle(Component.literal(crate.label))
            setTimes(5, 35, 5)
        }
    }

    private fun finishPickup() {
        val mc = Minecraft.getInstance()
        val player = mc.player ?: return
        val level = mc.level ?: return
        val pickedId = activePickupEntityId ?: return
        val crate = activeCrates.firstOrNull { it.giant.id == pickedId } ?: return

        crate.remove(level)
        activeCrates.remove(crate)
        activePickupEntityId = null
        pickupStartedAt = -1L
        carryingChest = true
        player.inventory.setItem(CHEST_SLOT, ItemStack(Items.CHEST))
    }

    private fun isOnPile(pos: Vec3): Boolean {
        return KuudraWaypointModule.getPileTargets().any { (_, pilePos) ->
            squaredHorizontalDistance(pos, pilePos) <= PILE_CLEAR_RADIUS_SQ
        }
    }

    private fun updatePearlLandings(
        player: net.minecraft.client.player.LocalPlayer,
        level: net.minecraft.client.multiplayer.ClientLevel
    ) {
        val currentPearlIds = hashSetOf<Int>()
        for (entity in level.entitiesForRendering()) {
            val pearl = entity as? ThrownEnderpearl ?: continue
            if (pearl.owner?.uuid != player.uuid) continue

            currentPearlIds += pearl.id
            predictLanding(level, pearl)?.let { trackedPearlLandings[pearl.id] = it }
        }

        val iterator = trackedPearlLandings.iterator()
        while (iterator.hasNext()) {
            val (pearlId, landingPos) = iterator.next()
            if (pearlId in currentPearlIds) continue

            if (carryingChest && isOnPile(landingPos)) {
                player.inventory.setItem(CHEST_SLOT, ItemStack.EMPTY)
                carryingChest = false
                KuudraWaypointModule.onChatMessage("You retrieved some of Elle's supplies from the Lava!")
            }
            iterator.remove()
        }
    }

    private fun predictLanding(
        level: net.minecraft.client.multiplayer.ClientLevel,
        pearl: ThrownEnderpearl
    ): Vec3? {
        var pos = pearl.position()
        var velocity = pearl.deltaMovement

        repeat(MAX_PEARL_SIM_TICKS) {
            val nextPos = pos.add(velocity)
            val hit = level.clip(
                ClipContext(
                    pos,
                    nextPos,
                    ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE,
                    pearl
                )
            )

            if (hit.type != HitResult.Type.MISS) {
                return Vec3(hit.blockPos.x + 0.5, hit.blockPos.y + 1.0, hit.blockPos.z + 0.5)
            }

            pos = nextPos
            velocity = velocity.scale(PEARL_DRAG).add(0.0, -PEARL_GRAVITY, 0.0)
        }

        return null
    }

    private fun squaredHorizontalDistance(a: Vec3, b: Vec3): Double {
        val dx = a.x - b.x
        val dz = a.z - b.z
        return dx * dx + dz * dz
    }

    private fun zombieYForChest(chestPos: Vec3): Double {
        return chestPos.y + 5.2
    }

    private fun giantHeadBox(crate: SimCrate): AABB {
        return AABB(
            crate.giant.x - 0.7, crate.giant.y + 10.1, crate.giant.z - 0.7,
            crate.giant.x + 0.7, crate.giant.y + 11.5, crate.giant.z + 0.7
        )
    }

    private fun giantPositionForChest(chestPos: Vec3): Vec3 {
        val yawRad = Math.toRadians(GIANT_YAW.toDouble())
        val rotated = rotateY(HELD_CHEST_OFFSET, yawRad)
        return chestPos.subtract(rotated)
    }

    private fun rotateY(vec: Vec3, yawRad: Double): Vec3 {
        val cos = kotlin.math.cos(yawRad)
        val sin = kotlin.math.sin(yawRad)
        return Vec3(
            vec.x * cos - vec.z * sin,
            vec.y,
            vec.x * sin + vec.z * cos
        )
    }
}
