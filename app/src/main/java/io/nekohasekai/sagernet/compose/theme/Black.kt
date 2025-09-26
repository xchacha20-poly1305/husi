package io.nekohasekai.sagernet.compose.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** 飞鸿踏雪 */
object Black {
    val primaryLight = Color(0xFF5D5F5F)
    val onPrimaryLight = Color(0xFFFFFFFF)
    val primaryContainerLight = Color(0xFFFFFFFF)
    val onPrimaryContainerLight = Color(0xFF747676)
    val secondaryLight = Color(0xFF5D5F5F)
    val onSecondaryLight = Color(0xFFFFFFFF)
    val secondaryContainerLight = Color(0xFFFAFAFA)
    val onSecondaryContainerLight = Color(0xFF717373)
    val tertiaryLight = Color(0xFF5D5E5F)
    val onTertiaryLight = Color(0xFFFFFFFF)
    val tertiaryContainerLight = Color(0xFFBDBDBD)
    val onTertiaryContainerLight = Color(0xFF4B4C4D)
    val errorLight = Color(0xFFBA1A1A)
    val onErrorLight = Color(0xFFFFFFFF)
    val errorContainerLight = Color(0xFFFFDAD6)
    val onErrorContainerLight = Color(0xFF93000A)
    val backgroundLight = Color(0xFFFCF8F8)
    val onBackgroundLight = Color(0xFF1C1B1B)
    val surfaceLight = Color(0xFFFCF8F8)
    val onSurfaceLight = Color(0xFF1C1B1B)
    val surfaceVariantLight = Color(0xFFE0E3E3)
    val onSurfaceVariantLight = Color(0xFF444748)
    val outlineLight = Color(0xFF747878)
    val outlineVariantLight = Color(0xFFC4C7C8)
    val scrimLight = Color(0xFF000000)
    val inverseSurfaceLight = Color(0xFF313030)
    val inverseOnSurfaceLight = Color(0xFFF4F0EF)
    val inversePrimaryLight = Color(0xFFC6C6C7)
    val surfaceDimLight = Color(0xFFDDD9D9)
    val surfaceBrightLight = Color(0xFFFCF8F8)
    val surfaceContainerLowestLight = Color(0xFFFFFFFF)
    val surfaceContainerLowLight = Color(0xFFF6F3F2)
    val surfaceContainerLight = Color(0xFFF1EDEC)
    val surfaceContainerHighLight = Color(0xFFEBE7E7)
    val surfaceContainerHighestLight = Color(0xFFE5E2E1)

    val primaryLightMediumContrast = Color(0xFF343637)
    val onPrimaryLightMediumContrast = Color(0xFFFFFFFF)
    val primaryContainerLightMediumContrast = Color(0xFF6C6D6D)
    val onPrimaryContainerLightMediumContrast = Color(0xFFFFFFFF)
    val secondaryLightMediumContrast = Color(0xFF343637)
    val onSecondaryLightMediumContrast = Color(0xFFFFFFFF)
    val secondaryContainerLightMediumContrast = Color(0xFF6C6D6D)
    val onSecondaryContainerLightMediumContrast = Color(0xFFFFFFFF)
    val tertiaryLightMediumContrast = Color(0xFF353637)
    val onTertiaryLightMediumContrast = Color(0xFFFFFFFF)
    val tertiaryContainerLightMediumContrast = Color(0xFF6C6D6D)
    val onTertiaryContainerLightMediumContrast = Color(0xFFFFFFFF)
    val errorLightMediumContrast = Color(0xFF740006)
    val onErrorLightMediumContrast = Color(0xFFFFFFFF)
    val errorContainerLightMediumContrast = Color(0xFFCF2C27)
    val onErrorContainerLightMediumContrast = Color(0xFFFFFFFF)
    val backgroundLightMediumContrast = Color(0xFFFCF8F8)
    val onBackgroundLightMediumContrast = Color(0xFF1C1B1B)
    val surfaceLightMediumContrast = Color(0xFFFCF8F8)
    val onSurfaceLightMediumContrast = Color(0xFF111111)
    val surfaceVariantLightMediumContrast = Color(0xFFE0E3E3)
    val onSurfaceVariantLightMediumContrast = Color(0xFF333738)
    val outlineLightMediumContrast = Color(0xFF4F5354)
    val outlineVariantLightMediumContrast = Color(0xFF6A6E6E)
    val scrimLightMediumContrast = Color(0xFF000000)
    val inverseSurfaceLightMediumContrast = Color(0xFF313030)
    val inverseOnSurfaceLightMediumContrast = Color(0xFFF4F0EF)
    val inversePrimaryLightMediumContrast = Color(0xFFC6C6C7)
    val surfaceDimLightMediumContrast = Color(0xFFC9C6C5)
    val surfaceBrightLightMediumContrast = Color(0xFFFCF8F8)
    val surfaceContainerLowestLightMediumContrast = Color(0xFFFFFFFF)
    val surfaceContainerLowLightMediumContrast = Color(0xFFF6F3F2)
    val surfaceContainerLightMediumContrast = Color(0xFFEBE7E7)
    val surfaceContainerHighLightMediumContrast = Color(0xFFDFDCDB)
    val surfaceContainerHighestLightMediumContrast = Color(0xFFD4D1D0)

