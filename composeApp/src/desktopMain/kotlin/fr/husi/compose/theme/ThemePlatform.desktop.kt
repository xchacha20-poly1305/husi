package fr.husi.compose.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.LocalSystemTheme
import androidx.compose.ui.SystemTheme
import fr.husi.ktx.blankAsNull
import fr.husi.repository.repo
import kotlinx.coroutines.delay
import java.util.concurrent.TimeUnit

internal actual fun isDynamicThemeSupported(): Boolean = false

@Composable
internal actual fun rememberDynamicColorScheme(isDarkMode: Boolean): ColorScheme? = null


@OptIn(InternalComposeUiApi::class)
@Composable
internal actual fun rememberPlatformSystemDarkMode(): Boolean {
    val systemTheme = LocalSystemTheme.current
    if (systemTheme == SystemTheme.Dark) return true
    if (systemTheme == SystemTheme.Light) return false
    if (!repo.isLinux) return false

    var fallbackDarkMode by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val (result, probe) = resolveDesktopDarkModeProbe()
        fallbackDarkMode = result
        if (probe == null) return@LaunchedEffect

        while (true) {
            delay(10_000)
            fallbackDarkMode = probe()
        }
    }

    return fallbackDarkMode
}

private fun resolveDesktopDarkModeProbe(): Pair<Boolean, (() -> Boolean)?> {
    fun probeEnvGTKTheme(): Boolean? {
        return System.getenv("GTK_THEME")?.blankAsNull()?.contains("dark", ignoreCase = true)
    }
    probeEnvGTKTheme()?.let { return it to { probeEnvGTKTheme() ?: false } }

    fun probePortal(): Boolean? {
        val output = queryCommand(
            "gdbus", "call", "--session",
            "--dest", "org.freedesktop.portal.Desktop",
            "--object-path", "/org/freedesktop/portal/desktop",
            "--method", "org.freedesktop.portal.Settings.Read",
            "org.freedesktop.appearance", "color-scheme",
        ) ?: return null
        return when (Regex("""\b([01])\b""").find(output)?.groupValues?.get(1)?.toIntOrNull()) {
            1 -> true
            0 -> false
            else -> null
        }
    }
    probePortal()?.let { return it to { probePortal() ?: false } }

    fun probeColorScheme(): Boolean? {
        val out = queryCommand("gsettings", "get", "org.gnome.desktop.interface", "color-scheme")
            ?: return null
        return when {
            out.contains("prefer-dark", ignoreCase = true) -> true
            out.contains("default", ignoreCase = true) || out.contains(
                "prefer-light",
                ignoreCase = true,
            ) -> false

            else -> null
        }
    }
    probeColorScheme()?.let { return it to { probeColorScheme() ?: false } }

    fun probeGtkTheme(): Boolean? {
        val out = queryCommand("gsettings", "get", "org.gnome.desktop.interface", "gtk-theme")
            ?: return null
        return if (out.contains("dark", ignoreCase = true)) true else null
    }
    probeGtkTheme()?.let { return it to { probeGtkTheme() ?: false } }

    fun probeKreadconfig(cmd: String): Boolean? =
        queryCommand(cmd, "--group", "General", "--key", "ColorScheme")
            ?.takeIf { it.contains("dark", ignoreCase = true) }
            ?.let { true }

    probeKreadconfig("kreadconfig6")?.let {
        return it to {
            probeKreadconfig("kreadconfig6") ?: false
        }
    }
    probeKreadconfig("kreadconfig5")?.let {
        return it to {
            probeKreadconfig("kreadconfig5") ?: false
        }
    }

    return false to null
}

private fun queryCommand(vararg command: String): String? {
    return runCatching {
        val process = ProcessBuilder(*command)
            .redirectErrorStream(true)
            .start()
        if (!process.waitFor(800, TimeUnit.MILLISECONDS)) {
            process.destroyForcibly()
            return null
        }
        if (process.exitValue() != 0) return null
        process.inputStream.bufferedReader().use { it.readText().trim() }.blankAsNull()
    }.getOrNull()
}