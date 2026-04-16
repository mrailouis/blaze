package me.mrai.larpclient.features.impl.kuudra.p1.autoroutetopre

import net.minecraft.core.BlockPos

enum class PreRouteName(val id: String) {
    TRI("tri"),
    X("x"),
    SLASH("slash"),
    EQUALS("equals");

    companion object {
        fun from(input: String): PreRouteName? {
            return entries.firstOrNull { it.id.equals(input, ignoreCase = true) }
        }
    }
}

data class StoredPreSegment(
    val triggerBlock: BlockPos,
    val destinationBlock: BlockPos,
    val hitX: Double,
    val hitY: Double,
    val hitZ: Double
)

data class StoredPreRoute(
    val name: PreRouteName,
    var segments: MutableList<StoredPreSegment> = mutableListOf()
)