    val primaryLightHighContrast = Color(0xFF2A2C2D)
    val onPrimaryLightHighContrast = Color(0xFFFFFFFF)
    val primaryContainerLightHighContrast = Color(0xFF48494A)
    val onPrimaryContainerLightHighContrast = Color(0xFFFFFFFF)
    val secondaryLightHighContrast = Color(0xFF2A2C2D)
    val onSecondaryLightHighContrast = Color(0xFFFFFFFF)
    val secondaryContainerLightHighContrast = Color(0xFF48494A)
    val onSecondaryContainerLightHighContrast = Color(0xFFFFFFFF)
    val tertiaryLightHighContrast = Color(0xFF2B2C2D)
    val onTertiaryLightHighContrast = Color(0xFFFFFFFF)
    val tertiaryContainerLightHighContrast = Color(0xFF48494A)
    val onTertiaryContainerLightHighContrast = Color(0xFFFFFFFF)
    val errorLightHighContrast = Color(0xFF600004)
    val onErrorLightHighContrast = Color(0xFFFFFFFF)
    val errorContainerLightHighContrast = Color(0xFF98000A)
    val onErrorContainerLightHighContrast = Color(0xFFFFFFFF)
    val backgroundLightHighContrast = Color(0xFFFCF8F8)
    val onBackgroundLightHighContrast = Color(0xFF1C1B1B)
    val surfaceLightHighContrast = Color(0xFFFCF8F8)
    val onSurfaceLightHighContrast = Color(0xFF000000)
    val surfaceVariantLightHighContrast = Color(0xFFE0E3E3)
    val onSurfaceVariantLightHighContrast = Color(0xFF000000)
    val outlineLightHighContrast = Color(0xFF292D2D)
    val outlineVariantLightHighContrast = Color(0xFF464A4A)
    val scrimLightHighContrast = Color(0xFF000000)
    val inverseSurfaceLightHighContrast = Color(0xFF313030)
    val inverseOnSurfaceLightHighContrast = Color(0xFFFFFFFF)
    val inversePrimaryLightHighContrast = Color(0xFFC6C6C7)
    val surfaceDimLightHighContrast = Color(0xFFBBB8B7)
    val surfaceBrightLightHighContrast = Color(0xFFFCF8F8)
    val surfaceContainerLowestLightHighContrast = Color(0xFFFFFFFF)
    val surfaceContainerLowLightHighContrast = Color(0xFFF4F0EF)
    val surfaceContainerLightHighContrast = Color(0xFFE5E2E1)
    val surfaceContainerHighLightHighContrast = Color(0xFFD7D4D3)
    val surfaceContainerHighestLightHighContrast = Color(0xFFC9C6C5)

