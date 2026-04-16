package me.mrai.larpclient.module

import org.lwjgl.glfw.GLFW

open class Setting(
    val name: String
) {
    private var visibilityProvider: () -> Boolean = { true }

    fun isVisible(): Boolean = runCatching(visibilityProvider).getOrDefault(true)

    internal fun setVisibilityProvider(provider: () -> Boolean) {
        visibilityProvider = provider
    }
}

class BoolSetting(
    name: String,
    var value: Boolean = false
) : Setting(name)

class SliderSetting(
    name: String,
    vararg args: Any
) : Setting(name) {

    var value: Double
    val min: Double
    val max: Double
    val step: Double

    init {
        val nums = args.mapNotNull {
            when (it) {
                is Number -> it.toDouble()
                else -> null
            }
        }

        when (nums.size) {
            4 -> {
                // Expected modern order: value, min, max, step
                value = nums[0]
                min = nums[1]
                max = nums[2]
                step = nums[3]
            }

            3 -> {
                // Legacy order: min, max, step
                min = nums[0]
                max = nums[1]
                step = nums[2]
                value = min
            }

            else -> {
                value = 0.0
                min = 0.0
                max = 1.0
                step = 0.1
            }
        }
    }
}

class ModeSetting(
    name: String,
    vararg args: Any
) : Setting(name) {

    val modes: List<String>
    var selectedIndex: Int

    val selected: String
        get() = modes.getOrElse(selectedIndex) { modes.firstOrNull() ?: "" }

    init {
        when {
            args.isEmpty() -> {
                modes = listOf("Default")
                selectedIndex = 0
            }

            args.size == 1 && args[0] is List<*> -> {
                modes = (args[0] as List<*>).map { it.toString() }
                selectedIndex = 0
            }

            args.size == 2 && args[0] is List<*> && args[1] is String -> {
                modes = (args[0] as List<*>).map { it.toString() }
                val wanted = args[1].toString()
                selectedIndex = modes.indexOf(wanted).takeIf { it >= 0 } ?: 0
            }

            args.size == 2 && args[0] is List<*> && args[1] is Number -> {
                modes = (args[0] as List<*>).map { it.toString() }
                selectedIndex = (args[1] as Number).toInt().coerceIn(0, (modes.size - 1).coerceAtLeast(0))
            }

            else -> {
                modes = args.map { it.toString() }
                selectedIndex = 0
            }
        }
    }
}

class KeybindSetting(
    first: Any,
    second: Any? = null
) : Setting(
    when {
        first is String -> first
        second is String -> second
        else -> "Keybind"
    }
) {

    var key: Int = GLFW.GLFW_KEY_UNKNOWN

    init {
        when {
            first is String && second is Number -> key = second.toInt()
            first is Number && second is String -> key = first.toInt()
            first is String && second == null -> key = GLFW.GLFW_KEY_UNKNOWN
        }
    }

    val keyName: String
        get() = when (key) {
            GLFW.GLFW_KEY_UNKNOWN -> "None"
            GLFW.GLFW_KEY_LEFT_SHIFT -> "LShift"
            GLFW.GLFW_KEY_RIGHT_SHIFT -> "RShift"
            GLFW.GLFW_KEY_LEFT_CONTROL -> "LCtrl"
            GLFW.GLFW_KEY_RIGHT_CONTROL -> "RCtrl"
            GLFW.GLFW_KEY_LEFT_ALT -> "LAlt"
            GLFW.GLFW_KEY_RIGHT_ALT -> "RAlt"
            GLFW.GLFW_KEY_ESCAPE -> "Esc"
            GLFW.GLFW_KEY_ENTER -> "Enter"
            GLFW.GLFW_KEY_TAB -> "Tab"
            GLFW.GLFW_KEY_BACKSPACE -> "Backspace"
            GLFW.GLFW_KEY_DELETE -> "Delete"
            GLFW.GLFW_KEY_INSERT -> "Insert"
            GLFW.GLFW_KEY_HOME -> "Home"
            GLFW.GLFW_KEY_END -> "End"
            GLFW.GLFW_KEY_PAGE_UP -> "PageUp"
            GLFW.GLFW_KEY_PAGE_DOWN -> "PageDown"
            GLFW.GLFW_KEY_UP -> "Up"
            GLFW.GLFW_KEY_DOWN -> "Down"
            GLFW.GLFW_KEY_LEFT -> "Left"
            GLFW.GLFW_KEY_RIGHT -> "Right"
            else -> GLFW.glfwGetKeyName(key, 0)?.uppercase() ?: "KEY_$key"
        }
}

class ComponentSetting(
    name: String,
    var text: String = ""
) : Setting(name)

class ActionSetting(
    name: String,
    private val labelProvider: () -> String = { name },
    private val action: () -> Unit
) : Setting(name) {
    val label: String
        get() = labelProvider()

    fun click() = action()
}

class InfoSetting(
    name: String,
    private val valueProvider: () -> String
) : Setting(name) {
    val value: String
        get() = valueProvider()
}

fun <T : Setting> T.shownWhen(predicate: () -> Boolean): T {
    setVisibilityProvider(predicate)
    return this
}
