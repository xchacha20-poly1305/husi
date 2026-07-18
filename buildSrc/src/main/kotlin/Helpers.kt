@file:Suppress("UnstableApiUsage")

import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.CommonExtension
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import org.gradle.api.JavaVersion
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.Project
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Exec
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.register
import java.io.File
import java.util.Base64
import java.util.Locale
import java.util.Properties
import kotlin.system.exitProcess

private val Project.android: CommonExtension
    get() = extensions.getByName("android") as CommonExtension

private val Project.androidApp: ApplicationExtension
    get() = extensions.getByType<ApplicationExtension>()

fun Project.requireFlavor(): String {
    if (gradle.startParameter.taskNames.isNotEmpty()) {
        val taskName = gradle.startParameter.taskNames[0]
        when {
            taskName.contains("assemble") -> {
                return taskName.substringAfter("assemble")
            }

            taskName.contains("install") -> {
                return taskName.substringAfter("install")
            }

            taskName.contains("bundle") -> {
                return taskName.substringAfter("bundle")
            }
        }
    }

    return ""
}

private fun parseProperties(content: String): Properties =
    Properties().also { properties ->
        properties.load(content.byteInputStream())
    }

fun Project.requireMetadata(key: String): Provider<String> =
    providers.fileContents(rootProject.layout.projectDirectory.file("husi.properties")).asText.map { content ->
        parseProperties(content).getProperty(key)
            ?: error("Missing '$key' in husi.properties.")
    }

private fun Project.localProperties(): Provider<Properties> {
    val localPropertiesFile = rootProject.layout.projectDirectory.file("local.properties")
    val fromFile = providers.fileContents(localPropertiesFile).asText.orElse("").map(::parseProperties)
    return providers.environmentVariable("LOCAL_PROPERTIES").flatMap { encoded ->
        if (encoded.isBlank()) {
            fromFile
        } else {
            providers.provider {
                Properties().also { properties ->
                    properties.load(Base64.getDecoder().decode(encoded).inputStream())
                }
            }
        }
    }.orElse(fromFile)
}

fun Project.requireLocalProperty(key: String): Provider<String> =
    localProperties().map { properties -> properties.getProperty(key) }.orElse("")

fun Project.requireTargetAbi(): String {
    var targetAbi = ""
    if (gradle.startParameter.taskNames.isNotEmpty()) {
        if (gradle.startParameter.taskNames.size == 1) {
            val targetTask = gradle.startParameter.taskNames[0].lowercase(Locale.ROOT).trim()
            when {
                targetTask.contains("arm64") -> targetAbi = "arm64-v8a"
                targetTask.contains("arm") -> targetAbi = "armeabi-v7a"
                targetTask.contains("x64") -> targetAbi = "x86_64"
                targetTask.contains("x86") -> targetAbi = "x86"
            }
        }
    }
    return targetAbi
}

fun Project.setupCommon() {
    android.apply {
        buildToolsVersion = "37.0.0"
        compileSdk = 37
        defaultConfig.apply {
            minSdk = 24
        }
        buildTypes.getByName("release").apply {
            isMinifyEnabled = true
            vcsInfo.include = false
        }
        compileOptions.apply {
            sourceCompatibility = JavaVersion.VERSION_21
            targetCompatibility = JavaVersion.VERSION_21
        }
        lint.apply {
            showAll = true
            checkAllWarnings = true
            checkReleaseBuilds = false
            warningsAsErrors = true
        }
        packaging.apply {
            resources.excludes.addAll(
                listOf(
                    "**/*.kotlin_*",
                    "/META-INF/*.version",
                    "/META-INF/native/**",
                    "/META-INF/native-image/**",
                    "/META-INF/INDEX.LIST",
                    "DebugProbesKt.bin",
                    "com/**",
                    "org/**",
                    "**/*.java",
                    "**/*.proto",
                    "okhttp3/**",
                ),
            )
            jniLibs.useLegacyPackaging = true
        }
    }
    (android as? ApplicationExtension)?.apply {
        defaultConfig.apply {
            targetSdk = 36
        }
        buildTypes {
            getByName("release") {
                isShrinkResources = true
            }
            getByName("debug") {
                applicationIdSuffix = "debug"
                isDebuggable = true
                isJniDebuggable = true
            }
        }
    }
}