    val primaryDark = Color(0xFFFFFFFF)
    val onPrimaryDark = Color(0xFF2F3131)
    val primaryContainerDark = Color(0xFFE2E2E2)
    val onPrimaryContainerDark = Color(0xFF636565)
    val secondaryDark = Color(0xFFFFFFFF)
    val onSecondaryDark = Color(0xFF2F3131)
    val secondaryContainerDark = Color(0xFFE2E2E2)
    val onSecondaryContainerDark = Color(0xFF636565)
    val tertiaryDark = Color(0xFFD9D9D9)
    val onTertiaryDark = Color(0xFF2F3131)
    val tertiaryContainerDark = Color(0xFFBDBDBD)
    val onTertiaryContainerDark = Color(0xFF4B4C4D)
    val errorDark = Color(0xFFFFB4AB)
    val onErrorDark = Color(0xFF690005)
    val errorContainerDark = Color(0xFF93000A)
    val onErrorContainerDark = Color(0xFFFFDAD6)
    val backgroundDark = Color(0xFF141313)
    val onBackgroundDark = Color(0xFFE5E2E1)
    val surfaceDark = Color(0xFF141313)
    val onSurfaceDark = Color(0xFFE5E2E1)
    val surfaceVariantDark = Color(0xFF444748)
    val onSurfaceVariantDark = Color(0xFFC4C7C8)
    val outlineDark = Color(0xFF8E9192)
    val outlineVariantDark = Color(0xFF444748)
    val scrimDark = Color(0xFF000000)
    val inverseSurfaceDark = Color(0xFFE5E2E1)
    val inverseOnSurfaceDark = Color(0xFF313030)
    val inversePrimaryDark = Color(0xFF5D5F5F)
    val surfaceDimDark = Color(0xFF141313)
    val surfaceBrightDark = Color(0xFF3A3939)
    val surfaceContainerLowestDark = Color(0xFF0E0E0E)
    val surfaceContainerLowDark = Color(0xFF1C1B1B)
    val surfaceContainerDark = Color(0xFF201F1F)
    val surfaceContainerHighDark = Color(0xFF2A2A2A)
    val surfaceContainerHighestDark = Color(0xFF353434)

    val primaryDarkMediumContrast = Color(0xFFFFFFFF)
    val onPrimaryDarkMediumContrast = Color(0xFF2F3131)
    val primaryContainerDarkMediumContrast = Color(0xFFE2E2E2)
    val onPrimaryContainerDarkMediumContrast = Color(0xFF464849)
    val secondaryDarkMediumContrast = Color(0xFFFFFFFF)
    val onSecondaryDarkMediumContrast = Color(0xFF2F3131)
    val secondaryContainerDarkMediumContrast = Color(0xFFE2E2E2)
    val onSecondaryContainerDarkMediumContrast = Color(0xFF464849)
    val tertiaryDarkMediumContrast = Color(0xFFDCDCDC)
    val onTertiaryDarkMediumContrast = Color(0xFF242626)
    val tertiaryContainerDarkMediumContrast = Color(0xFFBDBDBD)
    val onTertiaryContainerDarkMediumContrast = Color(0xFF2E3030)
    val errorDarkMediumContrast = Color(0xFFFFD2CC)
    val onErrorDarkMediumContrast = Color(0xFF540003)
    val errorContainerDarkMediumContrast = Color(0xFFFF5449)
    val onErrorContainerDarkMediumContrast = Color(0xFF000000)
    val backgroundDarkMediumContrast = Color(0xFF141313)
    val onBackgroundDarkMediumContrast = Color(0xFFE5E2E1)
    val surfaceDarkMediumContrast = Color(0xFF141313)
    val onSurfaceDarkMediumContrast = Color(0xFFFFFFFF)
    val surfaceVariantDarkMediumContrast = Color(0xFF444748)
    val onSurfaceVariantDarkMediumContrast = Color(0xFFDADDDD)
    val outlineDarkMediumContrast = Color(0xFFAFB2B3)
    val outlineVariantDarkMediumContrast = Color(0xFF8D9191)
    val scrimDarkMediumContrast = Color(0xFF000000)
    val inverseSurfaceDarkMediumContrast = Color(0xFFE5E2E1)
    val inverseOnSurfaceDarkMediumContrast = Color(0xFF2A2A2A)
    val inversePrimaryDarkMediumContrast = Color(0xFF464849)
    val surfaceDimDarkMediumContrast = Color(0xFF141313)
    val surfaceBrightDarkMediumContrast = Color(0xFF454444)
    val surfaceContainerLowestDarkMediumContrast = Color(0xFF070707)
    val surfaceContainerLowDarkMediumContrast = Color(0xFF1E1D1D)
    val surfaceContainerDarkMediumContrast = Color(0xFF282828)
    val surfaceContainerHighDarkMediumContrast = Color(0xFF333232)
    val surfaceContainerHighestDarkMediumContrast = Color(0xFF3E3D3D)

