plugins {
    `java-gradle-plugin`
    `kotlin-dsl`
}

apply(from = "../repositories.gradle.kts")

dependencies {
    // Gradle Plugins
    implementation(libs.agp.gradle.plugin)
    implementation(libs.kotlin.gradle.plugin)
}
