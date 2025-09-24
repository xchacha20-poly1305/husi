package io.nekohasekai.sagernet.compose.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** 落日熔金 */
object DeepOrange {
    val primaryLight = Color(0xFFB02F00)
    val onPrimaryLight = Color(0xFFFFFFFF)
    val primaryContainerLight = Color(0xFFFF5722)
    val onPrimaryContainerLight = Color(0xFF541200)
    val secondaryLight = Color(0xFFAD3306)
    val onSecondaryLight = Color(0xFFFFFFFF)
    val secondaryContainerLight = Color(0xFFFF6E40)
    val onSecondaryContainerLight = Color(0xFF631800)
    val tertiaryLight = Color(0xFF8B5000)
    val onTertiaryLight = Color(0xFFFFFFFF)
    val tertiaryContainerLight = Color(0xFFFF9800)
    val onTertiaryContainerLight = Color(0xFF653900)
    val errorLight = Color(0xFFBA1A1A)
    val onErrorLight = Color(0xFFFFFFFF)
    val errorContainerLight = Color(0xFFFFDAD6)
    val onErrorContainerLight = Color(0xFF93000A)
    val backgroundLight = Color(0xFFFFF8F6)
    val onBackgroundLight = Color(0xFF271813)
    val surfaceLight = Color(0xFFFFF8F6)
    val onSurfaceLight = Color(0xFF271813)
    val surfaceVariantLight = Color(0xFFFFDBD1)
    val onSurfaceVariantLight = Color(0xFF5B4039)
    val outlineLight = Color(0xFF907067)
    val outlineVariantLight = Color(0xFFE4BEB4)
    val scrimLight = Color(0xFF000000)
    val inverseSurfaceLight = Color(0xFF3E2C27)
    val inverseOnSurfaceLight = Color(0xFFFFEDE8)
    val inversePrimaryLight = Color(0xFFFFB5A0)
    val surfaceDimLight = Color(0xFFF1D4CC)
    val surfaceBrightLight = Color(0xFFFFF8F6)
    val surfaceContainerLowestLight = Color(0xFFFFFFFF)
    val surfaceContainerLowLight = Color(0xFFFFF1ED)
    val surfaceContainerLight = Color(0xFFFFE9E4)
    val surfaceContainerHighLight = Color(0xFFFFE2DA)
    val surfaceContainerHighestLight = Color(0xFFFADCD4)

    val primaryLightMediumContrast = Color(0xFF691800)
    val onPrimaryLightMediumContrast = Color(0xFFFFFFFF)
    val primaryContainerLightMediumContrast = Color(0xFFCA3700)
    val onPrimaryContainerLightMediumContrast = Color(0xFFFFFFFF)
    val secondaryLightMediumContrast = Color(0xFF681A00)
    val onSecondaryLightMediumContrast = Color(0xFFFFFFFF)
    val secondaryContainerLightMediumContrast = Color(0xFFC14216)
    val onSecondaryContainerLightMediumContrast = Color(0xFFFFFFFF)
    val tertiaryLightMediumContrast = Color(0xFF522D00)
    val onTertiaryLightMediumContrast = Color(0xFFFFFFFF)
    val tertiaryContainerLightMediumContrast = Color(0xFF9F5D00)
    val onTertiaryContainerLightMediumContrast = Color(0xFFFFFFFF)
    val errorLightMediumContrast = Color(0xFF740006)
    val onErrorLightMediumContrast = Color(0xFFFFFFFF)
    val errorContainerLightMediumContrast = Color(0xFFCF2C27)
    val onErrorContainerLightMediumContrast = Color(0xFFFFFFFF)
    val backgroundLightMediumContrast = Color(0xFFFFF8F6)
    val onBackgroundLightMediumContrast = Color(0xFF271813)
    val surfaceLightMediumContrast = Color(0xFFFFF8F6)
    val onSurfaceLightMediumContrast = Color(0xFF1C0E09)
    val surfaceVariantLightMediumContrast = Color(0xFFFFDBD1)
    val onSurfaceVariantLightMediumContrast = Color(0xFF493029)
    val outlineLightMediumContrast = Color(0xFF684C44)
    val outlineVariantLightMediumContrast = Color(0xFF85665D)
    val scrimLightMediumContrast = Color(0xFF000000)
    val inverseSurfaceLightMediumContrast = Color(0xFF3E2C27)
    val inverseOnSurfaceLightMediumContrast = Color(0xFFFFEDE8)
    val inversePrimaryLightMediumContrast = Color(0xFFFFB5A0)
    val surfaceDimLightMediumContrast = Color(0xFFDDC0B9)
    val surfaceBrightLightMediumContrast = Color(0xFFFFF8F6)
    val surfaceContainerLowestLightMediumContrast = Color(0xFFFFFFFF)
    val surfaceContainerLowLightMediumContrast = Color(0xFFFFF1ED)
    val surfaceContainerLightMediumContrast = Color(0xFFFFE2DA)
    val surfaceContainerHighLightMediumContrast = Color(0xFFF4D7CF)
    val surfaceContainerHighestLightMediumContrast = Color(0xFFE8CBC4)