    val primaryDarkHighContrast = Color(0xFFFFFFFF)
    val onPrimaryDarkHighContrast = Color(0xFF000000)
    val primaryContainerDarkHighContrast = Color(0xFFE2E2E2)
    val onPrimaryContainerDarkHighContrast = Color(0xFF282A2B)
    val secondaryDarkHighContrast = Color(0xFFFFFFFF)
    val onSecondaryDarkHighContrast = Color(0xFF000000)
    val secondaryContainerDarkHighContrast = Color(0xFFE2E2E2)
    val onSecondaryContainerDarkHighContrast = Color(0xFF282A2B)
    val tertiaryDarkHighContrast = Color(0xFFF0F0F0)
    val onTertiaryDarkHighContrast = Color(0xFF000000)
    val tertiaryContainerDarkHighContrast = Color(0xFFC2C2C2)
    val onTertiaryContainerDarkHighContrast = Color(0xFF0A0B0C)
    val errorDarkHighContrast = Color(0xFFFFECE9)
    val onErrorDarkHighContrast = Color(0xFF000000)
    val errorContainerDarkHighContrast = Color(0xFFFFAEA4)
    val onErrorContainerDarkHighContrast = Color(0xFF220001)
    val backgroundDarkHighContrast = Color(0xFF141313)
    val onBackgroundDarkHighContrast = Color(0xFFE5E2E1)
    val surfaceDarkHighContrast = Color(0xFF141313)
    val onSurfaceDarkHighContrast = Color(0xFFFFFFFF)
    val surfaceVariantDarkHighContrast = Color(0xFF444748)
    val onSurfaceVariantDarkHighContrast = Color(0xFFFFFFFF)
    val outlineDarkHighContrast = Color(0xFFEEF0F1)
    val outlineVariantDarkHighContrast = Color(0xFFC0C3C4)
    val scrimDarkHighContrast = Color(0xFF000000)
    val inverseSurfaceDarkHighContrast = Color(0xFFE5E2E1)
    val inverseOnSurfaceDarkHighContrast = Color(0xFF000000)
    val inversePrimaryDarkHighContrast = Color(0xFF464849)
    val surfaceDimDarkHighContrast = Color(0xFF141313)
    val surfaceBrightDarkHighContrast = Color(0xFF51504F)
    val surfaceContainerLowestDarkHighContrast = Color(0xFF000000)
    val surfaceContainerLowDarkHighContrast = Color(0xFF201F1F)
    val surfaceContainerDarkHighContrast = Color(0xFF313030)
    val surfaceContainerHighDarkHighContrast = Color(0xFF3C3B3B)
    val surfaceContainerHighestDarkHighContrast = Color(0xFF474646)