fun Project.setupKotlinCommon() {
    setupCommon()
}

fun Project.setupAppCommon() {
    setupKotlinCommon()

    val keystorePwd = requireLocalProperty("KEYSTORE_PASS").orNull?.ifBlank { null }
        ?: providers.environmentVariable("KEYSTORE_PASS").orNull
    val alias = requireLocalProperty("ALIAS_NAME").orNull?.ifBlank { null }
        ?: providers.environmentVariable("ALIAS_NAME").orNull
    val pwd = requireLocalProperty("ALIAS_PASS").orNull?.ifBlank { null }
        ?: providers.environmentVariable("ALIAS_PASS").orNull

    androidApp.apply {
        if (keystorePwd != null) {
            signingConfigs {
                create("release") {
                    storeFile = rootProject.file("release.keystore")
                    storePassword = keystorePwd
                    keyAlias = alias
                    keyPassword = pwd
                    enableV1Signing = true
                    enableV2Signing = true
                    enableV3Signing = true
                }
            }
        } else if (requireFlavor().contains("FossRelease")) {
            exitProcess(0)
        }
        buildTypes {
            val key = signingConfigs.findByName("release")
            if (key != null) {
                if (requireTargetAbi().isBlank()) {
                    getByName("release").signingConfig = key
                }
                getByName("debug").signingConfig = key
            }
        }
    }
}

fun Project.setupApp() {
    val pkgName = requireMetadata("PACKAGE_NAME").get()
    val verName = requireMetadata("VERSION_NAME").get()
    val verCode = requireMetadata("VERSION_CODE").get().toInt()
    androidApp.apply {
        defaultConfig {
            applicationId = pkgName
            versionCode = verCode
            versionName = verName
        }
    }
    setupAppCommon()

    val targetAbi = requireTargetAbi()

    androidApp.apply {
        buildTypes {
            getByName("release") {
                proguardFiles(
                    getDefaultProguardFile("proguard-android-optimize.txt"),
                    file("proguard-rules.pro"),
                )
            }
        }

        splits.abi {
            isEnable = true
            isUniversalApk = false
            if (targetAbi.isNotBlank()) {
                reset()
                include(targetAbi)
            }
        }

        flavorDimensions += "vendor"
        productFlavors {
            create("foss")
            create("play")
        }

        registerApkRenamer(
            replaceFrom = project.name,
            replaceToTemplate = "husi-%VERSION_NAME%",
            stripTokens = listOf("-release", "-foss"),
        )

        sourceSets.getByName("main").apply {
            jniLibs.directories.add(rootProject.file("composeApp/executableSo").toString())
        }
    }
}

