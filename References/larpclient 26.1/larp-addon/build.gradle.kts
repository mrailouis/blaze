import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.gradle.jvm.tasks.Jar

plugins {
    id("net.fabricmc.fabric-loom")
    `maven-publish`
    id("org.jetbrains.kotlin.jvm") version "2.3.20"
}

version = rootProject.providers.gradleProperty("mod_version").get()
group = rootProject.providers.gradleProperty("maven_group").get()
val publicModProject = project(":larp")

val addonArchiveName = "larp-addon"
val addonModName = "LarpClient"

base {
    archivesName = addonArchiveName
}

repositories {
    mavenCentral()
}

dependencies {
    minecraft("com.mojang:minecraft:${rootProject.providers.gradleProperty("minecraft_version").get()}")

    implementation("net.fabricmc:fabric-loader:${rootProject.providers.gradleProperty("loader_version").get()}")
    implementation("net.fabricmc.fabric-api:fabric-api:${rootProject.providers.gradleProperty("fabric_api_version").get()}")
    implementation("net.fabricmc:fabric-language-kotlin:${rootProject.providers.gradleProperty("fabric_kotlin_version").get()}")

    implementation(publicModProject)

    testImplementation(kotlin("test"))
}

tasks.processResources {
    inputs.property("version", version)
    inputs.property("mod_name", addonModName)

    filesMatching("fabric.mod.json") {
        expand(
            "version" to version,
            "mod_name" to addonModName
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