    private val lightScheme = lightColorScheme(
        primary = primaryLight,
        onPrimary = onPrimaryLight,
        primaryContainer = primaryContainerLight,
        onPrimaryContainer = onPrimaryContainerLight,
        secondary = secondaryLight,
        onSecondary = onSecondaryLight,
        secondaryContainer = secondaryContainerLight,
        onSecondaryContainer = onSecondaryContainerLight,
        tertiary = tertiaryLight,
        onTertiary = onTertiaryLight,
        tertiaryContainer = tertiaryContainerLight,
        onTertiaryContainer = onTertiaryContainerLight,
        error = errorLight,
        onError = onErrorLight,
        errorContainer = errorContainerLight,
        onErrorContainer = onErrorContainerLight,
        background = backgroundLight,
        onBackground = onBackgroundLight,
        surface = surfaceLight,
        onSurface = onSurfaceLight,
        surfaceVariant = surfaceVariantLight,
        onSurfaceVariant = onSurfaceVariantLight,
        outline = outlineLight,
        outlineVariant = outlineVariantLight,
        scrim = scrimLight,
        inverseSurface = inverseSurfaceLight,
        inverseOnSurface = inverseOnSurfaceLight,
        inversePrimary = inversePrimaryLight,
        surfaceDim = surfaceDimLight,
        surfaceBright = surfaceBrightLight,
        surfaceContainerLowest = surfaceContainerLowestLight,
        surfaceContainerLow = surfaceContainerLowLight,
        surfaceContainer = surfaceContainerLight,
        surfaceContainerHigh = surfaceContainerHighLight,
        surfaceContainerHighest = surfaceContainerHighestLight,
    )

    private val darkScheme = darkColorScheme(
        primary = primaryDark,
        onPrimary = onPrimaryDark,
        primaryContainer = primaryContainerDark,
        onPrimaryContainer = onPrimaryContainerDark,
        secondary = secondaryDark,
        onSecondary = onSecondaryDark,
        secondaryContainer = secondaryContainerDark,
        onSecondaryContainer = onSecondaryContainerDark,
        tertiary = tertiaryDark,
        onTertiary = onTertiaryDark,
        tertiaryContainer = tertiaryContainerDark,
        onTertiaryContainer = onTertiaryContainerDark,
        error = errorDark,
        onError = onErrorDark,
        errorContainer = errorContainerDark,
        onErrorContainer = onErrorContainerDark,
        background = backgroundDark,
        onBackground = onBackgroundDark,
        surface = surfaceDark,
        onSurface = onSurfaceDark,
        surfaceVariant = surfaceVariantDark,
        onSurfaceVariant = onSurfaceVariantDark,
        outline = outlineDark,
        outlineVariant = outlineVariantDark,
        scrim = scrimDark,
        inverseSurface = inverseSurfaceDark,
        inverseOnSurface = inverseOnSurfaceDark,
        inversePrimary = inversePrimaryDark,
        surfaceDim = surfaceDimDark,
        surfaceBright = surfaceBrightDark,
        surfaceContainerLowest = surfaceContainerLowestDark,
        surfaceContainerLow = surfaceContainerLowDark,
        surfaceContainer = surfaceContainerDark,
        surfaceContainerHigh = surfaceContainerHighDark,
        surfaceContainerHighest = surfaceContainerHighestDark,
    )

    private val mediumContrastLightColorScheme = lightColorScheme(
        primary = primaryLightMediumContrast,
        onPrimary = onPrimaryLightMediumContrast,
        primaryContainer = primaryContainerLightMediumContrast,
        onPrimaryContainer = onPrimaryContainerLightMediumContrast,
        secondary = secondaryLightMediumContrast,
        onSecondary = onSecondaryLightMediumContrast,
        secondaryContainer = secondaryContainerLightMediumContrast,
        onSecondaryContainer = onSecondaryContainerLightMediumContrast,
        tertiary = tertiaryLightMediumContrast,
        onTertiary = onTertiaryLightMediumContrast,
        tertiaryContainer = tertiaryContainerLightMediumContrast,
        onTertiaryContainer = onTertiaryContainerLightMediumContrast,
        error = errorLightMediumContrast,
        onError = onErrorLightMediumContrast,
        errorContainer = errorContainerLightMediumContrast,
        onErrorContainer = onErrorContainerLightMediumContrast,
        background = backgroundLightMediumContrast,
        onBackground = onBackgroundLightMediumContrast,
        surface = surfaceLightMediumContrast,
        onSurface = onSurfaceLightMediumContrast,
        surfaceVariant = surfaceVariantLightMediumContrast,
        onSurfaceVariant = onSurfaceVariantLightMediumContrast,
        outline = outlineLightMediumContrast,
        outlineVariant = outlineVariantLightMediumContrast,
        scrim = scrimLightMediumContrast,
        inverseSurface = inverseSurfaceLightMediumContrast,
        inverseOnSurface = inverseOnSurfaceLightMediumContrast,
        inversePrimary = inversePrimaryLightMediumContrast,
        surfaceDim = surfaceDimLightMediumContrast,
        surfaceBright = surfaceBrightLightMediumContrast,
        surfaceContainerLowest = surfaceContainerLowestLightMediumContrast,
        surfaceContainerLow = surfaceContainerLowLightMediumContrast,
        surfaceContainer = surfaceContainerLightMediumContrast,
        surfaceContainerHigh = surfaceContainerHighLightMediumContrast,
        surfaceContainerHighest = surfaceContainerHighestLightMediumContrast,
    )

