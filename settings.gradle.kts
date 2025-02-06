include(":plugin:api")

val buildPlugin = System.getenv("BUILD_PLUGIN")
if (!buildPlugin.isNullOrBlank()) {
    include(":plugin:$buildPlugin")
}

include(":app")

rootProject.name = "husi"

// https://issuetracker.google.com/issues/389508413
pluginManagement {
    buildscript {
        repositories {
            mavenCentral()
            maven {
                url = uri("https://storage.googleapis.com/r8-releases/raw")
            }
        }
        dependencies {
            classpath("com.android.tools:r8:8.8.27")
        }
    }
}
