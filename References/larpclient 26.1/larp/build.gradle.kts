import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.gradle.jvm.tasks.Jar

plugins {
    id("net.fabricmc.fabric-loom")
    `maven-publish`
    id("org.jetbrains.kotlin.jvm") version "2.3.20"
}

version = providers.gradleProperty("mod_version").get()
group = providers.gradleProperty("maven_group").get()

val publicArchiveName = "larp"
val publicModName = "Larp"

base {
    archivesName = publicArchiveName
}

repositories {
    mavenCentral()
}

dependencies {
    minecraft("com.mojang:minecraft:${providers.gradleProperty("minecraft_version").get()}")

    implementation("net.fabricmc:fabric-loader:${providers.gradleProperty("loader_version").get()}")
    implementation("net.fabricmc.fabric-api:fabric-api:${providers.gradleProperty("fabric_api_version").get()}")
    implementation("net.fabricmc:fabric-language-kotlin:${providers.gradleProperty("fabric_kotlin_version").get()}")

    implementation("io.github.CDAGaming:DiscordIPC:0.10.2")
    include("io.github.CDAGaming:DiscordIPC:0.10.2")

    testImplementation(kotlin("test"))
}

tasks.processResources {
    inputs.property("version", version)
    inputs.property("mod_name", publicModName)

    filesMatching("fabric.mod.json") {
        expand(
            "version" to version,
            "mod_name" to publicModName
        )
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 22
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_22
    }
    jvmToolchain(25)
}

java {
    withSourcesJar()
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }

    sourceCompatibility = JavaVersion.VERSION_22
    targetCompatibility = JavaVersion.VERSION_22
}

tasks.named<Jar>("jar") {
    inputs.property("archivesName", base.archivesName)

    from(rootProject.file("LICENSE")) {
        rename { "${it}_${base.archivesName.get()}" }
    }
}

publishing {
    publications {
        register<MavenPublication>("mavenJava") {
            artifactId = base.archivesName.get()
            from(components["java"])
        }
    }

    repositories {
    }
}

tasks.register("buildLarp") {
    group = "build"
    description = "Builds the public Larp mod."
    dependsOn("build")
}

tasks.register("runClientLarp") {
    group = "fabric"
    description = "Runs the public Larp mod."
    dependsOn("runClient")
}

val addonProjectPath = rootProject.findProject(":larp-addon")?.path

tasks.register("buildLarpClient") {
    group = "build"
    description = "Builds Larp together with the LarpClient addon jar when the addon project is available."
    dependsOn(addonProjectPath?.let { "$it:build" } ?: "build")
}

tasks.register("runClientLarpClient") {
    group = "fabric"
    description = "Runs Larp together with the LarpClient addon when the addon project is available."
    dependsOn(addonProjectPath?.let { "$it:runClient" } ?: "runClient")
}

tasks.register("buildPublic") {
    group = "build"
    description = "Alias for buildLarp."
    dependsOn("buildLarp")
}

tasks.register("runClientPublic") {
    group = "fabric"
    description = "Alias for runClientLarp."
    dependsOn("runClientLarp")
}

tasks.register("buildAddon") {
    group = "build"
    description = "Alias for buildLarpClient."
    dependsOn("buildLarpClient")
}

tasks.register("runClientAddon") {
    group = "fabric"
    description = "Alias for runClientLarpClient."
    dependsOn("runClientLarpClient")
}