fun Project.setupPlugin(projectName: String) {
    val propPrefix = projectName.uppercase(Locale.ROOT)
    val projName = projectName.lowercase(Locale.ROOT)
    val verName = requireMetadata("${propPrefix}_VERSION_NAME").get().trim()
    val verCode = requireMetadata("${propPrefix}_VERSION").get().trim().toInt()

    androidApp.apply {
        defaultConfig {
            versionName = verName
            versionCode = verCode
        }
    }

    setupAppCommon()

    val targetAbi = requireTargetAbi()

    androidApp.apply {
        buildTypes {
            getByName("release") {
                proguardFiles(
                    getDefaultProguardFile("proguard-android-optimize.txt"),
                    project(":plugin:api").file("proguard-rules.pro"),
                )
            }
        }

        splits.abi {
            isEnable = true
            isUniversalApk = false

            if (targetAbi.isNotBlank()) {
                reset()
                include(targetAbi)
            } else {
                reset()
                include("x86", "x86_64", "armeabi-v7a", "arm64-v8a")
            }
        }

        flavorDimensions.add("vendor")
        productFlavors {
            create("foss")
        }

        if (
            providers.environmentVariable("SKIP_BUILD").orNull != "on" &&
            providers.systemProperty("SKIP_BUILD_$propPrefix").orNull != "on"
        ) {
            if (targetAbi.isBlank()) {
                tasks.register<Exec>("externalBuild") {
                    executable(rootProject.file("run"))
                    args("plugin", projName)
                    workingDir(rootProject.projectDir)
                }

                tasks.configureEach {
                    if (name.startsWith("merge") && name.endsWith("JniLibFolders")) {
                        dependsOn("externalBuild")
                    }
                }
            } else {
                tasks.register<Exec>("externalBuildInit") {
                    executable(rootProject.file("run"))
                    args("plugin", projName, "init")
                    workingDir(rootProject.projectDir)
                }
                tasks.register<Exec>("externalBuild") {
                    executable(rootProject.file("run"))
                    args("plugin", projName, targetAbi)
                    workingDir(rootProject.projectDir)
                    dependsOn("externalBuildInit")
                }
                tasks.register<Exec>("externalBuildEnd") {
                    executable(rootProject.file("run"))
                    args("plugin", projName, "end")
                    workingDir(rootProject.projectDir)
                    dependsOn("externalBuild")
                }
                tasks.configureEach {
                    if (name.startsWith("merge") && name.endsWith("JniLibFolders")) {
                        dependsOn("externalBuildEnd")
                    }
                }
            }
        }

        registerApkRenamer(
            replaceFrom = project.name,
            replaceToTemplate = "${project.name}-plugin-%VERSION_NAME%",
            stripTokens = listOf("-release", "-foss"),
        )
    }

    dependencies.add("implementation", dependencies.project(":plugin:api"))

}

private fun Project.registerApkRenamer(
    replaceFrom: String,
    replaceToTemplate: String,
    stripTokens: List<String> = listOf("-release", "-foss"),
) {
    val androidComponents = extensions.getByType<ApplicationAndroidComponentsExtension>()

    androidComponents.onVariants { variant ->
        variant.outputs.forEach { output ->
            val versionName = output.versionName.orNull.orEmpty()
            val replaceTo = replaceToTemplate.replace("%VERSION_NAME%", versionName)
            val originalFileName = output.outputFileName.get()
            var newName = originalFileName.replace(replaceFrom, replaceTo)
            for (stripToken in stripTokens) {
                newName = newName.replace(stripToken, "")
            }
            if (newName != originalFileName) {
                output.outputFileName.set(newName)
            }
        }
    }
}

private fun writePlatformInfo(
    outputDir: File,
    packageName: String,
    fileName: String,
    platform: String,
) {
    val dir = outputDir.resolve(packageName.replace('.', '/'))
    dir.mkdirs()
    dir.resolve(fileName).writeText(
        """
        |package $packageName
        |
        |actual object PlatformInfo {
        |    actual val platform: Platform = Platform.$platform
        |    actual val isAndroid: Boolean
        |        get() = platform == Platform.Android
        |    actual val isLinux: Boolean
        |        get() = platform == Platform.Linux
        |    actual val isMacOs: Boolean
        |        get() = platform == Platform.MacOs
        |    actual val isWindows: Boolean
        |        get() = platform == Platform.Windows
        |}
        """.trimMargin(),
    )
}

abstract class GeneratePlatformInfoTask : DefaultTask() {
    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @get:Input
    abstract val packageName: Property<String>

    @get:Input
    abstract val fileName: Property<String>

    @get:Input
    abstract val platform: Property<String>

    @TaskAction
    fun generate() {
        writePlatformInfo(
            outputDir = outputDir.get().asFile,
            packageName = packageName.get(),
            fileName = fileName.get(),
            platform = platform.get(),
        )
    }
}
