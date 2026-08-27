package fr.husi.compose.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import dev.nucleusframework.darkmodedetector.isSystemInDarkMode

internal actual fun isDynamicThemeSupported(): Boolean = false

@Composable
internal actual fun rememberDynamicColorScheme(isDarkMode: Boolean): ColorScheme? = null

// TODO replace with official once skiko version greater than v0.152.0-alpha.2
@Composable
actual fun rememberPlatformSystemDarkMode(): Boolean = isSystemInDarkMode()
