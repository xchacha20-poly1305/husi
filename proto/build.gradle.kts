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
        // Gradle and `make proto` compile the same schema tree and import paths.
        proto {
            setSrcDirs(listOf(projectDir))
        }
    }
    create("web") {
        proto {
            setSrcDirs(listOf(projectDir))
            setIncludes(listOf("daemon/started_service.proto"))
        }
    }
}

val wireCompiler = configurations.create("wireCompiler")
val webSources = configurations.create("webSources") {
    isCanBeConsumed = true
    isCanBeResolved = false
}

dependencies {
    // api, because every generated class exposes the protobuf runtime types,
    // and the generated kurpc clients the kurpc ones.
    api(libs.protobuf.kotlin.lite)
    api(libs.kurpc)
    add(wireCompiler.name, libs.wire.compiler)
    // Supplies well-known proto imports to the web protoc invocation.
    add("webCompileOnly", libs.protobuf.kotlin.lite)
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
        ofSourceSet("main").configureEach {
            val task = this
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
        ofSourceSet("web").configureEach {
            builtins.clear()
            plugins {
                id("kurpc") { option("runtime=wire") }
            }
        }
    }
}

val generateWebMessages = tasks.register<JavaExec>("generateWebMessages") {
    description = "Generate Wire messages for the browser's kurpc client."
    val schema = layout.projectDirectory.file("daemon/started_service.proto")
    val output = layout.buildDirectory.dir("generated/wire/web")
    inputs.file(schema).withPathSensitivity(PathSensitivity.RELATIVE)
    outputs.dir(output)
    classpath = wireCompiler
    mainClass.set("com.squareup.wire.WireCompiler")
    args(
        "--proto_path=$projectDir",
        "--kotlin_out=${output.get().asFile}",
        "--kotlin_rpc_role=none",
        "daemon/started_service.proto",
    )
    val outputDirectory = output.get().asFile
    doFirst { outputDirectory.deleteRecursively() }
}

// Share generated Kotlin sources without placing Wire and protobuf-java's identically
// named messages on the same classpath. The web source set is only used for generation.
artifacts {
    add(webSources.name, layout.buildDirectory.dir("generated/wire/web")) {
        builtBy(generateWebMessages)
    }
    add(webSources.name, layout.buildDirectory.dir("generated/sources/proto/web/kurpc")) {
        builtBy("generateWebProto")
    }
}

tasks.jar {
    // Consumers need the generated classes, not the schema they came from.
    exclude("**/*.proto")
}
