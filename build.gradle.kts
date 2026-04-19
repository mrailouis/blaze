plugins {
	base
}

data class VersionTarget(
	val taskSuffix: String,
	val displayVersion: String,
	val projectPath: String,
)

val versionTargets = listOf(
	VersionTarget("12110", "1.21.10", ":versions:mc1_21_10"),
	VersionTarget("12111", "1.21.11", ":versions:mc1_21_11"),
	VersionTarget("261", "26.1", ":versions:mc26_1"),
)

tasks.named("assemble") {
	dependsOn(versionTargets.map { "${it.projectPath}:assemble" })
}

tasks.register("compileJava") {
	group = "build"
	description = "Compiles Java sources for all supported versions."
	dependsOn(versionTargets.map { "${it.projectPath}:compileJava" })
}

tasks.register("compileKotlin") {
	group = "build"
	description = "Compiles Kotlin sources for all supported versions."
	dependsOn(versionTargets.map { "${it.projectPath}:compileKotlin" })
}

tasks.register("processResources") {
	group = "build"
	description = "Processes resources for all supported versions."
	dependsOn(versionTargets.map { "${it.projectPath}:processResources" })
}

tasks.register("classes") {
	group = "build"
	description = "Assembles classes for all supported versions."
	dependsOn(versionTargets.map { "${it.projectPath}:classes" })
}

tasks.register("mainClasses") {
	group = "build"
	description = "Assembles main classes for all supported versions."
	dependsOn(versionTargets.map { "${it.projectPath}:mainClasses" })
}

tasks.register("compileTestJava") {
	group = "build"
	description = "Compiles test Java sources for all supported versions."
	dependsOn(versionTargets.map { "${it.projectPath}:compileTestJava" })
}

tasks.register("compileTestKotlin") {
	group = "build"
	description = "Compiles test Kotlin sources for all supported versions."
	dependsOn(versionTargets.map { "${it.projectPath}:compileTestKotlin" })
}

tasks.register("processTestResources") {
	group = "build"
	description = "Processes test resources for all supported versions."
	dependsOn(versionTargets.map { "${it.projectPath}:processTestResources" })
}

tasks.register("testClasses") {
	group = "build"
	description = "Assembles test classes for all supported versions."
	dependsOn(versionTargets.map { "${it.projectPath}:testClasses" })
}

tasks.named("check") {
	dependsOn(versionTargets.map { "${it.projectPath}:check" })
}

tasks.named("build") {
	dependsOn(versionTargets.map { "${it.projectPath}:build" })
}

tasks.named("clean") {
	dependsOn(versionTargets.map { "${it.projectPath}:clean" })
}

versionTargets.forEach { target ->
	tasks.register("build${target.taskSuffix}") {
		group = "build"
		description = "Builds Blaze for Minecraft ${target.displayVersion}."
		dependsOn("${target.projectPath}:build")
	}

	tasks.register("runClient${target.taskSuffix}") {
		group = "fabric"
		description = "Runs the Blaze client for Minecraft ${target.displayVersion}."
		dependsOn("${target.projectPath}:runClient")
	}

	tasks.register("runServer${target.taskSuffix}") {
		group = "fabric"
		description = "Runs the Blaze server for Minecraft ${target.displayVersion}."
		dependsOn("${target.projectPath}:runServer")
	}
}
