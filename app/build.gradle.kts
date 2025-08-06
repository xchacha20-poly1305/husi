@file:Suppress("UnstableApiUsage")

plugins {
    id("com.android.application")
    id("kotlin-android")
    id("kotlin-parcelize")
    id("com.google.devtools.ksp") version "2.2.0-2.0.2"
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
                "x86"
            )
        }
        ndkVersion = "28.2.13676358"
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
        viewBinding = true
        aidl = true
        buildConfig = true
    }
    namespace = "io.nekohasekai.sagernet"

    tasks.withType<Test> {
        useJUnitPlatform()
//        include()
    }
}

dependencies {

    implementation(fileTree("libs"))

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.recyclerview:recyclerview:1.4.0")
    implementation("androidx.activity:activity-ktx:1.10.1")
    implementation("androidx.fragment:fragment-ktx:1.8.8")
    implementation("androidx.browser:browser:1.9.0")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    implementation("androidx.constraintlayout:constraintlayout:2.2.1")
    implementation("androidx.navigation:navigation-fragment-ktx:2.9.3")
    implementation("androidx.navigation:navigation-ui-ktx:2.9.3")
    implementation("androidx.preference:preference-ktx:1.2.1")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("androidx.work:work-runtime-ktx:2.10.3")
    implementation("androidx.work:work-multiprocess:2.10.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.9.2")

    implementation("com.google.android.material:material:1.12.0")
    implementation("com.google.code.gson:gson:2.13.1")

    implementation("com.blacksquircle.ui:editorkit:2.9.0")
    implementation("com.blacksquircle.ui:language-base:2.9.0")
    implementation("com.blacksquircle.ui:language-json:2.9.0")

    implementation("androidx.camera:camera-view:1.4.2")
    implementation("androidx.camera:camera-lifecycle:1.4.2")
    implementation("androidx.camera:camera-camera2:1.4.2")
    implementation("com.google.zxing:core:3.5.3")

    implementation("com.jakewharton:process-phoenix:3.0.0")
    implementation("com.esotericsoftware:kryo:5.6.2")
    implementation("org.ini4j:ini4j:0.5.4")
    implementation("com.twofortyfouram:android-plugin-api-for-locale:1.0.4")

    implementation("com.android.tools.smali:smali-dexlib2:3.0.9") {
        exclude(group = "com.google.guava", module = "guava")
    }
    implementation("com.google.guava:guava:33.4.8-android")

    implementation("androidx.room:room-runtime:2.7.2")
    implementation("dev.rikka.rikkax.preference:simplemenu-preference:1.0.3")
    ksp("androidx.room:room-compiler:2.7.2")
    implementation("androidx.room:room-ktx:2.7.2")
    implementation("com.github.MatrixDev.Roomigrant:RoomigrantLib:0.3.4")
    ksp("com.github.MatrixDev.Roomigrant:RoomigrantCompiler:0.3.4")

    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")

    // testImplementation("io.mockk:mockk:1.14.2")
    testImplementation(platform("org.junit:junit-bom:5.13.4"))
    testImplementation("org.junit.jupiter:junit-jupiter-api")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
}
