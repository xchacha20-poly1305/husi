@file:Suppress("UnstableApiUsage")

plugins {
    id("com.android.application")
    id("kotlin-parcelize")
    id("com.google.devtools.ksp") version "2.3.5"
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.0"
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
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
    }
    ksp {
        arg("room.incremental", "true")
        arg("room.schemaLocation", "$projectDir/schemas")
    }
    bundle {
        language {
            enableSplit = false
        }
    }
    buildFeatures {
        compose = true
        viewBinding = true
        aidl = true
        buildConfig = true
    }
    namespace = "io.nekohasekai.sagernet"

    kotlin {
        compilerOptions {
            optIn.add("androidx.compose.material3.ExperimentalMaterial3Api")
        }
    }


    tasks.withType<Test> {
        useJUnitPlatform()
//        include()
    }
}

dependencies {

    implementation(fileTree("libs"))

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-collections-immutable:0.4.0")

    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("androidx.activity:activity-ktx:1.12.3")
    implementation("androidx.browser:browser:1.9.0")

    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")

    implementation("androidx.work:work-runtime-ktx:2.11.0")
    implementation("androidx.work:work-multiprocess:2.11.0")

    implementation("androidx.datastore:datastore:1.2.0")
    implementation("androidx.datastore:datastore-preferences:1.2.0")
    implementation("androidx.datastore:datastore-preferences-core:1.2.0")

    implementation(platform("androidx.compose:compose-bom:2026.01.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.ui:ui-viewbinding")
    implementation("androidx.compose.material3:material3:1.5.0-alpha12")
    implementation("androidx.compose.animation:animation-graphics")
    implementation("androidx.activity:activity-compose:1.12.2")
    implementation("androidx.navigation:navigation-compose:2.9.6")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("com.google.accompanist:accompanist-drawablepainter:0.37.3")
    implementation("com.google.accompanist:accompanist-permissions:0.37.3")
    implementation("me.zhanghai.compose.preference:preference:2.1.1")
    implementation("io.github.oikvpqya.compose.fastscroller:fastscroller-core:0.3.2")
    implementation("io.github.oikvpqya.compose.fastscroller:fastscroller-material3:0.3.2")
    implementation(project(":library:DragDropSwipeLazyColumn:drag-drop-swipe-lazycolumn"))
    implementation(project(":library:compose-code-editor:codeeditor"))

    implementation("androidx.camera:camera-core:1.5.2")
    implementation("androidx.camera:camera-lifecycle:1.5.2")
    implementation("androidx.camera:camera-camera2:1.5.3")
    implementation("androidx.camera:camera-compose:1.5.2")
    implementation("com.google.zxing:core:3.5.4")

    implementation("androidx.room:room-runtime:2.8.4")
    implementation("androidx.room:room-ktx:2.8.4")
    ksp("androidx.room:room-compiler:2.8.4")

    implementation("com.google.code.gson:gson:2.13.2")
    implementation("com.android.tools.smali:smali-dexlib2:3.0.9") {
        exclude(group = "com.google.guava", module = "guava")
    }
    implementation("com.google.guava:guava:33.5.0-android")

    implementation("com.jakewharton:process-phoenix:3.0.0")
    implementation("com.esotericsoftware:kryo:5.6.2")
    implementation("org.ini4j:ini4j:0.5.4")

    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")

    // testImplementation("io.mockk:mockk:1.14.2")
    testImplementation(platform("org.junit:junit-bom:6.0.2"))
    testImplementation("org.junit.jupiter:junit-jupiter-api")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
}
