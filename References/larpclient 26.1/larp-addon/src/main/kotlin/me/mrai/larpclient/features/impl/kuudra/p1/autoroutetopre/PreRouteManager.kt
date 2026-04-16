package me.mrai.larpclient.features.impl.kuudra.p1.autoroutetopre

import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import me.mrai.larpclient.util.LarpLog
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import net.minecraft.world.level.ClipContext
import java.nio.file.Files
import kotlin.io.path.exists

object PreRouteManager {
    private val gson = GsonBuilder().setPrettyPrinting().create()

    private val configDir = FabricLoader.getInstance().configDir.resolve("larpclient")
    private val configFile = configDir.resolve("preroutes.json")

    private val routes = linkedMapOf<PreRouteName, StoredPreRoute>()

    fun load() {
        try {
            if (!configDir.exists()) Files.createDirectories(configDir)
            if (!configFile.exists()) return

            val json = Files.readString(configFile)
            val type = object : TypeToken<List<StoredPreRoute>>() {}.type
            val loaded: List<StoredPreRoute> = gson.fromJson(json, type) ?: emptyList()

            routes.clear()
            loaded.forEach { route ->
                routes[route.name] = StoredPreRoute(
                    route.name,
                    route.segments.toMutableList()
                )
            }
        } catch (throwable: Throwable) {
            LarpLog.warn("Failed to load preroutes from $configFile: ${throwable.message ?: throwable.javaClass.simpleName}")
        }
    }

    fun save() {
        try {
            if (!configDir.exists()) Files.createDirectories(configDir)
            Files.writeString(configFile, gson.toJson(routes.values.toList()))
        } catch (throwable: Throwable) {
            LarpLog.warn("Failed to save preroutes to $configFile: ${throwable.message ?: throwable.javaClass.simpleName}")
        }
    }

    fun get(name: PreRouteName): StoredPreRoute? = routes[name]

    fun getAll(): Collection<StoredPreRoute> = routes.values

    fun clear(name: PreRouteName) {
        routes[name] = StoredPreRoute(name, mutableListOf())
        save()
    }

    fun addLookedSegment(name: PreRouteName): Boolean {
        val client = Minecraft.getInstance()
        val player = client.player ?: return false
        val level = player.level()

        val camera = client.gameRenderer.mainCamera
        val start = camera.position()
        val rotation = player.getViewVector(1.0f)
        val maxDistance = 128.0
        val end = start.add(rotation.scale(maxDistance))

        val hit = level.clip(
            ClipContext(
                start,
                end,
                ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE,
                player
            )
        )

        if (hit.type != HitResult.Type.BLOCK) return false

        val blockHit = hit as BlockHitResult
        val triggerBlock = player.blockPosition().below()
        val destinationBlock = blockHit.blockPos
        val hitPos = blockHit.location

        val route = routes.getOrPut(name) { StoredPreRoute(name, mutableListOf()) }
        route.segments += StoredPreSegment(
            triggerBlock = triggerBlock,
            destinationBlock = destinationBlock,
            hitX = hitPos.x,
            hitY = hitPos.y,
            hitZ = hitPos.z
        )

        save()

        player.sendSystemMessage(
            Component.literal(
                "Added segment ${route.segments.size - 1} to ${name.id}: " +
                        "${fmt(triggerBlock)} -> ${fmt(destinationBlock)}"
            )
        )
        return true
    }

    fun remove(name: PreRouteName, index: Int): Boolean {
        val route = routes[name] ?: return false
        val segments = route.segments
        if (index !in segments.indices) return false

        segments.removeAt(index)
        save()
        return true
    }

    private fun fmt(pos: BlockPos): String = "${pos.x}, ${pos.y}, ${pos.z}"
}
