import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
	id("net.fabricmc.fabric-loom-remap")
	`maven-publish`
	id("org.jetbrains.kotlin.jvm") version "2.3.20"
}

version = rootProject.providers.gradleProperty("mod_version").get()
group = rootProject.providers.gradleProperty("maven_group").get()

val minecraftVersion = "1.21.11"
val fabricApiVersion = "0.141.3+1.21.11"
val javaVersion = 21
val modId = rootProject.providers.gradleProperty("mod_id").get()

base {
	archivesName = "${modId}-${minecraftVersion}"
}

repositories {
}

sourceSets {
	named("main") {
		java.srcDirs(rootProject.file("src/main/java"), project.file("src/main/java"))
		resources.srcDirs(rootProject.file("src/main/resources"), project.file("src/main/resources"))
	}
}

kotlin {
	sourceSets.named("main") {
		kotlin.srcDirs(rootProject.file("src/main/kotlin"), project.file("src/main/kotlin"))
	}

	compilerOptions {
		jvmTarget = JvmTarget.fromTarget(javaVersion.toString())
	}
}

dependencies {
	minecraft("com.mojang:minecraft:$minecraftVersion")
	mappings(loom.officialMojangMappings())
	modImplementation("net.fabricmc:fabric-loader:${rootProject.providers.gradleProperty("loader_version").get()}")
	modImplementation("net.fabricmc.fabric-api:fabric-api:$fabricApiVersion")
	modImplementation("net.fabricmc:fabric-language-kotlin:${rootProject.providers.gradleProperty("fabric_kotlin_version").get()}")
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
	options.release = javaVersion
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
