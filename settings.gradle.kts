pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }
    plugins {
        id("org.jetbrains.kotlin.plugin.compose") version "2.3.20-Beta2"
        id("org.jetbrains.compose") version "1.10.1"
        id("org.jetbrains.dokka") version "1.9.20"
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

include(":plugin:api")

val buildPlugin = System.getenv("BUILD_PLUGIN")
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
include(":library:compose-code-editor:codeeditor")
include(":library:DragDropSwipeLazyColumn")

rootProject.name = "husi"
