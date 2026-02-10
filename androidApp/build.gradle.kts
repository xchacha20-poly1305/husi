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
        ndkVersion = "29.0.14206865"
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
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
    }
    namespace = "fr.husi"

}

dependencies {
    implementation(project(":composeApp"))
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    debugImplementation(project.dependencies.platform(libs.androidx.compose.bom))
    debugImplementation(libs.androidx.compose.ui.tooling)
}
