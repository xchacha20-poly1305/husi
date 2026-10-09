@file:Suppress("UnstableApiUsage")

plugins {
    id("com.android.application")
}

setupApp()

android {
    defaultConfig {
        splits.abi {
            reset()
            include(
                "arm64-v8a",
                "armeabi-v7a",
                "x86_64",
                "x86",
            )
        }
        // Keep equal to ANDROID_NDK_VERSION in buildScript/init/version.sh.
        ndkVersion = "30.0.16248370"
    }
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
    bundle {
        language {
            enableSplit = false
        }
    }
    buildFeatures {
        buildConfig = false
    }
    namespace = "fr.husi"
    compileOptions {
        // kotlinx-datetime runs on java.time, which Android only ships from API 26.
        isCoreLibraryDesugaringEnabled = true
    }
}

dependencies {
    coreLibraryDesugaring(libs.android.desugar.jdk.libs)
    implementation(project(":composeApp"))
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.room.runtime)
    debugImplementation(project.dependencies.platform(libs.androidx.compose.bom))
    debugImplementation(libs.androidx.compose.ui.tooling)
}
