package me.mrai.blaze.feature.blaze

import me.mrai.blaze.config.BlazeDataStore
import me.mrai.blaze.feature.module.BlazeModuleIds
import me.mrai.blaze.render.world.WorldBoxRenderer
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents
import net.minecraft.client.Minecraft
import net.minecraft.world.entity.monster.Blaze

object BlazeEspController {
    private const val MAX_RENDER_DISTANCE_SQR = 128.0 * 128.0
    private val boxStyle = WorldBoxRenderer.BoxStyle(
        fillRed = 1.0f,
        fillGreen = 0.30f,
        fillBlue = 0.12f,
        fillAlpha = 0.16f,
        outlineRed = 1.0f,
        outlineGreen = 0.72f,
        outlineBlue = 0.24f,
        outlineAlpha = 0.92f,
        outlineThickness = 0.035f
    )

    fun register() {
        WorldRenderEvents.END_MAIN.register(::render)
    }

    private fun render(context: WorldRenderContext) {
        BlazePathfindController.renderFrame()
        val client = Minecraft.getInstance()
        val level = client.level
        val player = client.player
        if (level == null || player == null) {
            return
        }

        if (!BlazeDataStore.state.enabled) {
            return
        }

        val boxes = mutableListOf<Pair<net.minecraft.world.phys.AABB, WorldBoxRenderer.BoxStyle>>()
        val quads = BlazePathfindController.activeRouteQuads()
        if (BlazeDataStore.isModuleEnabled(BlazeModuleIds.BLAZE_ESP)) {
            boxes += level.entitiesForRendering()
                .filterIsInstance<Blaze>()
                .filter { !it.isRemoved && it.isAlive && it.distanceToSqr(player) <= MAX_RENDER_DISTANCE_SQR }
                .map { it.boundingBox.inflate(0.02) to boxStyle }
        }
        boxes += BlazePathfindController.activeRouteBoxes()
        boxes += BlazePathfindController.activeEdgeDebugBoxes()

        if (boxes.isNotEmpty() || quads.isNotEmpty()) {
            WorldBoxRenderer.render(context, boxes, quads, depthless = true)
        }
    }
}
