import com.android.build.api.artifact.SingleArtifact
import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.CommonExtension
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.tasks.Exec
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

private lateinit var metadata: Properties
private lateinit var localProperties: Properties
private lateinit var flavor: String

fun Project.requireFlavor(): String {
    if (::flavor.isInitialized) return flavor
    if (gradle.startParameter.taskNames.isNotEmpty()) {
        val taskName = gradle.startParameter.taskNames[0]
        when {
            taskName.contains("assemble") -> {
                flavor = taskName.substringAfter("assemble")
                return flavor
            }

            taskName.contains("install") -> {
                flavor = taskName.substringAfter("install")
                return flavor
            }

            taskName.contains("bundle") -> {
                flavor = taskName.substringAfter("bundle")
                return flavor
            }
        }
    }

    flavor = ""
    return flavor
}

fun Project.requireMetadata(): Properties {
    if (!::metadata.isInitialized) {
        metadata = Properties().apply {
            load(rootProject.file("husi.properties").inputStream())
        }
    }
    return metadata
}

@Suppress("NewApi")
fun Project.requireLocalProperties(): Properties {
    if (!::localProperties.isInitialized) {
        localProperties = Properties()

        val base64 = System.getenv("LOCAL_PROPERTIES")
        if (!base64.isNullOrBlank()) {
            localProperties.load(Base64.getDecoder().decode(base64).inputStream())
        } else {
            val localPropertiesFile = project.rootProject.file("local.properties")
            if (localPropertiesFile.exists()) {
                localProperties.load(localPropertiesFile.inputStream())
            }
        }
    }
    return localProperties
}

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
        buildToolsVersion = "36.1.0"
        compileSdk = 36
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
            textOutput = project.file("build/lint.txt")
            htmlOutput = project.file("build/lint.html")
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

    val lp = requireLocalProperties()
    val keystorePwd = lp.getProperty("KEYSTORE_PASS") ?: System.getenv("KEYSTORE_PASS")
    val alias = lp.getProperty("ALIAS_NAME") ?: System.getenv("ALIAS_NAME")
    val pwd = lp.getProperty("ALIAS_PASS") ?: System.getenv("ALIAS_PASS")

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
    val pkgName = requireMetadata().getProperty("PACKAGE_NAME")
    val verName = requireMetadata().getProperty("VERSION_NAME")
    val verCode = requireMetadata().getProperty("VERSION_CODE").toInt()
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
            jniLibs.directories.add("executableSo")
        }
    }
}

fun Project.setupPlugin(projectName: String) {
    val propPrefix = projectName.uppercase(Locale.ROOT)
    val projName = projectName.lowercase(Locale.ROOT)
    val verName = requireMetadata().getProperty("${propPrefix}_VERSION_NAME").trim()
    val verCode = requireMetadata().getProperty("${propPrefix}_VERSION").trim().toInt()

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

        if (System.getenv("SKIP_BUILD") != "on" && System.getProperty("SKIP_BUILD_$propPrefix") != "on") {
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

    dependencies.add("implementation", project(":plugin:api"))

}

private fun String.cap(): String = replaceFirstChar {
    if (it.isLowerCase()) {
        it.titlecase(Locale.ROOT)
    } else {
        it.toString()
    }
}

private fun Project.registerApkRenamer(
    replaceFrom: String,
    replaceToTemplate: String,
    stripTokens: List<String> = listOf("-release", "-foss"),
) {
    val androidComponents = extensions.getByType<ApplicationAndroidComponentsExtension>()

    androidComponents.onVariants { variant ->
        val loader = variant.artifacts.getBuiltArtifactsLoader()
        val apkDir = variant.artifacts.get(SingleArtifact.APK)

        tasks.matching { it.name == "assemble${variant.name.cap()}" }.configureEach {
            doLast {
                val built = requireNotNull(loader.load(apkDir.get())) {
                    "Cannot load APK artifacts from ${apkDir.get()}"
                }

                for (artifact in built.elements) {
                    val srcFile = File(artifact.outputFile)
                    val versionName = artifact.versionName.orEmpty()
                    val replaceTo = replaceToTemplate.replace("%VERSION_NAME%", versionName)

                    var newName = srcFile.name.replace(replaceFrom, replaceTo)
                    for (stripToken in stripTokens) {
                        newName = newName.replace(stripToken, "")
                    }

                    val dstFile = File(srcFile.parentFile, newName)
                    if (srcFile != dstFile) {
                        srcFile.renameTo(dstFile)
                    }
                }
            }
        }
    }
}