import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.compose.desktop.application.tasks.AbstractJPackageTask
import java.io.File

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.kotlin.multiplatform.library")
    id("kotlin-parcelize")
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.compose.multiplatform)
}

val metadata = requireMetadata()
val supportedDesktopTargets =
    setOf(
        "linux/amd64",
        "linux/arm64",
        "darwin/amd64",
        "darwin/arm64",
        "windows/amd64",
        "windows/arm64",
    )

fun normalizeDesktopPlatform(rawValue: String): String {
    val value = rawValue.trim().lowercase()
    return when (value) {
        "linux" -> "linux"
        "darwin", "macos", "mac", "osx" -> "darwin"
        "windows", "win" -> "windows"
        else -> error("Unsupported desktop platform '$rawValue'. Use linux, darwin, or windows.")
    }
}

fun normalizeDesktopArch(rawValue: String): String {
    val value = rawValue.trim().lowercase()
    return when (value) {
        "amd64", "x86_64" -> "amd64"
        "arm64", "aarch64" -> "arm64"
        else -> error("Unsupported desktop arch '$rawValue'. Use amd64 or arm64.")
    }
}

fun normalizeDesktopTarget(rawValue: String): String {
    val tokens = rawValue.trim().split("/", limit = 2)
    require(tokens.size == 2) {
        "Invalid desktopTarget '$rawValue'. Use <platform>/<arch>, e.g. linux/amd64."
    }
    return "${normalizeDesktopPlatform(tokens[0])}/${normalizeDesktopArch(tokens[1])}"
}

fun resolveHostDesktopTarget(): String {
    val platform = normalizeDesktopPlatform(System.getProperty("os.name"))
    val arch = normalizeDesktopArch(System.getProperty("os.arch"))
    return "$platform/$arch"
}

fun parseTargetFormat(rawValue: String): TargetFormat {
    val value = rawValue.trim().lowercase()
    return when (value) {
        "appimage", "app-image", "app_image", "app" -> TargetFormat.AppImage
        "deb" -> TargetFormat.Deb
        "rpm" -> TargetFormat.Rpm
        "dmg" -> TargetFormat.Dmg
        "pkg" -> TargetFormat.Pkg
        "msi" -> TargetFormat.Msi
        "exe" -> TargetFormat.Exe
        else ->
            error(
                "Unsupported desktop target format '$rawValue'. " +
                    "Use appimage, deb, rpm, dmg, pkg, msi, or exe.",
            )
    }
}

fun resolveLinuxTargetFormats(): Set<TargetFormat> {
    val requestedFormats = project.findProperty("desktopLinuxFormats")?.toString().orEmpty().trim()
    if (requestedFormats.isNotEmpty()) {
        return requestedFormats
            .split(",")
            .map { parseTargetFormat(it) }
            .filter { it == TargetFormat.AppImage || it == TargetFormat.Deb || it == TargetFormat.Rpm }
            .toCollection(linkedSetOf())
            .also {
                require(it.isNotEmpty()) {
                    "desktopLinuxFormats must include appimage, deb, or rpm."
                }
            }
    }

    val requestedDistro = project.findProperty("desktopLinuxDistro")?.toString().orEmpty().trim().lowercase()
    if (requestedDistro.isEmpty()) {
        return setOf(TargetFormat.AppImage)
    }

    return when (requestedDistro) {
        "debian", "ubuntu" -> linkedSetOf(TargetFormat.AppImage, TargetFormat.Deb)
        "fedora", "rhel", "opensuse", "rpm" -> linkedSetOf(TargetFormat.AppImage, TargetFormat.Rpm)
        "all", "both" -> linkedSetOf(TargetFormat.AppImage, TargetFormat.Deb, TargetFormat.Rpm)
        else ->
            error(
                "Unsupported desktopLinuxDistro '$requestedDistro'. " +
                    "Use debian, ubuntu, fedora, rhel, opensuse, rpm, all, or both.",
            )
    }
}

fun normalizeRpmPackageVersion(versionName: String): String = versionName.replace("-", ".")

