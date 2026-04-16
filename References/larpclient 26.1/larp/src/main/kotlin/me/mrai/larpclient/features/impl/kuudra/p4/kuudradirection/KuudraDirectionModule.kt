package me.mrai.larpclient.features.impl.kuudra.p4.kuudradirection

import me.mrai.larpclient.module.BoolSetting
import me.mrai.larpclient.module.Module
import me.mrai.larpclient.module.ModuleCategory
import me.mrai.larpclient.module.SliderSetting
import net.minecraft.client.Minecraft
import net.minecraft.world.entity.monster.MagmaCube

object KuudraDirectionModule : Module(
    name = "Kuudra Direction",
    description = "Displays Kuudra's peek direction when it changes.",
    category = ModuleCategory.KUUDRA_P4
) {
    private val bold = BoolSetting("Bold", true)
    private val displayTicks = SliderSetting("Display Ticks", 80.0, 20.0, 200.0, 1.0)

    @Volatile
    private var currentDirection: Direction = Direction.UNKNOWN

    @Volatile
    private var activeTicksRemaining: Int = 0

    init {
        settings += listOf(bold, displayTicks)
    }

    override fun onDisable() {
        currentDirection = Direction.UNKNOWN
        activeTicksRemaining = 0
    }

    fun onWorldChange() {
        currentDirection = Direction.UNKNOWN
        activeTicksRemaining = 0
    }

    override fun onTick() {
        val level = Minecraft.getInstance().level ?: run {
            currentDirection = Direction.UNKNOWN
            activeTicksRemaining = 0
            return
        }

        val kuudra = level.entitiesForRendering()
            .filterIsInstance<MagmaCube>()
            .filter { it.isAlive && it.size == 30 && it.maxHealth >= 10_000f }
            .maxByOrNull { it.y }

        if (kuudra != null) {
            val detected = Direction.from(kuudra.x, kuudra.z)
            if (detected != Direction.UNKNOWN && detected != currentDirection) {
                currentDirection = detected
                activeTicksRemaining = displayTicks.value.toInt()
            }
        }

        if (activeTicksRemaining > 0) {
            activeTicksRemaining--
        }
    }

    fun shouldRender(): Boolean = enabled && activeTicksRemaining > 0 && currentDirection != Direction.UNKNOWN

    fun getDisplayText(): String {
        val base = currentDirection.label
        return if (bold.value) "§l$base" else base
    }

    fun getColor(): Int = currentDirection.color

    private enum class Direction(val label: String, val color: Int) {
        RIGHT("RIGHT!", 0xFFFF5555.toInt()),
        FRONT("FRONT!", 0xFF55FF55.toInt()),
        LEFT("LEFT!", 0xFFAAFF55.toInt()),
        BACK("BACK!", 0xFFAA0000.toInt()),
        UNKNOWN("UNKNOWN", 0xFFFFFFFF.toInt());

        companion object {
            fun from(x: Double, z: Double): Direction {
                if (x < -128.0) return RIGHT
                if (z > -84.0) return FRONT
                if (x > -72.0) return LEFT
                if (z < -132.0) return BACK
                return UNKNOWN
            }
        }
    }
}
