package fr.husi.compose.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import dev.nucleusframework.darkmodedetector.isSystemInDarkMode

internal actual fun isDynamicThemeSupported(): Boolean = false

@Composable
internal actual fun rememberDynamicColorScheme(isDarkMode: Boolean): ColorScheme? = null

@Composable
actual fun rememberPlatformSystemDarkMode(): Boolean = isSystemInDarkMode()
