package io.nekohasekai.sagernet.compose.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** 星垂月涌 */
object Indigo {
    val primaryLight = Color(0xFF24389C)
    val onPrimaryLight = Color(0xFFFFFFFF)
    val primaryContainerLight = Color(0xFF3F51B5)
    val onPrimaryContainerLight = Color(0xFFCACFFF)
    val secondaryLight = Color(0xFF2D4ADD)
    val onSecondaryLight = Color(0xFFFFFFFF)
    val secondaryContainerLight = Color(0xFF4B65F7)
    val onSecondaryContainerLight = Color(0xFFFFFBFF)
    val tertiaryLight = Color(0xFF4F1C9E)
    val onTertiaryLight = Color(0xFFFFFFFF)
    val tertiaryContainerLight = Color(0xFF673AB7)
    val onTertiaryContainerLight = Color(0xFFD8C2FF)
    val errorLight = Color(0xFFBA1A1A)
    val onErrorLight = Color(0xFFFFFFFF)
    val errorContainerLight = Color(0xFFFFDAD6)
    val onErrorContainerLight = Color(0xFF93000A)
    val backgroundLight = Color(0xFFFBF8FF)
    val onBackgroundLight = Color(0xFF1A1B22)
    val surfaceLight = Color(0xFFFBF8FF)
    val onSurfaceLight = Color(0xFF1A1B22)
    val surfaceVariantLight = Color(0xFFE2E1F1)
    val onSurfaceVariantLight = Color(0xFF454652)
    val outlineLight = Color(0xFF757684)
    val outlineVariantLight = Color(0xFFC5C5D4)
    val scrimLight = Color(0xFF000000)
    val inverseSurfaceLight = Color(0xFF2F3037)
    val inverseOnSurfaceLight = Color(0xFFF2EFF9)
    val inversePrimaryLight = Color(0xFFBAC3FF)
    val surfaceDimLight = Color(0xFFDBD9E2)
    val surfaceBrightLight = Color(0xFFFBF8FF)
    val surfaceContainerLowestLight = Color(0xFFFFFFFF)
    val surfaceContainerLowLight = Color(0xFFF4F2FC)
    val surfaceContainerLight = Color(0xFFEFEDF6)
    val surfaceContainerHighLight = Color(0xFFE9E7F0)
    val surfaceContainerHighestLight = Color(0xFFE3E1EA)

    val primaryLightMediumContrast = Color(0xFF13298F)
    val onPrimaryLightMediumContrast = Color(0xFFFFFFFF)
    val primaryContainerLightMediumContrast = Color(0xFF3F51B5)
    val onPrimaryContainerLightMediumContrast = Color(0xFFFFFFFF)
    val secondaryLightMediumContrast = Color(0xFF0022A0)
    val onSecondaryLightMediumContrast = Color(0xFFFFFFFF)
    val secondaryContainerLightMediumContrast = Color(0xFF435DEE)
    val onSecondaryContainerLightMediumContrast = Color(0xFFFFFFFF)
    val tertiaryLightMediumContrast = Color(0xFF460A95)
    val onTertiaryLightMediumContrast = Color(0xFFFFFFFF)
    val tertiaryContainerLightMediumContrast = Color(0xFF673AB7)
    val onTertiaryContainerLightMediumContrast = Color(0xFFFFF9FD)
    val errorLightMediumContrast = Color(0xFF740006)
    val onErrorLightMediumContrast = Color(0xFFFFFFFF)
    val errorContainerLightMediumContrast = Color(0xFFCF2C27)
    val onErrorContainerLightMediumContrast = Color(0xFFFFFFFF)
    val backgroundLightMediumContrast = Color(0xFFFBF8FF)
    val onBackgroundLightMediumContrast = Color(0xFF1A1B22)
    val surfaceLightMediumContrast = Color(0xFFFBF8FF)
    val onSurfaceLightMediumContrast = Color(0xFF101117)
    val surfaceVariantLightMediumContrast = Color(0xFFE2E1F1)
    val onSurfaceVariantLightMediumContrast = Color(0xFF343541)
    val outlineLightMediumContrast = Color(0xFF51525E)
    val outlineVariantLightMediumContrast = Color(0xFF6B6C7A)
    val scrimLightMediumContrast = Color(0xFF000000)
    val inverseSurfaceLightMediumContrast = Color(0xFF2F3037)
    val inverseOnSurfaceLightMediumContrast = Color(0xFFF2EFF9)
    val inversePrimaryLightMediumContrast = Color(0xFFBAC3FF)
    val surfaceDimLightMediumContrast = Color(0xFFC7C5CE)
    val surfaceBrightLightMediumContrast = Color(0xFFFBF8FF)
    val surfaceContainerLowestLightMediumContrast = Color(0xFFFFFFFF)
    val surfaceContainerLowLightMediumContrast = Color(0xFFF4F2FC)
    val surfaceContainerLightMediumContrast = Color(0xFFE9E7F0)
    val surfaceContainerHighLightMediumContrast = Color(0xFFDDDCE5)
    val surfaceContainerHighestLightMediumContrast = Color(0xFFD2D0D9)