    private val highContrastLightColorScheme = lightColorScheme(
        primary = primaryLightHighContrast,
        onPrimary = onPrimaryLightHighContrast,
        primaryContainer = primaryContainerLightHighContrast,
        onPrimaryContainer = onPrimaryContainerLightHighContrast,
        secondary = secondaryLightHighContrast,
        onSecondary = onSecondaryLightHighContrast,
        secondaryContainer = secondaryContainerLightHighContrast,
        onSecondaryContainer = onSecondaryContainerLightHighContrast,
        tertiary = tertiaryLightHighContrast,
        onTertiary = onTertiaryLightHighContrast,
        tertiaryContainer = tertiaryContainerLightHighContrast,
        onTertiaryContainer = onTertiaryContainerLightHighContrast,
        error = errorLightHighContrast,
        onError = onErrorLightHighContrast,
        errorContainer = errorContainerLightHighContrast,
        onErrorContainer = onErrorContainerLightHighContrast,
        background = backgroundLightHighContrast,
        onBackground = onBackgroundLightHighContrast,
        surface = surfaceLightHighContrast,
        onSurface = onSurfaceLightHighContrast,
        surfaceVariant = surfaceVariantLightHighContrast,
        onSurfaceVariant = onSurfaceVariantLightHighContrast,
        outline = outlineLightHighContrast,
        outlineVariant = outlineVariantLightHighContrast,
        scrim = scrimLightHighContrast,
        inverseSurface = inverseSurfaceLightHighContrast,
        inverseOnSurface = inverseOnSurfaceLightHighContrast,
        inversePrimary = inversePrimaryLightHighContrast,
        surfaceDim = surfaceDimLightHighContrast,
        surfaceBright = surfaceBrightLightHighContrast,
        surfaceContainerLowest = surfaceContainerLowestLightHighContrast,
        surfaceContainerLow = surfaceContainerLowLightHighContrast,
        surfaceContainer = surfaceContainerLightHighContrast,
        surfaceContainerHigh = surfaceContainerHighLightHighContrast,
        surfaceContainerHighest = surfaceContainerHighestLightHighContrast,
    )

