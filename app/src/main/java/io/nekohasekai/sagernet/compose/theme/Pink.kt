package io.nekohasekai.sagernet.compose.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** 醉卧沙场 */
object Pink {
    val primaryLight = Color(0xFFB80049)
    val onPrimaryLight = Color(0xFFFFFFFF)
    val primaryContainerLight = Color(0xFFE2165F)
    val onPrimaryContainerLight = Color(0xFFFFFBFF)
    val secondaryLight = Color(0xFFB70052)
    val onSecondaryLight = Color(0xFFFFFFFF)
    val secondaryContainerLight = Color(0xFFDD2269)
    val onSecondaryContainerLight = Color(0xFFFFFBFF)
    val tertiaryLight = Color(0xFFB81311)
    val onTertiaryLight = Color(0xFFFFFFFF)
    val tertiaryContainerLight = Color(0xFFDC3128)
    val onTertiaryContainerLight = Color(0xFFFFFBFF)
    val errorLight = Color(0xFFBA1A1A)
    val onErrorLight = Color(0xFFFFFFFF)
    val errorContainerLight = Color(0xFFFFDAD6)
    val onErrorContainerLight = Color(0xFF93000A)
    val backgroundLight = Color(0xFFFFF8F7)
    val onBackgroundLight = Color(0xFF28171A)
    val surfaceLight = Color(0xFFFFF8F7)
    val onSurfaceLight = Color(0xFF28171A)
    val surfaceVariantLight = Color(0xFFFFD9DE)
    val onSurfaceVariantLight = Color(0xFF5B3F43)
    val outlineLight = Color(0xFF8F6F73)
    val outlineVariantLight = Color(0xFFE4BDC2)
    val scrimLight = Color(0xFF000000)
    val inverseSurfaceLight = Color(0xFF3E2B2E)
    val inverseOnSurfaceLight = Color(0xFFFFECEE)
    val inversePrimaryLight = Color(0xFFFFB2BE)
    val surfaceDimLight = Color(0xFFF1D3D6)
    val surfaceBrightLight = Color(0xFFFFF8F7)
    val surfaceContainerLowestLight = Color(0xFFFFFFFF)
    val surfaceContainerLowLight = Color(0xFFFFF0F1)
    val surfaceContainerLight = Color(0xFFFFE9EA)
    val surfaceContainerHighLight = Color(0xFFFFE1E4)
    val surfaceContainerHighestLight = Color(0xFFFADBDE)

    val primaryLightMediumContrast = Color(0xFF71002A)
    val onPrimaryLightMediumContrast = Color(0xFFFFFFFF)
    val primaryContainerLightMediumContrast = Color(0xFFD70357)
    val onPrimaryContainerLightMediumContrast = Color(0xFFFFFFFF)
    val secondaryLightMediumContrast = Color(0xFF710030)
    val onSecondaryLightMediumContrast = Color(0xFFFFFFFF)
    val secondaryContainerLightMediumContrast = Color(0xFFD31662)
    val onSecondaryContainerLightMediumContrast = Color(0xFFFFFFFF)
    val tertiaryLightMediumContrast = Color(0xFF740003)
    val onTertiaryLightMediumContrast = Color(0xFFFFFFFF)
    val tertiaryContainerLightMediumContrast = Color(0xFFD12921)
    val onTertiaryContainerLightMediumContrast = Color(0xFFFFFFFF)
    val errorLightMediumContrast = Color(0xFF740006)
    val onErrorLightMediumContrast = Color(0xFFFFFFFF)
    val errorContainerLightMediumContrast = Color(0xFFCF2C27)
    val onErrorContainerLightMediumContrast = Color(0xFFFFFFFF)
    val backgroundLightMediumContrast = Color(0xFFFFF8F7)
    val onBackgroundLightMediumContrast = Color(0xFF28171A)
    val surfaceLightMediumContrast = Color(0xFFFFF8F7)
    val onSurfaceLightMediumContrast = Color(0xFF1C0D0F)
    val surfaceVariantLightMediumContrast = Color(0xFFFFD9DE)
    val onSurfaceVariantLightMediumContrast = Color(0xFF492F33)
    val outlineLightMediumContrast = Color(0xFF684B4F)
    val outlineVariantLightMediumContrast = Color(0xFF856569)
    val scrimLightMediumContrast = Color(0xFF000000)
    val inverseSurfaceLightMediumContrast = Color(0xFF3E2B2E)
    val inverseOnSurfaceLightMediumContrast = Color(0xFFFFECEE)
    val inversePrimaryLightMediumContrast = Color(0xFFFFB2BE)
    val surfaceDimLightMediumContrast = Color(0xFFDDBFC3)
    val surfaceBrightLightMediumContrast = Color(0xFFFFF8F7)
    val surfaceContainerLowestLightMediumContrast = Color(0xFFFFFFFF)
    val surfaceContainerLowLightMediumContrast = Color(0xFFFFF0F1)
    val surfaceContainerLightMediumContrast = Color(0xFFFFE1E4)
    val surfaceContainerHighLightMediumContrast = Color(0xFFF4D5D9)
    val surfaceContainerHighestLightMediumContrast = Color(0xFFE8CACE)

