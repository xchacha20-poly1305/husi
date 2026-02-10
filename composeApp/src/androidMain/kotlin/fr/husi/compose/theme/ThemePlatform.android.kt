package fr.husi.compose.theme

import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

internal actual fun isDynamicThemeSupported(): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

@Composable
internal actual fun rememberDynamicColorScheme(isDarkMode: Boolean): ColorScheme? {
    if (!isDynamicThemeSupported()) return null
    val context = LocalContext.current
    return remember(context, isDarkMode) {
        if (isDarkMode) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    }
}
