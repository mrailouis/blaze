import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
	id("net.fabricmc.fabric-loom")
	`maven-publish`
	id("org.jetbrains.kotlin.jvm") version "2.3.20"
}

version = rootProject.providers.gradleProperty("mod_version").get()
group = rootProject.providers.gradleProperty("maven_group").get()

val minecraftVersion = "26.1"
val fabricApiVersion = "0.145.1+26.1"
val javaVersion = 25
val modId = rootProject.providers.gradleProperty("mod_id").get()
val overrideKotlinDir = project.file("src/main/kotlin")
val overrideJavaDir = project.file("src/main/java")
val overrideResourceDir = project.file("src/main/resources")

fun relativePaths(root: java.io.File, extension: String): List<String> {
	if (!root.exists()) {
		return emptyList()
	}

	return root.walkTopDown()
		.filter { it.isFile && it.extension == extension }
		.map { it.relativeTo(root).invariantSeparatorsPath }
		.toList()
}

val overrideKotlinPaths = relativePaths(overrideKotlinDir, "kt")
val overrideJavaPaths = relativePaths(overrideJavaDir, "java")
val overrideResourcePaths = if (overrideResourceDir.exists()) {
	overrideResourceDir.walkTopDown()
		.filter { it.isFile }
		.map { it.relativeTo(overrideResourceDir).invariantSeparatorsPath }
		.toList()
} else {
	emptyList()
}

base {
	archivesName = "${modId}-${minecraftVersion}"
}

repositories {
}

sourceSets {
	named("main") {
		java.srcDirs(rootProject.file("src/main/java"), project.file("src/main/java"))
		resources.srcDirs(project.file("src/main/resources"))
	}
}

kotlin {
	sourceSets.named("main") {
		kotlin.srcDirs(project.file("src/main/kotlin"))
	}

	compilerOptions {
		jvmTarget = JvmTarget.fromTarget(javaVersion.toString())
	}
}

dependencies {
	minecraft("com.mojang:minecraft:$minecraftVersion")
	implementation("net.fabricmc:fabric-loader:${rootProject.providers.gradleProperty("loader_version").get()}")
	implementation("net.fabricmc.fabric-api:fabric-api:$fabricApiVersion")
	implementation("net.fabricmc:fabric-language-kotlin:${rootProject.providers.gradleProperty("fabric_kotlin_version").get()}")
}

loom {
	runs {
		named("client") {
			configName = "Blaze $minecraftVersion Client"
			runDir = rootProject.layout.projectDirectory.dir("run/$minecraftVersion").asFile.absolutePath
		}
		named("server") {
			configName = "Blaze $minecraftVersion Server"
			runDir = rootProject.layout.projectDirectory.dir("run/$minecraftVersion").asFile.absolutePath
		}
	}
}

tasks.processResources {
	from(rootProject.file("src/main/resources")) {
		exclude(overrideResourcePaths)
	}
	from(project.file("src/main/resources"))

	inputs.properties(
		mapOf(
			"version" to version,
			"minecraft_dependency" to minecraftVersion,
			"java_dependency" to javaVersion,
		)
	)

	filesMatching(listOf("fabric.mod.json", "blaze.properties")) {
		expand(
			"version" to version,
			"minecraft_dependency" to minecraftVersion,
			"java_dependency" to javaVersion,
		)
	}
}

tasks.withType<JavaCompile>().configureEach {
	source(rootProject.fileTree("src/main/java") {
		exclude(overrideJavaPaths)
	})
	source(project.fileTree("src/main/java"))
	options.release = javaVersion
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
	source(rootProject.fileTree("src/main/kotlin") {
		exclude(overrideKotlinPaths)
	})
	source(project.fileTree("src/main/kotlin"))
}

tasks.withType<Test>().configureEach {
	failOnNoDiscoveredTests = false
}

java {
	withSourcesJar()
	sourceCompatibility = JavaVersion.toVersion(javaVersion)
	targetCompatibility = JavaVersion.toVersion(javaVersion)
}

tasks.jar {
	inputs.property("projectName", project.name)

	from(rootProject.file("LICENSE")) {
		rename { "${it}_${project.name}" }
	}
}

publishing {
	publications {
		register<MavenPublication>("mavenJava") {
			from(components["java"])
		}
	}

	repositories {
	}
}
