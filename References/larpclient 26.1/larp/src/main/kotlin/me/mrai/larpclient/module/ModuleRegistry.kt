package me.mrai.larpclient.module

enum class ModuleDistribution {
    FULL,
    LEGIT
}

data class ModuleDefinition(
    val create: () -> Module,
    val distributions: Set<ModuleDistribution>
)

class ModuleRegistry {
    private val definitions = mutableListOf<ModuleDefinition>()

    fun register(
        create: () -> Module,
        vararg distributions: ModuleDistribution
    ) {
        definitions += ModuleDefinition(
            create = create,
            distributions = if (distributions.isEmpty()) ModuleDistribution.entries.toSet() else distributions.toSet()
        )
    }

    fun createModulesFor(distribution: ModuleDistribution): List<Module> {
        return definitions
            .asSequence()
            .filter { distribution in it.distributions }
            .map { it.create() }
            .toList()
    }
}
