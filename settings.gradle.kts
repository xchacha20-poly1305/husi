pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }
    plugins {
        id("org.jetbrains.kotlin.plugin.compose") version "2.4.10"
        id("org.jetbrains.compose") version "1.11.1"
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

include(":plugin:api")

val buildPlugin = providers.environmentVariable("BUILD_PLUGIN").orNull
when {
    buildPlugin.isNullOrBlank() -> {
        include(":plugin:hysteria2")
        include(":plugin:juicity")
        include(":plugin:naive")
        include(":plugin:mieru")
        include(":plugin:shadowquic")
    }
    buildPlugin == "none" -> {
    }
    else -> {
        include(":plugin:$buildPlugin")
    }
}

include(":androidApp")
include(":composeApp")
include(":proto")
include(":library:DragDropSwipeLazyColumn")

rootProject.name = "husi"