    val primaryLightHighContrast = Color(0xFF581300)
    val onPrimaryLightHighContrast = Color(0xFFFFFFFF)
    val primaryContainerLightHighContrast = Color(0xFF8B2300)
    val onPrimaryContainerLightHighContrast = Color(0xFFFFFFFF)
    val secondaryLightHighContrast = Color(0xFF571400)
    val onSecondaryLightHighContrast = Color(0xFFFFFFFF)
    val secondaryContainerLightHighContrast = Color(0xFF892500)
    val onSecondaryContainerLightHighContrast = Color(0xFFFFFFFF)
    val tertiaryLightHighContrast = Color(0xFF442500)
    val onTertiaryLightHighContrast = Color(0xFFFFFFFF)
    val tertiaryContainerLightHighContrast = Color(0xFF6D3E00)
    val onTertiaryContainerLightHighContrast = Color(0xFFFFFFFF)
    val errorLightHighContrast = Color(0xFF600004)
    val onErrorLightHighContrast = Color(0xFFFFFFFF)
    val errorContainerLightHighContrast = Color(0xFF98000A)
    val onErrorContainerLightHighContrast = Color(0xFFFFFFFF)
    val backgroundLightHighContrast = Color(0xFFFFF8F6)
    val onBackgroundLightHighContrast = Color(0xFF271813)
    val surfaceLightHighContrast = Color(0xFFFFF8F6)
    val onSurfaceLightHighContrast = Color(0xFF000000)
    val surfaceVariantLightHighContrast = Color(0xFFFFDBD1)
    val onSurfaceVariantLightHighContrast = Color(0xFF000000)
    val outlineLightHighContrast = Color(0xFF3E2620)
    val outlineVariantLightHighContrast = Color(0xFF5E433B)
    val scrimLightHighContrast = Color(0xFF000000)
    val inverseSurfaceLightHighContrast = Color(0xFF3E2C27)
    val inverseOnSurfaceLightHighContrast = Color(0xFFFFFFFF)
    val inversePrimaryLightHighContrast = Color(0xFFFFB5A0)
    val surfaceDimLightHighContrast = Color(0xFFCEB3AC)
    val surfaceBrightLightHighContrast = Color(0xFFFFF8F6)
    val surfaceContainerLowestLightHighContrast = Color(0xFFFFFFFF)
    val surfaceContainerLowLightHighContrast = Color(0xFFFFEDE8)
    val surfaceContainerLightHighContrast = Color(0xFFFADCD4)
    val surfaceContainerHighLightHighContrast = Color(0xFFEBCEC7)
    val surfaceContainerHighestLightHighContrast = Color(0xFFDDC0B9)

    val primaryDark = Color(0xFFFFB5A0)
    val onPrimaryDark = Color(0xFF5F1500)
    val primaryContainerDark = Color(0xFFFF5722)
    val onPrimaryContainerDark = Color(0xFF541200)
    val secondaryDark = Color(0xFFFFB59F)
    val onSecondaryDark = Color(0xFF5E1600)
    val secondaryContainerDark = Color(0xFFFF6E40)
    val onSecondaryContainerDark = Color(0xFF631800)
    val tertiaryDark = Color(0xFFFFC081)
    val onTertiaryDark = Color(0xFF4A2800)
    val tertiaryContainerDark = Color(0xFFFF9800)
    val onTertiaryContainerDark = Color(0xFF653900)
    val errorDark = Color(0xFFFFB4AB)
    val onErrorDark = Color(0xFF690005)
    val errorContainerDark = Color(0xFF93000A)
    val onErrorContainerDark = Color(0xFFFFDAD6)
    val backgroundDark = Color(0xFF1E100C)
    val onBackgroundDark = Color(0xFFFADCD4)
    val surfaceDark = Color(0xFF1E100C)
    val onSurfaceDark = Color(0xFFFADCD4)
    val surfaceVariantDark = Color(0xFF5B4039)
    val onSurfaceVariantDark = Color(0xFFE4BEB4)
    val outlineDark = Color(0xFFAB8980)
    val outlineVariantDark = Color(0xFF5B4039)
    val scrimDark = Color(0xFF000000)
    val inverseSurfaceDark = Color(0xFFFADCD4)
    val inverseOnSurfaceDark = Color(0xFF3E2C27)
    val inversePrimaryDark = Color(0xFFB02F00)
    val surfaceDimDark = Color(0xFF1E100C)
    val surfaceBrightDark = Color(0xFF483530)
    val surfaceContainerLowestDark = Color(0xFF180B07)
    val surfaceContainerLowDark = Color(0xFF271813)
    val surfaceContainerDark = Color(0xFF2C1C17)
    val surfaceContainerHighDark = Color(0xFF372621)
    val surfaceContainerHighestDark = Color(0xFF43302B)

