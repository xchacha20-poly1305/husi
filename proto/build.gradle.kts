import com.google.protobuf.gradle.id
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    `java-library`
    id("org.jetbrains.kotlin.jvm")
    alias(libs.plugins.protobuf)
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
    }
}

// Build outputs must stay out of the proto tree: the whole project directory is
// the protoc include path, so anything generated under it would turn every task
// writing there into an undeclared input of generateProto.
layout.buildDirectory.set(file("../build/proto"))

sourceSets {
    main {
        // This directory is also the buf module root, so that Gradle and
        // `make proto` compile the very same tree with the same import paths.
        proto {
            setSrcDirs(listOf(projectDir))
        }
    }
}

dependencies {
    // api, because every generated class exposes the protobuf runtime types,
    // and the generated kurpc clients the kurpc ones.
    api(libs.protobuf.kotlin.lite)
    api(libs.kurpc)
}

protobuf {
    protoc {
        artifact = libs.protobuf.protoc.get().toString()
    }
    plugins {
        id("kurpc") {
            artifact = "${libs.protoc.gen.kurpc.get()}:all@jar"
        }
    }
    generateProtoTasks {
        all().forEach { task ->
            task.builtins {
                named("java") {
                    option("lite")
                }
                create("kotlin") {
                    option("lite")
                }
            }
            task.plugins {
                id("kurpc")
            }
        }
    }
}

tasks.jar {
    // Consumers need the generated classes, not the schema they came from.
    exclude("**/*.proto")
}
