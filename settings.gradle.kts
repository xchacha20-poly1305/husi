pluginManagement {
    // In a composite build, AGP allows Kotlin's npm resolver to run during task graph creation.
    includeBuild("build-logic")

    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        maven("https://packages.jetbrains.team/maven/p/cmp/dev")
    }
    plugins {
        id("org.jetbrains.kotlin.plugin.compose") version "2.4.21"
        id("org.jetbrains.compose") version "1.12.2"
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositories {
        mavenLocal()
        google()
        mavenCentral()
        maven("https://packages.jetbrains.team/maven/p/cmp/dev")
        gradlePluginPortal()
    }
}

include(":plugin:api")

val pluginApks = listOf("hysteria2", "juicity", "mieru", "shadowquic")
val buildPlugin = providers.environmentVariable("BUILD_PLUGIN").orNull
when {
    buildPlugin.isNullOrBlank() -> {
        pluginApks.forEach { include(":plugin:$it") }
    }
    buildPlugin == "none" -> {
    }
    else -> {
        require(buildPlugin in pluginApks) { "Unknown plugin APK: $buildPlugin" }
        include(":plugin:$buildPlugin")
    }
}

include(":androidApp")
include(":composeApp")
include(":proto")
include(":library:DragDropSwipeLazyColumn")
include(":library:ComposePreference")
project(":library:ComposePreference").apply {
    projectDir = file("library/ComposePreference/preference")
    buildFileName = "husi.gradle.kts"
}

rootProject.name = "husi"
