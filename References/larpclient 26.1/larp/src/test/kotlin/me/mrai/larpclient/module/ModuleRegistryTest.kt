package me.mrai.larpclient.module

import kotlin.test.Test
import kotlin.test.assertEquals

class ModuleRegistryTest {
    @Test
    fun `registry filters modules by distribution`() {
        val registry = ModuleRegistry()
        registry.register({ Module("Both", "desc", ModuleCategory.MISC_OTHER) })
        registry.register({ Module("FullOnly", "desc", ModuleCategory.MISC_OTHER) }, ModuleDistribution.FULL)
        registry.register({ Module("LegitOnly", "desc", ModuleCategory.MISC_OTHER) }, ModuleDistribution.LEGIT)

        val fullNames = registry.createModulesFor(ModuleDistribution.FULL).map { it.name }
        val legitNames = registry.createModulesFor(ModuleDistribution.LEGIT).map { it.name }

        assertEquals(listOf("Both", "FullOnly"), fullNames)
        assertEquals(listOf("Both", "LegitOnly"), legitNames)
    }
}
