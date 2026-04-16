package me.mrai.larpclient.features.impl.dungeons.f7.predev.arrowalign

import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.world.entity.decoration.ItemFrame
import net.minecraft.world.item.ArrowItem
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3

object ArrowAlignSolver {
    private val solutions = arrayOf(
        intArrayOf(7, 7, 7, 7, -1, 1, -1, -1, -1, -1, 1, 3, 3, 3, 3, -1, -1, -1, -1, 1, -1, 7, 7, 7, 1),
        intArrayOf(-1, -1, -1, -1, -1, 1, -1, 1, -1, 1, 1, -1, 1, -1, 1, 1, -1, 1, -1, 1, -1, -1, -1, -1, -1),
        intArrayOf(5, 3, 3, 3, -1, 5, -1, -1, -1, -1, 7, 7, -1, -1, -1, 1, -1, -1, -1, -1, 1, 3, 3, 3, -1),
        intArrayOf(-1, -1, -1, -1, -1, -1, 1, -1, 1, -1, 7, 1, 7, 1, 3, 1, -1, 1, -1, 1, -1, -1, -1, -1, -1),
        intArrayOf(-1, -1, 7, 7, 5, -1, 7, 1, -1, 5, -1, -1, -1, -1, -1, -1, 7, 5, -1, 1, -1, -1, 7, 7, 1),
        intArrayOf(7, 7, -1, -1, -1, 1, -1, -1, -1, -1, 1, 3, 3, 3, 3, -1, -1, -1, -1, 1, -1, -1, -1, 7, 1),
        intArrayOf(5, 3, 3, 3, 3, 5, -1, -1, -1, 1, 7, 7, -1, -1, 1, -1, -1, -1, -1, 1, -1, 7, 7, 7, 1),
        intArrayOf(7, 7, -1, -1, -1, 1, -1, -1, -1, -1, 1, 3, -1, 7, 5, -1, -1, -1, -1, 5, -1, -1, -1, 3, 3),
        intArrayOf(-1, -1, -1, -1, -1, 1, 3, 3, 3, 3, -1, -1, -1, -1, 1, 7, 7, 7, 7, 1, -1, -1, -1, -1, -1)
    )

    private val deviceMiddle = Vec3(-3.0, 120.0, 77.0)
    private const val deviceCornerY = 120
    private const val deviceCornerZ = 75
    val deviceBox: AABB = AABB(-4.0, 119.0, 74.0, -1.0, 125.0, 80.0)

    data class FrameSolution(
        val index: Int,
        val frame: ItemFrame,
        val currentRotation: Int,
        val targetRotation: Int,
        val clicksNeeded: Int
    )

    data class Snapshot(
        val solution: IntArray,
        val frames: List<FrameSolution>
    )

    fun isNearDevice(position: Vec3, maxDistanceSq: Double = 100.0): Boolean {
        return position.distanceToSqr(deviceMiddle) <= maxDistanceSq
    }

    fun buildSnapshot(
        level: ClientLevel,
        predictedRotations: IntArray? = null,
        recentClickTimes: LongArray? = null,
        predictionWindowMs: Long = 1000L
    ): Snapshot? {
        val frameData = arrayOfNulls<FrameData>(25)
        val now = System.currentTimeMillis()

        for (frame in level.getEntitiesOfClass(ItemFrame::class.java, deviceBox)) {
            if (frame.item.item !is ArrowItem) continue

            val index = frameIndex(frame) ?: continue
            val predictedRotation = predictedRotations?.getOrNull(index) ?: -1
            val recentClick = recentClickTimes?.getOrNull(index) ?: 0L
            val rotation = if (predictedRotation >= 0 && now - recentClick < predictionWindowMs) {
                predictedRotation
            } else {
                frame.rotation
            }
            frameData[index] = FrameData(frame, rotation)
        }

        if (frameData.none { it != null }) return null

        val occupancy = IntArray(25) { index -> frameData[index]?.rotation ?: -1 }
        val solution = findMatchingSolution(occupancy) ?: return null
        val frames = buildList {
            for (index in 0 until 25) {
                val data = frameData[index] ?: continue
                val targetRotation = solution[index]
                if (targetRotation < 0) continue
                add(
                    FrameSolution(
                        index = index,
                        frame = data.frame,
                        currentRotation = data.rotation,
                        targetRotation = targetRotation,
                        clicksNeeded = (targetRotation - data.rotation + 8) % 8
                    )
                )
            }
        }
        return Snapshot(solution = solution, frames = frames)
    }

    private fun findMatchingSolution(rotations: IntArray): IntArray? {
        for (solution in solutions) {
            var matches = true
            for (index in 0 until 25) {
                val solutionHasFrame = solution[index] != -1
                val currentHasFrame = rotations[index] != -1
                if (solutionHasFrame != currentHasFrame) {
                    matches = false
                    break
                }
            }
            if (matches) return solution
        }
        return null
    }

    private fun frameIndex(frame: ItemFrame): Int? {
        val dy = frame.y.toInt() - deviceCornerY
        val dz = frame.z.toInt() - deviceCornerZ
        val index = dy + dz * 5
        return index.takeIf { it in 0..24 }
    }

    private data class FrameData(
        val frame: ItemFrame,
        val rotation: Int
    )
}
