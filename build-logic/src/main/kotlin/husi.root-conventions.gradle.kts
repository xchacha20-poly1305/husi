import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    base
}

tasks.named<Delete>("clean") {
    delete(rootProject.layout.buildDirectory.get().asFile.path)
}

tasks.withType<AbstractArchiveTask>().configureEach {
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions {
        freeCompilerArgs.add(
            "-Xbackend-threads=${Runtime.getRuntime().availableProcessors()}"
        )
    }
}