fun resolveWindowsTargetFormats(): Set<TargetFormat> {
    val requestedFormats = project.findProperty("desktopWindowsFormats")?.toString().orEmpty().trim()
    if (requestedFormats.isEmpty()) {
        return linkedSetOf(TargetFormat.Exe, TargetFormat.Msi)
    }
    return requestedFormats
        .split(",")
        .map { parseTargetFormat(it) }
        .filter { it == TargetFormat.Exe || it == TargetFormat.Msi }
        .toCollection(linkedSetOf())
        .also {
            require(it.isNotEmpty()) {
                "desktopWindowsFormats must include exe or msi."
            }
        }
}

fun normalizeMacPackageVersion(versionName: String): String {
    val numbers =
        Regex("""\d+""")
            .findAll(versionName)
            .map { it.value }
            .take(3)
            .toList()
    val major = numbers.getOrElse(0) { "1" }
    val minor = numbers.getOrElse(1) { "0" }
    val patch = numbers.getOrElse(2) { "0" }
    return "$major.$minor.$patch"
}

fun normalizeWindowsPackageVersion(versionName: String, versionCode: Int): String {
    val numbers =
        Regex("""\d+""")
            .findAll(versionName).mapNotNull { it.value.toIntOrNull() }
            .toList()
    val major = numbers.getOrElse(0) { 0 }.coerceIn(0, 255)
    val minor = numbers.getOrElse(1) { 0 }.coerceIn(0, 255)
    val build = versionCode.coerceIn(0, 65535)
    return "$major.$minor.$build"
}

val requestedDesktopTarget = project.findProperty("desktopTarget")?.toString()?.trim().orEmpty()
val desktopTarget =
    if (requestedDesktopTarget.isNotEmpty()) {
        normalizeDesktopTarget(requestedDesktopTarget)
    } else {
        resolveHostDesktopTarget()
    }

require(desktopTarget in supportedDesktopTargets) {
    "Unsupported desktop target '$desktopTarget'. Supported targets: ${supportedDesktopTargets.joinToString()}."
}

val desktopJarName = "libcore-desktop-${desktopTarget.substringBefore("/")}-${desktopTarget.substringAfter("/")}.jar"
val desktopJarFile = layout.projectDirectory.file("libs/$desktopJarName").asFile
require(desktopJarFile.isFile) {
    "Missing desktop libcore jar '${desktopJarFile.path}'. Build it first, e.g. make libcore_desktop DESKTOP_TARGETS=$desktopTarget."
}
val libcoreDesktopJar = files(desktopJarFile)
val hostDesktopPlatform = normalizeDesktopPlatform(System.getProperty("os.name"))
val desktopPackageName = metadata.getProperty("PACKAGE_NAME").trim()
val desktopVersion = metadata.getProperty("VERSION_NAME").trim()
val desktopVersionCode = metadata.getProperty("VERSION_CODE").trim().toInt()
val macPackageVersion = normalizeMacPackageVersion(desktopVersion)
val windowsPackageVersion = normalizeWindowsPackageVersion(desktopVersion, desktopVersionCode)
val desktopTargetFormats =
    when (hostDesktopPlatform) {
        "linux" -> resolveLinuxTargetFormats()
        "darwin" -> setOf(TargetFormat.Dmg)
        "windows" -> resolveWindowsTargetFormats()
        else -> error("Unsupported host desktop platform '$hostDesktopPlatform'.")
    }
val linuxWrappedLauncherSuffix = "-launcher-bin"
val linuxWrappedLauncherPlaceholder = "__HUSI_WRAPPED_LAUNCHER__"
val linuxJavaOptionsTemplateTarget = "desktop-java-opts.conf.template"
val linuxAppArgsTemplateTarget = "desktop-app-args.conf.template"

val linuxDesktopReleaseDir = rootProject.layout.projectDirectory.dir("release/linux/desktop")
val linuxLauncherTemplateFile = linuxDesktopReleaseDir.file("launcher.sh").asFile
val linuxJavaOptionsTemplateFile = linuxDesktopReleaseDir.file("desktop-java-opts.conf").asFile
val linuxAppArgsTemplateFile = linuxDesktopReleaseDir.file("desktop-app-args.conf").asFile

