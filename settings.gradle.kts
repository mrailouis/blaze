pluginManagement {
	repositories {
		maven {
			name = "Fabric"
			url = uri("https://maven.fabricmc.net/")
		}
		mavenCentral()
		gradlePluginPortal()
	}

	plugins {
		id("net.fabricmc.fabric-loom-remap") version providers.gradleProperty("loom_version").get()
		id("net.fabricmc.fabric-loom") version providers.gradleProperty("loom_version").get()
	}
}

rootProject.name = "blaze"

include(":versions:mc1_21_10")
project(":versions:mc1_21_10").projectDir = file("versions/1.21.10")

include(":versions:mc1_21_11")
project(":versions:mc1_21_11").projectDir = file("versions/1.21.11")

include(":versions:mc26_1")
project(":versions:mc26_1").projectDir = file("versions/26.1")
