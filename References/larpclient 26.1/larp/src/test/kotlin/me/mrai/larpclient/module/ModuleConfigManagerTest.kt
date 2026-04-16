package me.mrai.larpclient.module

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ModuleConfigManagerTest {
    @Test
    fun `module config round trips module state and settings`() {
        val module = Module("Example", "desc", ModuleCategory.MISC_OTHER).apply {
            enabled = true
            bindKey = 65
            settings += BoolSetting("Enabled Setting", true)
            settings += SliderSetting("Speed", 4.5, 0.0, 10.0, 0.5)
            settings += ModeSetting("Mode", listOf("One", "Two"), "Two")
            settings += ComponentSetting("Label", "hello")
            settings += KeybindSetting("Action Key", 66)
        }

        val root = ModuleConfigManager.toJson(listOf(module))

        module.enabled = false
        module.bindKey = -1
        (module.settings[0] as BoolSetting).value = false
        (module.settings[1] as SliderSetting).value = 1.0
        (module.settings[2] as ModeSetting).selectedIndex = 0
        (module.settings[3] as ComponentSetting).text = "changed"
        (module.settings[4] as KeybindSetting).key = 67

        ModuleConfigManager.applyJson(root, listOf(module))

        assertTrue(module.enabled)
        assertEquals(65, module.bindKey)
        assertTrue((module.settings[0] as BoolSetting).value)
        assertEquals(4.5, (module.settings[1] as SliderSetting).value)
        assertEquals("Two", (module.settings[2] as ModeSetting).selected)
        assertEquals("hello", (module.settings[3] as ComponentSetting).text)
        assertEquals(66, (module.settings[4] as KeybindSetting).key)
    }

    @Test
    fun `module settings are restored before onEnable runs`() {
        val toggle = BoolSetting("Show Username", false)
        var observedValueOnEnable: Boolean? = null

        val module = object : Module("Discord Rich Presence", "desc", ModuleCategory.MISC_OTHER) {
            init {
                settings += toggle
            }

            override fun onEnable() {
                observedValueOnEnable = toggle.value
            }
        }

        val root = ModuleConfigManager.toJson(listOf(module))
        val storedModule = root.getAsJsonArray("modules")[0].asJsonObject
        storedModule.addProperty("enabled", true)
        storedModule.getAsJsonObject("settings").addProperty("Show Username", true)

        ModuleConfigManager.applyJson(root, listOf(module))

        assertTrue(module.enabled)
        assertTrue(toggle.value)
        assertNotNull(observedValueOnEnable)
        assertTrue(observedValueOnEnable == true)
    }
}