fun patchLinuxDesktopLauncher(
    appImageRoot: File,
    launcherName: String,
    launcherTemplateFile: File,
    javaOptionsTemplateFile: File,
    appArgsTemplateFile: File,
) {
    require(launcherTemplateFile.isFile) {
        "Missing release launcher template '${launcherTemplateFile.path}'."
    }
    require(javaOptionsTemplateFile.isFile) {
        "Missing release JVM options template '${javaOptionsTemplateFile.path}'."
    }
    require(appArgsTemplateFile.isFile) {
        "Missing release app args template '${appArgsTemplateFile.path}'."
    }

    val launcherDir = appImageRoot.resolve("bin")
    val launcherScript = launcherDir.resolve(launcherName)
    require(launcherScript.isFile) {
        "Launcher '${launcherScript.path}' not found in Linux app image."
    }

    val wrappedLauncherName = "$launcherName$linuxWrappedLauncherSuffix"
    val wrappedLauncher = launcherDir.resolve(wrappedLauncherName)
    if (!wrappedLauncher.exists()) {
        launcherScript.copyTo(wrappedLauncher, overwrite = true)
        launcherScript.delete()
    }

    val launcherCfgDir = appImageRoot.resolve("lib/app")
    val launcherCfg = launcherCfgDir.resolve("$launcherName.cfg")
    if (launcherCfg.isFile) {
        launcherCfg.copyTo(launcherCfgDir.resolve("$wrappedLauncherName.cfg"), overwrite = true)
    }

    val launcherTemplate = launcherTemplateFile.readText()
    require(launcherTemplate.contains(linuxWrappedLauncherPlaceholder)) {
        "Launcher template '${launcherTemplateFile.path}' must contain $linuxWrappedLauncherPlaceholder."
    }
    launcherScript.writeText(
        launcherTemplate.replace(linuxWrappedLauncherPlaceholder, wrappedLauncherName),
    )
    launcherScript.setExecutable(true, false)

    javaOptionsTemplateFile.copyTo(launcherDir.resolve(linuxJavaOptionsTemplateTarget), overwrite = true)
    appArgsTemplateFile.copyTo(launcherDir.resolve(linuxAppArgsTemplateTarget), overwrite = true)
}

val generateBuildConfig by tasks.registering {
    val outputDir = layout.buildDirectory.dir("generated/buildConfig/kotlin")
    val versionName = metadata.getProperty("VERSION_NAME")
    val versionCode = metadata.getProperty("VERSION_CODE")
    outputs.dir(outputDir)
    doLast {
        val dir = outputDir.get().asFile.resolve("fr/husi")
        dir.mkdirs()
        dir.resolve("BuildConfig.kt").writeText(
            """
            |package fr.husi
            |
            |object BuildConfig {
            |    const val VERSION_NAME = "$versionName"
            |    const val VERSION_CODE = $versionCode
            |    const val FLAVOR = ""
            |    val DEBUG = System.getProperty("husi.debug")?.toBoolean() ?: false
            |}
            """.trimMargin(),
        )
    }
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
        optIn.add("androidx.compose.material3.ExperimentalMaterial3Api")
        optIn.add("androidx.compose.material3.ExperimentalMaterial3ExpressiveApi")
    }

    androidLibrary {
        namespace = "fr.husi.lib"
        compileSdk = 36
        minSdk = 24
        androidResources {
            enable = true
        }
    }

    jvm("desktop")

    sourceSets {
        val commonMain by getting {
            kotlin.srcDir(generateBuildConfig)
            dependencies {
                // This is workaround for IDE to get libcore info
                compileOnly(libcoreDesktopJar)

                implementation(libs.jetbrains.compose.runtime)
                implementation(libs.jetbrains.compose.foundation)
                implementation(libs.jetbrains.compose.material3)
                implementation(libs.jetbrains.compose.animation.graphics)
                implementation(libs.jetbrains.compose.components.resources)
                implementation(libs.jetbrains.compose.ui.tooling.preview)
                implementation(libs.jetbrains.lifecycle.viewmodel.compose)
                implementation(libs.jetbrains.lifecycle.runtime.compose)
                implementation(libs.jetbrains.navigation.compose)
                implementation(libs.kotlinx.collections.immutable)
                implementation(libs.androidx.datastore.preferences)
                implementation(libs.androidx.room.runtime)
                implementation(libs.androidx.sqlite.bundled)
                implementation(libs.kotlinx.serialization.json)

                implementation(libs.ini4j)
                implementation(libs.kryo)
                implementation(libs.compose.preference)
                implementation(libs.fastscroller.core)
                implementation(libs.fastscroller.material3)
                implementation(libs.filekit.core)
                implementation(libs.filekit.dialogs.compose)
                implementation(libs.zxing.core)
                implementation(project(":library:compose-code-editor:codeeditor"))
                implementation(project(":library:DragDropSwipeLazyColumn"))
            }
        }
        val androidMain by getting {
            dependencies {
                implementation(
                    fileTree("libs") {
                        include("*.aar")
                    },
                )

                implementation(libs.kotlinx.coroutines.android)

                implementation(libs.androidx.core.ktx)
                implementation(libs.androidx.appcompat)
                implementation(libs.androidx.activity.ktx)
                implementation(libs.androidx.browser)


                implementation(libs.androidx.work.runtime.ktx)
                implementation(libs.androidx.work.multiprocess)

                implementation(libs.androidx.datastore)

                implementation(libs.androidx.compose.ui.viewbinding)
                implementation(libs.androidx.activity.compose)

                implementation(libs.accompanist.drawablepainter)

                implementation(libs.androidx.camera.core)
                implementation(libs.androidx.camera.lifecycle)
                implementation(libs.androidx.camera.camera2)
                implementation(libs.androidx.camera.compose)

                implementation(libs.smali.dexlib2.get().toString()) {
                    exclude(group = "com.google.guava", module = "guava")
                }
                implementation(libs.guava)

                implementation(libs.process.phoenix)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(project.dependencies.platform(libs.junit.bom))
                implementation(libs.junit.jupiter.api)
                runtimeOnly(libs.junit.platform.launcher)
                runtimeOnly(libs.junit.jupiter.engine)
            }
        }
        val desktopMain by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation(libs.kotlinx.coroutines.swing)
                implementation(libs.kotlinx.cli)
                implementation(libcoreDesktopJar)
            }
        }
    }
}