    private val mediumContrastDarkColorScheme = darkColorScheme(
        primary = primaryDarkMediumContrast,
        onPrimary = onPrimaryDarkMediumContrast,
        primaryContainer = primaryContainerDarkMediumContrast,
        onPrimaryContainer = onPrimaryContainerDarkMediumContrast,
        secondary = secondaryDarkMediumContrast,
        onSecondary = onSecondaryDarkMediumContrast,
        secondaryContainer = secondaryContainerDarkMediumContrast,
        onSecondaryContainer = onSecondaryContainerDarkMediumContrast,
        tertiary = tertiaryDarkMediumContrast,
        onTertiary = onTertiaryDarkMediumContrast,
        tertiaryContainer = tertiaryContainerDarkMediumContrast,
        onTertiaryContainer = onTertiaryContainerDarkMediumContrast,
        error = errorDarkMediumContrast,
        onError = onErrorDarkMediumContrast,
        errorContainer = errorContainerDarkMediumContrast,
        onErrorContainer = onErrorContainerDarkMediumContrast,
        background = backgroundDarkMediumContrast,
        onBackground = onBackgroundDarkMediumContrast,
        surface = surfaceDarkMediumContrast,
        onSurface = onSurfaceDarkMediumContrast,
        surfaceVariant = surfaceVariantDarkMediumContrast,
        onSurfaceVariant = onSurfaceVariantDarkMediumContrast,
        outline = outlineDarkMediumContrast,
        outlineVariant = outlineVariantDarkMediumContrast,
        scrim = scrimDarkMediumContrast,
        inverseSurface = inverseSurfaceDarkMediumContrast,
        inverseOnSurface = inverseOnSurfaceDarkMediumContrast,
        inversePrimary = inversePrimaryDarkMediumContrast,
        surfaceDim = surfaceDimDarkMediumContrast,
        surfaceBright = surfaceBrightDarkMediumContrast,
        surfaceContainerLowest = surfaceContainerLowestDarkMediumContrast,
        surfaceContainerLow = surfaceContainerLowDarkMediumContrast,
        surfaceContainer = surfaceContainerDarkMediumContrast,
        surfaceContainerHigh = surfaceContainerHighDarkMediumContrast,
        surfaceContainerHighest = surfaceContainerHighestDarkMediumContrast,
    )

    private val highContrastDarkColorScheme = darkColorScheme(
        primary = primaryDarkHighContrast,
        onPrimary = onPrimaryDarkHighContrast,
        primaryContainer = primaryContainerDarkHighContrast,
        onPrimaryContainer = onPrimaryContainerDarkHighContrast,
        secondary = secondaryDarkHighContrast,
        onSecondary = onSecondaryDarkHighContrast,
        secondaryContainer = secondaryContainerDarkHighContrast,
        onSecondaryContainer = onSecondaryContainerDarkHighContrast,
        tertiary = tertiaryDarkHighContrast,
        onTertiary = onTertiaryDarkHighContrast,
        tertiaryContainer = tertiaryContainerDarkHighContrast,
        onTertiaryContainer = onTertiaryContainerDarkHighContrast,
        error = errorDarkHighContrast,
        onError = onErrorDarkHighContrast,
        errorContainer = errorContainerDarkHighContrast,
        onErrorContainer = onErrorContainerDarkHighContrast,
        background = backgroundDarkHighContrast,
        onBackground = onBackgroundDarkHighContrast,
        surface = surfaceDarkHighContrast,
        onSurface = onSurfaceDarkHighContrast,
        surfaceVariant = surfaceVariantDarkHighContrast,
        onSurfaceVariant = onSurfaceVariantDarkHighContrast,
        outline = outlineDarkHighContrast,
        outlineVariant = outlineVariantDarkHighContrast,
        scrim = scrimDarkHighContrast,
        inverseSurface = inverseSurfaceDarkHighContrast,
        inverseOnSurface = inverseOnSurfaceDarkHighContrast,
        inversePrimary = inversePrimaryDarkHighContrast,
        surfaceDim = surfaceDimDarkHighContrast,
        surfaceBright = surfaceBrightDarkHighContrast,
        surfaceContainerLowest = surfaceContainerLowestDarkHighContrast,
        surfaceContainerLow = surfaceContainerLowDarkHighContrast,
        surfaceContainer = surfaceContainerDarkHighContrast,
        surfaceContainerHigh = surfaceContainerHighDarkHighContrast,
        surfaceContainerHighest = surfaceContainerHighestDarkHighContrast,
    )

    val unspecified_scheme = ColorFamily(
        Color.Unspecified, Color.Unspecified, Color.Unspecified, Color.Unspecified
    )

    @Composable
    fun Theme(
        darkTheme: Boolean = isSystemInDarkTheme(),
        content: @Composable() () -> Unit,
    ) {
        val colorScheme = if (darkTheme) {
            darkScheme
        } else {
            lightScheme
        }

        MaterialTheme(
            colorScheme = colorScheme,
            content = content,
        )
    }


}