    val primaryLightHighContrast = Color(0xFF5F0022)
    val onPrimaryLightHighContrast = Color(0xFFFFFFFF)
    val primaryContainerLightHighContrast = Color(0xFF95003A)
    val onPrimaryContainerLightHighContrast = Color(0xFFFFFFFF)
    val secondaryLightHighContrast = Color(0xFF5E0027)
    val onSecondaryLightHighContrast = Color(0xFFFFFFFF)
    val secondaryContainerLightHighContrast = Color(0xFF940041)
    val onSecondaryContainerLightHighContrast = Color(0xFFFFFFFF)
    val tertiaryLightHighContrast = Color(0xFF610002)
    val onTertiaryLightHighContrast = Color(0xFFFFFFFF)
    val tertiaryContainerLightHighContrast = Color(0xFF980006)
    val onTertiaryContainerLightHighContrast = Color(0xFFFFFFFF)
    val errorLightHighContrast = Color(0xFF600004)
    val onErrorLightHighContrast = Color(0xFFFFFFFF)
    val errorContainerLightHighContrast = Color(0xFF98000A)
    val onErrorContainerLightHighContrast = Color(0xFFFFFFFF)
    val backgroundLightHighContrast = Color(0xFFFFF8F7)
    val onBackgroundLightHighContrast = Color(0xFF28171A)
    val surfaceLightHighContrast = Color(0xFFFFF8F7)
    val onSurfaceLightHighContrast = Color(0xFF000000)
    val surfaceVariantLightHighContrast = Color(0xFFFFD9DE)
    val onSurfaceVariantLightHighContrast = Color(0xFF000000)
    val outlineLightHighContrast = Color(0xFF3E2529)
    val outlineVariantLightHighContrast = Color(0xFF5E4246)
    val scrimLightHighContrast = Color(0xFF000000)
    val inverseSurfaceLightHighContrast = Color(0xFF3E2B2E)
    val inverseOnSurfaceLightHighContrast = Color(0xFFFFFFFF)
    val inversePrimaryLightHighContrast = Color(0xFFFFB2BE)
    val surfaceDimLightHighContrast = Color(0xFFCEB2B5)
    val surfaceBrightLightHighContrast = Color(0xFFFFF8F7)
    val surfaceContainerLowestLightHighContrast = Color(0xFFFFFFFF)
    val surfaceContainerLowLightHighContrast = Color(0xFFFFECEE)
    val surfaceContainerLightHighContrast = Color(0xFFFADBDE)
    val surfaceContainerHighLightHighContrast = Color(0xFFEBCDD0)
    val surfaceContainerHighestLightHighContrast = Color(0xFFDDBFC3)