    val primaryLightHighContrast = Color(0xFF001B86)
    val onPrimaryLightHighContrast = Color(0xFFFFFFFF)
    val primaryContainerLightHighContrast = Color(0xFF2C3FA3)
    val onPrimaryContainerLightHighContrast = Color(0xFFFFFFFF)
    val secondaryLightHighContrast = Color(0xFF001B86)
    val onSecondaryLightHighContrast = Color(0xFFFFFFFF)
    val secondaryContainerLightHighContrast = Color(0xFF0D32CA)
    val onSecondaryContainerLightHighContrast = Color(0xFFFFFFFF)
    val tertiaryLightHighContrast = Color(0xFF3A0082)
    val onTertiaryLightHighContrast = Color(0xFFFFFFFF)
    val tertiaryContainerLightHighContrast = Color(0xFF592AA9)
    val onTertiaryContainerLightHighContrast = Color(0xFFFFFFFF)
    val errorLightHighContrast = Color(0xFF600004)
    val onErrorLightHighContrast = Color(0xFFFFFFFF)
    val errorContainerLightHighContrast = Color(0xFF98000A)
    val onErrorContainerLightHighContrast = Color(0xFFFFFFFF)
    val backgroundLightHighContrast = Color(0xFFFBF8FF)
    val onBackgroundLightHighContrast = Color(0xFF1A1B22)
    val surfaceLightHighContrast = Color(0xFFFBF8FF)
    val onSurfaceLightHighContrast = Color(0xFF000000)
    val surfaceVariantLightHighContrast = Color(0xFFE2E1F1)
    val onSurfaceVariantLightHighContrast = Color(0xFF000000)
    val outlineLightHighContrast = Color(0xFF2A2B37)
    val outlineVariantLightHighContrast = Color(0xFF474855)
    val scrimLightHighContrast = Color(0xFF000000)
    val inverseSurfaceLightHighContrast = Color(0xFF2F3037)
    val inverseOnSurfaceLightHighContrast = Color(0xFFFFFFFF)
    val inversePrimaryLightHighContrast = Color(0xFFBAC3FF)
    val surfaceDimLightHighContrast = Color(0xFFB9B8C0)
    val surfaceBrightLightHighContrast = Color(0xFFFBF8FF)
    val surfaceContainerLowestLightHighContrast = Color(0xFFFFFFFF)
    val surfaceContainerLowLightHighContrast = Color(0xFFF2EFF9)
    val surfaceContainerLightHighContrast = Color(0xFFE3E1EA)
    val surfaceContainerHighLightHighContrast = Color(0xFFD5D3DC)
    val surfaceContainerHighestLightHighContrast = Color(0xFFC7C5CE)

    val primaryDark = Color(0xFFBAC3FF)
    val onPrimaryDark = Color(0xFF08218A)
    val primaryContainerDark = Color(0xFF3F51B5)
    val onPrimaryContainerDark = Color(0xFFCACFFF)
    val secondaryDark = Color(0xFFBAC3FF)
    val onSecondaryDark = Color(0xFF001E91)
    val secondaryContainerDark = Color(0xFF7287FF)
    val onSecondaryContainerDark = Color(0xFF00073D)
    val tertiaryDark = Color(0xFFD3BBFF)
    val onTertiaryDark = Color(0xFF3F008D)
    val tertiaryContainerDark = Color(0xFF673AB7)
    val onTertiaryContainerDark = Color(0xFFD8C2FF)
    val errorDark = Color(0xFFFFB4AB)
    val onErrorDark = Color(0xFF690005)
    val errorContainerDark = Color(0xFF93000A)
    val onErrorContainerDark = Color(0xFFFFDAD6)
    val backgroundDark = Color(0xFF121319)
    val onBackgroundDark = Color(0xFFE3E1EA)
    val surfaceDark = Color(0xFF121319)
    val onSurfaceDark = Color(0xFFE3E1EA)
    val surfaceVariantDark = Color(0xFF454652)
    val onSurfaceVariantDark = Color(0xFFC5C5D4)
    val outlineDark = Color(0xFF8F909E)
    val outlineVariantDark = Color(0xFF454652)
    val scrimDark = Color(0xFF000000)
    val inverseSurfaceDark = Color(0xFFE3E1EA)
    val inverseOnSurfaceDark = Color(0xFF2F3037)
    val inversePrimaryDark = Color(0xFF4355B9)
    val surfaceDimDark = Color(0xFF121319)
    val surfaceBrightDark = Color(0xFF383940)
    val surfaceContainerLowestDark = Color(0xFF0D0E14)
    val surfaceContainerLowDark = Color(0xFF1A1B22)
    val surfaceContainerDark = Color(0xFF1F1F26)
    val surfaceContainerHighDark = Color(0xFF292930)
    val surfaceContainerHighestDark = Color(0xFF34343B)

