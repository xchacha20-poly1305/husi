package fr.husi.compose.theme

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import fr.husi.compose.material3.ProvideTvMaterialBridge
import fr.husi.repository.resolveRepository
import androidx.tv.material3.ColorScheme as TvColorScheme
import androidx.tv.material3.MaterialTheme as TvMaterialTheme
import androidx.tv.material3.darkColorScheme as tvDarkColorScheme
import androidx.tv.material3.lightColorScheme as tvLightColorScheme

internal actual fun isDynamicThemeSupported(): Boolean {
    return Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !resolveRepository().isTv
}

@RequiresApi(Build.VERSION_CODES.S)
@Composable
internal actual fun rememberDynamicColorScheme(isDarkMode: Boolean): ColorScheme? {
    if (!isDynamicThemeSupported()) return null
    val context = LocalContext.current
    return remember(context, isDarkMode) {
        if (isDarkMode) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    }
}

internal object TvPlatformThemeApi : PlatformThemeApi {
    @Composable
    override fun ApplyTheme(
        colorScheme: ColorScheme,
        isDarkMode: Boolean,
        content: @Composable () -> Unit,
    ) {
        val tvColorScheme = remember(colorScheme, isDarkMode) {
            colorScheme.toTvColorScheme(isDarkMode)
        }
        MaterialTheme(
            colorScheme = colorScheme,
        ) {
            TvMaterialTheme(
                colorScheme = tvColorScheme,
                content = {
                    ProvideTvMaterialBridge(content)
                },
            )
        }
    }
}

private fun ColorScheme.toTvColorScheme(
    isDarkMode: Boolean,
): TvColorScheme {
    return if (isDarkMode) {
        tvDarkColorScheme(
            primary = primary,
            onPrimary = onPrimary,
            primaryContainer = primaryContainer,
            onPrimaryContainer = onPrimaryContainer,
            inversePrimary = inversePrimary,
            secondary = secondary,
            onSecondary = onSecondary,
            secondaryContainer = secondaryContainer,
            onSecondaryContainer = onSecondaryContainer,
            tertiary = tertiary,
            onTertiary = onTertiary,
            tertiaryContainer = tertiaryContainer,
            onTertiaryContainer = onTertiaryContainer,
            background = background,
            onBackground = onBackground,
            surface = surface,
            onSurface = onSurface,
            surfaceVariant = surfaceVariant,
            onSurfaceVariant = onSurfaceVariant,
            surfaceTint = surfaceTint,
            inverseSurface = inverseSurface,
            inverseOnSurface = inverseOnSurface,
            error = error,
            onError = onError,
            errorContainer = errorContainer,
            onErrorContainer = onErrorContainer,
            border = outline,
            borderVariant = outlineVariant,
            scrim = scrim,
        )
    } else {
        tvLightColorScheme(
            primary = primary,
            onPrimary = onPrimary,
            primaryContainer = primaryContainer,
            onPrimaryContainer = onPrimaryContainer,
            inversePrimary = inversePrimary,
            secondary = secondary,
            onSecondary = onSecondary,
            secondaryContainer = secondaryContainer,
            onSecondaryContainer = onSecondaryContainer,
            tertiary = tertiary,
            onTertiary = onTertiary,
            tertiaryContainer = tertiaryContainer,
            onTertiaryContainer = onTertiaryContainer,
            background = background,
            onBackground = onBackground,
            surface = surface,
            onSurface = onSurface,
            surfaceVariant = surfaceVariant,
            onSurfaceVariant = onSurfaceVariant,
            surfaceTint = surfaceTint,
            inverseSurface = inverseSurface,
            inverseOnSurface = inverseOnSurface,
            error = error,
            onError = onError,
            errorContainer = errorContainer,
            onErrorContainer = onErrorContainer,
            border = outline,
            borderVariant = outlineVariant,
            scrim = scrim,
        )
    }
}
