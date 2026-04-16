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
		id("net.fabricmc.fabric-loom") version providers.gradleProperty("loom_version")
	}
}

rootProject.name = "larpclient-workspace"

include(":larp")
include(":larp-addon")

project(":larp").projectDir = file("larp")
project(":larp-addon").projectDir = file("larp-addon")
