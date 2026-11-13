import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

buildscript {
    repositories {
        mavenLocal()
        mavenCentral()
    }
    dependencies {
        classpath(libs.kurpc.wire)
    }
}

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.kotlin.multiplatform.library")
    alias(libs.plugins.wire)
}

layout.buildDirectory.set(file("../build/proto"))

kotlin {
    android {
        namespace = "fr.husi.proto"
        compileSdk = 37
        minSdk = 21
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
        }
    }
    jvm("desktop") {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
        }
    }
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
    }
    sourceSets.commonMain.dependencies {
        api(libs.wire.runtime)
        api(libs.kurpc)
    }
}

wire {
    sourcePath {
        srcDir(projectDir)
        include("daemon/*.proto", "husi/v1/*.proto", "mieru/*.proto")
    }
    kotlin {
        // These API enums must expose new values instead of defaulting to IDLE, NEW or PANIC.
        includes = listOf("daemon.ServiceStatus", "daemon.ConnectionEventType", "daemon.LogLevel")
        enumMode = "sealed_class"
        rpcRole = "none"
    }
    kotlin {
        rpcRole = "none"
    }
    custom {
        schemaHandlerFactoryClass.set("io.github.xchacha20_poly1305.kurpc.wire.KurpcSchemaHandlerFactory")
    }
}