compose.desktop {
    application {
        mainClass = "fr.husi.DesktopMainKt"
        nativeDistributions {
            targetFormats(*desktopTargetFormats.toTypedArray())
            packageName = desktopPackageName
            packageVersion = macPackageVersion
            description = "Husi desktop proxy integration tool"
            vendor = "Husi contributors"
            copyright = "GPL-3.0-or-later"
            licenseFile.set(rootProject.layout.projectDirectory.file("LICENSE"))
            linux {
                shortcut = true
                packageName = desktopPackageName
                appCategory = "Network"
                menuGroup = "Network"
                debMaintainer = "安容"
                rpmLicenseType = "GPL-3.0-or-later"
                debPackageVersion = desktopVersion
                rpmPackageVersion = normalizeRpmPackageVersion(desktopVersion)
            }
            macOS {
                dmgPackageVersion = macPackageVersion
            }
            windows {
                exePackageVersion = windowsPackageVersion
                msiPackageVersion = windowsPackageVersion
            }
        }
    }
}

compose.resources {
    packageOfResClass = "fr.husi.resources"
}

ksp {
    arg("room.incremental", "true")
    arg("room.schemaLocation", "${projectDir}/schemas")
}

dependencies {
    kspAndroid(libs.androidx.room.compiler)
    add("kspDesktop", libs.androidx.room.compiler)
}

tasks.withType<AbstractJPackageTask>().configureEach {
    if (targetFormat != TargetFormat.AppImage) {
        return@configureEach
    }

    doLast {
        if (hostDesktopPlatform != "linux") {
            return@doLast
        }

        val appImageOutputDir = destinationDir.get().asFile
        val appImageRoots =
            appImageOutputDir
                .walkTopDown()
                .filter { it.isFile && it.name == desktopPackageName && it.parentFile.name == "bin" }
                .map { it.parentFile.parentFile }
                .distinctBy { it.absolutePath }
                .toList()

        require(appImageRoots.isNotEmpty()) {
            "No Linux app image launcher '$desktopPackageName' found under '${appImageOutputDir.path}'."
        }

        appImageRoots.forEach { appImageRoot ->
            patchLinuxDesktopLauncher(
                appImageRoot = appImageRoot,
                launcherName = desktopPackageName,
                launcherTemplateFile = linuxLauncherTemplateFile,
                javaOptionsTemplateFile = linuxJavaOptionsTemplateFile,
                appArgsTemplateFile = linuxAppArgsTemplateFile,
            )
        }
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
