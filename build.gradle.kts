import org.gradle.kotlin.dsl.withType

plugins {
    id("husi.root-conventions")
}

// Use the Node installation supplied by the developer or CI.
plugins.withType<org.jetbrains.kotlin.gradle.targets.wasm.nodejs.WasmNodeJsRootPlugin> {
    extensions.configure<org.jetbrains.kotlin.gradle.targets.wasm.nodejs.WasmNodeJsEnvSpec> {
        download.set(false)
    }
}

subprojects {
    plugins.withType<org.jetbrains.kotlin.gradle.targets.wasm.nodejs.WasmNodeJsPlugin> {
        extensions.configure<org.jetbrains.kotlin.gradle.targets.wasm.nodejs.WasmNodeJsEnvSpec> {
            download.set(false)
            command.set("node")
        }
    }
}

// Extend the vendored preference module's Husi target set without modifying the submodule.
subprojects {
    if (path == ":library:ComposePreference") {
        plugins.withId("org.jetbrains.kotlin.multiplatform") {
            extensions.configure<org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension> {
                @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
                wasmJs { browser() }
                sourceSets.named("wasmJsMain") {
                    kotlin.srcDir("src/jvmWasmJsMain/kotlin")
                    dependencies { implementation(libs.kotlinx.serialization.json) }
                }
            }
        }
    }
}