    val primaryDark = Color(0xFFFFB2BE)
    val onPrimaryDark = Color(0xFF660025)
    val primaryContainerDark = Color(0xFFFF4E7C)
    val onPrimaryContainerDark = Color(0xFF200007)
    val secondaryDark = Color(0xFFFFB1C1)
    val onSecondaryDark = Color(0xFF66002A)
    val secondaryContainerDark = Color(0xFFFF4C86)
    val onSecondaryContainerDark = Color(0xFF530021)
    val tertiaryDark = Color(0xFFFFB4A9)
    val onTertiaryDark = Color(0xFF690002)
    val tertiaryContainerDark = Color(0xFFFF5545)
    val onTertiaryContainerDark = Color(0xFF450001)
    val errorDark = Color(0xFFFFB4AB)
    val onErrorDark = Color(0xFF690005)
    val errorContainerDark = Color(0xFF93000A)
    val onErrorContainerDark = Color(0xFFFFDAD6)
    val backgroundDark = Color(0xFF1F0F12)
    val onBackgroundDark = Color(0xFFFADBDE)
    val surfaceDark = Color(0xFF1F0F12)
    val onSurfaceDark = Color(0xFFFADBDE)
    val surfaceVariantDark = Color(0xFF5B3F43)
    val onSurfaceVariantDark = Color(0xFFE4BDC2)
    val outlineDark = Color(0xFFAB888C)
    val outlineVariantDark = Color(0xFF5B3F43)
    val scrimDark = Color(0xFF000000)
    val inverseSurfaceDark = Color(0xFFFADBDE)
    val inverseOnSurfaceDark = Color(0xFF3E2B2E)
    val inversePrimaryDark = Color(0xFFBC004B)
    val surfaceDimDark = Color(0xFF1F0F12)
    val surfaceBrightDark = Color(0xFF483437)
    val surfaceContainerLowestDark = Color(0xFF190A0C)
    val surfaceContainerLowDark = Color(0xFF28171A)
    val surfaceContainerDark = Color(0xFF2C1B1E)
    val surfaceContainerHighDark = Color(0xFF372528)
    val surfaceContainerHighestDark = Color(0xFF433032)

    val primaryDarkMediumContrast = Color(0xFFFFD1D7)
    val onPrimaryDarkMediumContrast = Color(0xFF52001C)
    val primaryContainerDarkMediumContrast = Color(0xFFFF4E7C)
    val onPrimaryContainerDarkMediumContrast = Color(0xFF000000)
    val secondaryDarkMediumContrast = Color(0xFFFFD1D9)
    val onSecondaryDarkMediumContrast = Color(0xFF520021)
    val secondaryContainerDarkMediumContrast = Color(0xFFFF4C86)
    val onSecondaryContainerDarkMediumContrast = Color(0xFF000000)
    val tertiaryDarkMediumContrast = Color(0xFFFFD2CC)
    val onTertiaryDarkMediumContrast = Color(0xFF540002)
    val tertiaryContainerDarkMediumContrast = Color(0xFFFF5545)
    val onTertiaryContainerDarkMediumContrast = Color(0xFF000000)
    val errorDarkMediumContrast = Color(0xFFFFD2CC)
    val onErrorDarkMediumContrast = Color(0xFF540003)
    val errorContainerDarkMediumContrast = Color(0xFFFF5449)
    val onErrorContainerDarkMediumContrast = Color(0xFF000000)
    val backgroundDarkMediumContrast = Color(0xFF1F0F12)
    val onBackgroundDarkMediumContrast = Color(0xFFFADBDE)
    val surfaceDarkMediumContrast = Color(0xFF1F0F12)
    val onSurfaceDarkMediumContrast = Color(0xFFFFFFFF)
    val surfaceVariantDarkMediumContrast = Color(0xFF5B3F43)
    val onSurfaceVariantDarkMediumContrast = Color(0xFFFBD2D7)
    val outlineDarkMediumContrast = Color(0xFFCEA9AD)
    val outlineVariantDarkMediumContrast = Color(0xFFAA878C)
    val scrimDarkMediumContrast = Color(0xFF000000)
    val inverseSurfaceDarkMediumContrast = Color(0xFFFADBDE)
    val inverseOnSurfaceDarkMediumContrast = Color(0xFF372528)
    val inversePrimaryDarkMediumContrast = Color(0xFF920039)
    val surfaceDimDarkMediumContrast = Color(0xFF1F0F12)
    val surfaceBrightDarkMediumContrast = Color(0xFF543F42)
    val surfaceContainerLowestDarkMediumContrast = Color(0xFF110406)
    val surfaceContainerLowDarkMediumContrast = Color(0xFF2A191C)
    val surfaceContainerDarkMediumContrast = Color(0xFF352326)
    val surfaceContainerHighDarkMediumContrast = Color(0xFF412E30)
    val surfaceContainerHighestDarkMediumContrast = Color(0xFF4D383B)