    val primaryDarkMediumContrast = Color(0xFFFFD2C6)
    val onPrimaryDarkMediumContrast = Color(0xFF4C0F00)
    val primaryContainerDarkMediumContrast = Color(0xFFFF5722)
    val onPrimaryContainerDarkMediumContrast = Color(0xFF000000)
    val secondaryDarkMediumContrast = Color(0xFFFFD3C6)
    val onSecondaryDarkMediumContrast = Color(0xFF4C1000)
    val secondaryContainerDarkMediumContrast = Color(0xFFFF6E40)
    val onSecondaryContainerDarkMediumContrast = Color(0xFF1E0300)
    val tertiaryDarkMediumContrast = Color(0xFFFFD5AE)
    val onTertiaryDarkMediumContrast = Color(0xFF3B1F00)
    val tertiaryContainerDarkMediumContrast = Color(0xFFFF9800)
    val onTertiaryContainerDarkMediumContrast = Color(0xFF3A1F00)
    val errorDarkMediumContrast = Color(0xFFFFD2CC)
    val onErrorDarkMediumContrast = Color(0xFF540003)
    val errorContainerDarkMediumContrast = Color(0xFFFF5449)
    val onErrorContainerDarkMediumContrast = Color(0xFF000000)
    val backgroundDarkMediumContrast = Color(0xFF1E100C)
    val onBackgroundDarkMediumContrast = Color(0xFFFADCD4)
    val surfaceDarkMediumContrast = Color(0xFF1E100C)
    val onSurfaceDarkMediumContrast = Color(0xFFFFFFFF)
    val surfaceVariantDarkMediumContrast = Color(0xFF5B4039)
    val onSurfaceVariantDarkMediumContrast = Color(0xFFFBD4C9)
    val outlineDarkMediumContrast = Color(0xFFCEAAA0)
    val outlineVariantDarkMediumContrast = Color(0xFFAB897F)
    val scrimDarkMediumContrast = Color(0xFF000000)
    val inverseSurfaceDarkMediumContrast = Color(0xFFFADCD4)
    val inverseOnSurfaceDarkMediumContrast = Color(0xFF372621)
    val inversePrimaryDarkMediumContrast = Color(0xFF892200)
    val surfaceDimDarkMediumContrast = Color(0xFF1E100C)
    val surfaceBrightDarkMediumContrast = Color(0xFF54403B)
    val surfaceContainerLowestDarkMediumContrast = Color(0xFF100503)
    val surfaceContainerLowDarkMediumContrast = Color(0xFF291A15)
    val surfaceContainerDarkMediumContrast = Color(0xFF35241F)
    val surfaceContainerHighDarkMediumContrast = Color(0xFF402E29)
    val surfaceContainerHighestDarkMediumContrast = Color(0xFF4C3934)

    val primaryDarkHighContrast = Color(0xFFFFECE7)
    val onPrimaryDarkHighContrast = Color(0xFF000000)
    val primaryContainerDarkHighContrast = Color(0xFFFFAF98)
    val onPrimaryContainerDarkHighContrast = Color(0xFF1E0300)
    val secondaryDarkHighContrast = Color(0xFFFFECE7)
    val onSecondaryDarkHighContrast = Color(0xFF000000)
    val secondaryContainerDarkHighContrast = Color(0xFFFFAF97)
    val onSecondaryContainerDarkHighContrast = Color(0xFF1E0300)
    val tertiaryDarkHighContrast = Color(0xFFFFEDDF)
    val onTertiaryDarkHighContrast = Color(0xFF000000)
    val tertiaryContainerDarkHighContrast = Color(0xFFFFB363)
    val onTertiaryContainerDarkHighContrast = Color(0xFF150800)
    val errorDarkHighContrast = Color(0xFFFFECE9)
    val onErrorDarkHighContrast = Color(0xFF000000)
    val errorContainerDarkHighContrast = Color(0xFFFFAEA4)
    val onErrorContainerDarkHighContrast = Color(0xFF220001)
    val backgroundDarkHighContrast = Color(0xFF1E100C)
    val onBackgroundDarkHighContrast = Color(0xFFFADCD4)
    val surfaceDarkHighContrast = Color(0xFF1E100C)
    val onSurfaceDarkHighContrast = Color(0xFFFFFFFF)
    val surfaceVariantDarkHighContrast = Color(0xFF5B4039)
    val onSurfaceVariantDarkHighContrast = Color(0xFFFFFFFF)
    val outlineDarkHighContrast = Color(0xFFFFECE7)
    val outlineVariantDarkHighContrast = Color(0xFFE0BAB0)
    val scrimDarkHighContrast = Color(0xFF000000)
    val inverseSurfaceDarkHighContrast = Color(0xFFFADCD4)
    val inverseOnSurfaceDarkHighContrast = Color(0xFF000000)
    val inversePrimaryDarkHighContrast = Color(0xFF892200)
    val surfaceDimDarkHighContrast = Color(0xFF1E100C)
    val surfaceBrightDarkHighContrast = Color(0xFF604B46)
    val surfaceContainerLowestDarkHighContrast = Color(0xFF000000)
    val surfaceContainerLowDarkHighContrast = Color(0xFF2C1C17)
    val surfaceContainerDarkHighContrast = Color(0xFF3E2C27)
    val surfaceContainerHighDarkHighContrast = Color(0xFF4A3732)
    val surfaceContainerHighestDarkHighContrast = Color(0xFF56423D)


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