    val primaryDarkMediumContrast = Color(0xFFD6DAFF)
    val onPrimaryDarkMediumContrast = Color(0xFF001775)
    val primaryContainerDarkMediumContrast = Color(0xFF7789F0)
    val onPrimaryContainerDarkMediumContrast = Color(0xFF000000)
    val secondaryDarkMediumContrast = Color(0xFFD6DAFF)
    val onSecondaryDarkMediumContrast = Color(0xFF001776)
    val secondaryContainerDarkMediumContrast = Color(0xFF7287FF)
    val onSecondaryContainerDarkMediumContrast = Color(0xFF000000)
    val tertiaryDarkMediumContrast = Color(0xFFE5D5FF)
    val onTertiaryDarkMediumContrast = Color(0xFF320072)
    val tertiaryContainerDarkMediumContrast = Color(0xFFA478F7)
    val onTertiaryContainerDarkMediumContrast = Color(0xFF000000)
    val errorDarkMediumContrast = Color(0xFFFFD2CC)
    val onErrorDarkMediumContrast = Color(0xFF540003)
    val errorContainerDarkMediumContrast = Color(0xFFFF5449)
    val onErrorContainerDarkMediumContrast = Color(0xFF000000)
    val backgroundDarkMediumContrast = Color(0xFF121319)
    val onBackgroundDarkMediumContrast = Color(0xFFE3E1EA)
    val surfaceDarkMediumContrast = Color(0xFF121319)
    val onSurfaceDarkMediumContrast = Color(0xFFFFFFFF)
    val surfaceVariantDarkMediumContrast = Color(0xFF454652)
    val onSurfaceVariantDarkMediumContrast = Color(0xFFDBDBEB)
    val outlineDarkMediumContrast = Color(0xFFB1B1C0)
    val outlineVariantDarkMediumContrast = Color(0xFF8F8F9D)
    val scrimDarkMediumContrast = Color(0xFF000000)
    val inverseSurfaceDarkMediumContrast = Color(0xFFE3E1EA)
    val inverseOnSurfaceDarkMediumContrast = Color(0xFF292930)
    val inversePrimaryDarkMediumContrast = Color(0xFF2A3DA1)
    val surfaceDimDarkMediumContrast = Color(0xFF121319)
    val surfaceBrightDarkMediumContrast = Color(0xFF44444B)
    val surfaceContainerLowestDarkMediumContrast = Color(0xFF06070D)
    val surfaceContainerLowDarkMediumContrast = Color(0xFF1D1D24)
    val surfaceContainerDarkMediumContrast = Color(0xFF27272E)
    val surfaceContainerHighDarkMediumContrast = Color(0xFF323239)
    val surfaceContainerHighestDarkMediumContrast = Color(0xFF3D3D44)

    val primaryDarkHighContrast = Color(0xFFEFEEFF)
    val onPrimaryDarkHighContrast = Color(0xFF000000)
    val primaryContainerDarkHighContrast = Color(0xFFB5BFFF)
    val onPrimaryContainerDarkHighContrast = Color(0xFF000532)
    val secondaryDarkHighContrast = Color(0xFFEFEEFF)
    val onSecondaryDarkHighContrast = Color(0xFF000000)
    val secondaryContainerDarkHighContrast = Color(0xFFB5BFFF)
    val onSecondaryContainerDarkHighContrast = Color(0xFF000532)
    val tertiaryDarkHighContrast = Color(0xFFF6ECFF)
    val onTertiaryDarkHighContrast = Color(0xFF000000)
    val tertiaryContainerDarkHighContrast = Color(0xFFD0B6FF)
    val onTertiaryContainerDarkHighContrast = Color(0xFF110031)
    val errorDarkHighContrast = Color(0xFFFFECE9)
    val onErrorDarkHighContrast = Color(0xFF000000)
    val errorContainerDarkHighContrast = Color(0xFFFFAEA4)
    val onErrorContainerDarkHighContrast = Color(0xFF220001)
    val backgroundDarkHighContrast = Color(0xFF121319)
    val onBackgroundDarkHighContrast = Color(0xFFE3E1EA)
    val surfaceDarkHighContrast = Color(0xFF121319)
    val onSurfaceDarkHighContrast = Color(0xFFFFFFFF)
    val surfaceVariantDarkHighContrast = Color(0xFF454652)
    val onSurfaceVariantDarkHighContrast = Color(0xFFFFFFFF)
    val outlineDarkHighContrast = Color(0xFFEFEEFE)
    val outlineVariantDarkHighContrast = Color(0xFFC1C1D0)
    val scrimDarkHighContrast = Color(0xFF000000)
    val inverseSurfaceDarkHighContrast = Color(0xFFE3E1EA)
    val inverseOnSurfaceDarkHighContrast = Color(0xFF000000)
    val inversePrimaryDarkHighContrast = Color(0xFF2A3DA1)
    val surfaceDimDarkHighContrast = Color(0xFF121319)
    val surfaceBrightDarkHighContrast = Color(0xFF4F4F57)
    val surfaceContainerLowestDarkHighContrast = Color(0xFF000000)
    val surfaceContainerLowDarkHighContrast = Color(0xFF1F1F26)
    val surfaceContainerDarkHighContrast = Color(0xFF2F3037)
    val surfaceContainerHighDarkHighContrast = Color(0xFF3B3B42)
    val surfaceContainerHighestDarkHighContrast = Color(0xFF46464D)


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