    val primaryDarkHighContrast = Color(0xFFFFEBED)
    val onPrimaryDarkHighContrast = Color(0xFF000000)
    val primaryContainerDarkHighContrast = Color(0xFFFFACB9)
    val onPrimaryContainerDarkHighContrast = Color(0xFF200007)
    val secondaryDarkHighContrast = Color(0xFFFFEBEE)
    val onSecondaryDarkHighContrast = Color(0xFF000000)
    val secondaryContainerDarkHighContrast = Color(0xFFFFABBD)
    val onSecondaryContainerDarkHighContrast = Color(0xFF210009)
    val tertiaryDarkHighContrast = Color(0xFFFFECE9)
    val onTertiaryDarkHighContrast = Color(0xFF000000)
    val tertiaryContainerDarkHighContrast = Color(0xFFFFAEA3)
    val onTertiaryContainerDarkHighContrast = Color(0xFF220000)
    val errorDarkHighContrast = Color(0xFFFFECE9)
    val onErrorDarkHighContrast = Color(0xFF000000)
    val errorContainerDarkHighContrast = Color(0xFFFFAEA4)
    val onErrorContainerDarkHighContrast = Color(0xFF220001)
    val backgroundDarkHighContrast = Color(0xFF1F0F12)
    val onBackgroundDarkHighContrast = Color(0xFFFADBDE)
    val surfaceDarkHighContrast = Color(0xFF1F0F12)
    val onSurfaceDarkHighContrast = Color(0xFFFFFFFF)
    val surfaceVariantDarkHighContrast = Color(0xFF5B3F43)
    val onSurfaceVariantDarkHighContrast = Color(0xFFFFFFFF)
    val outlineDarkHighContrast = Color(0xFFFFEBED)
    val outlineVariantDarkHighContrast = Color(0xFFE0B9BE)
    val scrimDarkHighContrast = Color(0xFF000000)
    val inverseSurfaceDarkHighContrast = Color(0xFFFADBDE)
    val inverseOnSurfaceDarkHighContrast = Color(0xFF000000)
    val inversePrimaryDarkHighContrast = Color(0xFF920039)
    val surfaceDimDarkHighContrast = Color(0xFF1F0F12)
    val surfaceBrightDarkHighContrast = Color(0xFF604A4D)
    val surfaceContainerLowestDarkHighContrast = Color(0xFF000000)
    val surfaceContainerLowDarkHighContrast = Color(0xFF2C1B1E)
    val surfaceContainerDarkHighContrast = Color(0xFF3E2B2E)
    val surfaceContainerHighDarkHighContrast = Color(0xFF4A3639)
    val surfaceContainerHighestDarkHighContrast = Color(0xFF564144)

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