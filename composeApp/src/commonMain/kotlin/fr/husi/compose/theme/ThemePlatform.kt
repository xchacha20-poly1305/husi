package fr.husi.compose.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable

internal expect fun isDynamicThemeSupported(): Boolean

val DEFAULT = if (isDynamicThemeSupported()) DYNAMIC else RED

@Composable
internal expect fun rememberDynamicColorScheme(isDarkMode: Boolean): ColorScheme?
