package io.nekohasekai.sagernet.compose.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** 终岁端正 */
object Brown {
    val primaryLight = Color(0xFF5F3E32)
    val onPrimaryLight = Color(0xFFFFFFFF)
    val primaryContainerLight = Color(0xFF795548)
    val onPrimaryContainerLight = Color(0xFFFDCDBC)
    val secondaryLight = Color(0xFF442A22)
    val onSecondaryLight = Color(0xFFFFFFFF)
    val secondaryContainerLight = Color(0xFF5D4037)
    val onSecondaryContainerLight = Color(0xFFD4ADA1)
    val tertiaryLight = Color(0xFF271310)
    val onTertiaryLight = Color(0xFFFFFFFF)
    val tertiaryContainerLight = Color(0xFF3E2723)
    val onTertiaryContainerLight = Color(0xFFAE8D87)
    val errorLight = Color(0xFFBA1A1A)
    val onErrorLight = Color(0xFFFFFFFF)
    val errorContainerLight = Color(0xFFFFDAD6)
    val onErrorContainerLight = Color(0xFF93000A)
    val backgroundLight = Color(0xFFFFF8F6)
    val onBackgroundLight = Color(0xFF1E1B1A)
    val surfaceLight = Color(0xFFFFF8F6)
    val onSurfaceLight = Color(0xFF1E1B1A)
    val surfaceVariantLight = Color(0xFFF1DFD9)
    val onSurfaceVariantLight = Color(0xFF504440)
    val outlineLight = Color(0xFF82746F)
    val outlineVariantLight = Color(0xFFD4C3BD)
    val scrimLight = Color(0xFF000000)
    val inverseSurfaceLight = Color(0xFF342F2E)
    val inverseOnSurfaceLight = Color(0xFFF8EFEC)
    val inversePrimaryLight = Color(0xFFEBBCAC)
    val surfaceDimLight = Color(0xFFE1D8D6)
    val surfaceBrightLight = Color(0xFFFFF8F6)
    val surfaceContainerLowestLight = Color(0xFFFFFFFF)
    val surfaceContainerLowLight = Color(0xFFFBF2EF)
    val surfaceContainerLight = Color(0xFFF5ECE9)
    val surfaceContainerHighLight = Color(0xFFEFE6E4)
    val surfaceContainerHighestLight = Color(0xFFE9E1DE)

    val primaryLightMediumContrast = Color(0xFF4D2F23)
    val onPrimaryLightMediumContrast = Color(0xFFFFFFFF)
    val primaryContainerLightMediumContrast = Color(0xFF795548)
    val onPrimaryContainerLightMediumContrast = Color(0xFFFFFFFF)
    val secondaryLightMediumContrast = Color(0xFF442A22)
    val onSecondaryLightMediumContrast = Color(0xFFFFFFFF)
    val secondaryContainerLightMediumContrast = Color(0xFF5D4037)
    val onSecondaryContainerLightMediumContrast = Color(0xFFFFD8CD)
    val tertiaryLightMediumContrast = Color(0xFF271310)
    val onTertiaryLightMediumContrast = Color(0xFFFFFFFF)
    val tertiaryContainerLightMediumContrast = Color(0xFF3E2723)
    val onTertiaryContainerLightMediumContrast = Color(0xFFD6B1AB)
    val errorLightMediumContrast = Color(0xFF740006)
    val onErrorLightMediumContrast = Color(0xFFFFFFFF)
    val errorContainerLightMediumContrast = Color(0xFFCF2C27)
    val onErrorContainerLightMediumContrast = Color(0xFFFFFFFF)
    val backgroundLightMediumContrast = Color(0xFFFFF8F6)
    val onBackgroundLightMediumContrast = Color(0xFF1E1B1A)
    val surfaceLightMediumContrast = Color(0xFFFFF8F6)
    val onSurfaceLightMediumContrast = Color(0xFF14100F)
    val surfaceVariantLightMediumContrast = Color(0xFFF1DFD9)
    val onSurfaceVariantLightMediumContrast = Color(0xFF3F3430)
    val outlineLightMediumContrast = Color(0xFF5C504B)
    val outlineVariantLightMediumContrast = Color(0xFF786A65)
    val scrimLightMediumContrast = Color(0xFF000000)
    val inverseSurfaceLightMediumContrast = Color(0xFF342F2E)
    val inverseOnSurfaceLightMediumContrast = Color(0xFFF8EFEC)
    val inversePrimaryLightMediumContrast = Color(0xFFEBBCAC)
    val surfaceDimLightMediumContrast = Color(0xFFCDC5C2)
    val surfaceBrightLightMediumContrast = Color(0xFFFFF8F6)
    val surfaceContainerLowestLightMediumContrast = Color(0xFFFFFFFF)
    val surfaceContainerLowLightMediumContrast = Color(0xFFFBF2EF)
    val surfaceContainerLightMediumContrast = Color(0xFFEFE6E4)
    val surfaceContainerHighLightMediumContrast = Color(0xFFE4DBD9)
    val surfaceContainerHighestLightMediumContrast = Color(0xFFD8D0CD)

    val primaryLightHighContrast = Color(0xFF41251A)
    val onPrimaryLightHighContrast = Color(0xFFFFFFFF)
    val primaryContainerLightHighContrast = Color(0xFF624135)
    val onPrimaryContainerLightHighContrast = Color(0xFFFFFFFF)
    val secondaryLightHighContrast = Color(0xFF3F261E)
    val onSecondaryLightHighContrast = Color(0xFFFFFFFF)
    val secondaryContainerLightHighContrast = Color(0xFF5D4037)
    val onSecondaryContainerLightHighContrast = Color(0xFFFFFFFF)
    val tertiaryLightHighContrast = Color(0xFF271310)
    val onTertiaryLightHighContrast = Color(0xFFFFFFFF)
    val tertiaryContainerLightHighContrast = Color(0xFF3E2723)
    val onTertiaryContainerLightHighContrast = Color(0xFFFFE0DA)
    val errorLightHighContrast = Color(0xFF600004)
    val onErrorLightHighContrast = Color(0xFFFFFFFF)
    val errorContainerLightHighContrast = Color(0xFF98000A)
    val onErrorContainerLightHighContrast = Color(0xFFFFFFFF)
    val backgroundLightHighContrast = Color(0xFFFFF8F6)
    val onBackgroundLightHighContrast = Color(0xFF1E1B1A)
    val surfaceLightHighContrast = Color(0xFFFFF8F6)
    val onSurfaceLightHighContrast = Color(0xFF000000)
    val surfaceVariantLightHighContrast = Color(0xFFF1DFD9)
    val onSurfaceVariantLightHighContrast = Color(0xFF000000)
    val outlineLightHighContrast = Color(0xFF342A26)
    val outlineVariantLightHighContrast = Color(0xFF534642)
    val scrimLightHighContrast = Color(0xFF000000)
    val inverseSurfaceLightHighContrast = Color(0xFF342F2E)
    val inverseOnSurfaceLightHighContrast = Color(0xFFFFFFFF)
    val inversePrimaryLightHighContrast = Color(0xFFEBBCAC)
    val surfaceDimLightHighContrast = Color(0xFFBFB7B5)
    val surfaceBrightLightHighContrast = Color(0xFFFFF8F6)
    val surfaceContainerLowestLightHighContrast = Color(0xFFFFFFFF)
    val surfaceContainerLowLightHighContrast = Color(0xFFF8EFEC)
    val surfaceContainerLightHighContrast = Color(0xFFE9E1DE)
    val surfaceContainerHighLightHighContrast = Color(0xFFDBD3D0)
    val surfaceContainerHighestLightHighContrast = Color(0xFFCDC5C2)

    val primaryDark = Color(0xFFEBBCAC)
    val onPrimaryDark = Color(0xFF46291E)
    val primaryContainerDark = Color(0xFF795548)
    val onPrimaryContainerDark = Color(0xFFFDCDBC)
    val secondaryDark = Color(0xFFE7BDB1)
    val onSecondaryDark = Color(0xFF442A22)
    val secondaryContainerDark = Color(0xFF5D4037)
    val onSecondaryContainerDark = Color(0xFFD4ADA1)
    val tertiaryDark = Color(0xFFE3BEB8)
    val onTertiaryDark = Color(0xFF422A26)
    val tertiaryContainerDark = Color(0xFF3E2723)
    val onTertiaryContainerDark = Color(0xFFAE8D87)
    val errorDark = Color(0xFFFFB4AB)
    val onErrorDark = Color(0xFF690005)
    val errorContainerDark = Color(0xFF93000A)
    val onErrorContainerDark = Color(0xFFFFDAD6)
    val backgroundDark = Color(0xFF161311)
    val onBackgroundDark = Color(0xFFE9E1DE)
    val surfaceDark = Color(0xFF161311)
    val onSurfaceDark = Color(0xFFE9E1DE)
    val surfaceVariantDark = Color(0xFF504440)
    val onSurfaceVariantDark = Color(0xFFD4C3BD)
    val outlineDark = Color(0xFF9D8D88)
    val outlineVariantDark = Color(0xFF504440)
    val scrimDark = Color(0xFF000000)
    val inverseSurfaceDark = Color(0xFFE9E1DE)
    val inverseOnSurfaceDark = Color(0xFF342F2E)
    val inversePrimaryDark = Color(0xFF7A5649)
    val surfaceDimDark = Color(0xFF161311)
    val surfaceBrightDark = Color(0xFF3D3837)
    val surfaceContainerLowestDark = Color(0xFF110D0C)
    val surfaceContainerLowDark = Color(0xFF1E1B1A)
    val surfaceContainerDark = Color(0xFF231F1D)
    val surfaceContainerHighDark = Color(0xFF2D2928)
    val surfaceContainerHighestDark = Color(0xFF383432)

    val primaryDarkMediumContrast = Color(0xFFFFD3C4)
    val onPrimaryDarkMediumContrast = Color(0xFF3A1E14)
    val primaryContainerDarkMediumContrast = Color(0xFFB18778)
    val onPrimaryContainerDarkMediumContrast = Color(0xFF000000)
    val secondaryDarkMediumContrast = Color(0xFFFED3C6)
    val onSecondaryDarkMediumContrast = Color(0xFF381F18)
    val secondaryContainerDarkMediumContrast = Color(0xFFAD887D)
    val onSecondaryContainerDarkMediumContrast = Color(0xFF000000)
    val tertiaryDarkMediumContrast = Color(0xFFFAD4CD)
    val onTertiaryDarkMediumContrast = Color(0xFF36201C)
    val tertiaryContainerDarkMediumContrast = Color(0xFFAA8983)
    val onTertiaryContainerDarkMediumContrast = Color(0xFF000000)
    val errorDarkMediumContrast = Color(0xFFFFD2CC)
    val onErrorDarkMediumContrast = Color(0xFF540003)
    val errorContainerDarkMediumContrast = Color(0xFFFF5449)
    val onErrorContainerDarkMediumContrast = Color(0xFF000000)
    val backgroundDarkMediumContrast = Color(0xFF161311)
    val onBackgroundDarkMediumContrast = Color(0xFFE9E1DE)
    val surfaceDarkMediumContrast = Color(0xFF161311)
    val onSurfaceDarkMediumContrast = Color(0xFFFFFFFF)
    val surfaceVariantDarkMediumContrast = Color(0xFF504440)
    val onSurfaceVariantDarkMediumContrast = Color(0xFFEBD8D3)
    val outlineDarkMediumContrast = Color(0xFFBFAEA9)
    val outlineVariantDarkMediumContrast = Color(0xFF9D8D88)
    val scrimDarkMediumContrast = Color(0xFF000000)
    val inverseSurfaceDarkMediumContrast = Color(0xFFE9E1DE)
    val inverseOnSurfaceDarkMediumContrast = Color(0xFF2D2928)
    val inversePrimaryDarkMediumContrast = Color(0xFF614034)
    val surfaceDimDarkMediumContrast = Color(0xFF161311)
    val surfaceBrightDarkMediumContrast = Color(0xFF484342)
    val surfaceContainerLowestDarkMediumContrast = Color(0xFF090706)
    val surfaceContainerLowDarkMediumContrast = Color(0xFF211D1C)
    val surfaceContainerDarkMediumContrast = Color(0xFF2B2726)
    val surfaceContainerHighDarkMediumContrast = Color(0xFF363230)
    val surfaceContainerHighestDarkMediumContrast = Color(0xFF413D3B)

    val primaryDarkHighContrast = Color(0xFFFFECE6)
    val onPrimaryDarkHighContrast = Color(0xFF000000)
    val primaryContainerDarkHighContrast = Color(0xFFE7B8A8)
    val onPrimaryContainerDarkHighContrast = Color(0xFF1A0501)
    val secondaryDarkHighContrast = Color(0xFFFFECE7)
    val onSecondaryDarkHighContrast = Color(0xFF000000)
    val secondaryContainerDarkHighContrast = Color(0xFFE2BAAD)
    val onSecondaryContainerDarkHighContrast = Color(0xFF180602)
    val tertiaryDarkHighContrast = Color(0xFFFFECE8)
    val onTertiaryDarkHighContrast = Color(0xFF000000)
    val tertiaryContainerDarkHighContrast = Color(0xFFDFBAB4)
    val onTertiaryContainerDarkHighContrast = Color(0xFF170705)
    val errorDarkHighContrast = Color(0xFFFFECE9)
    val onErrorDarkHighContrast = Color(0xFF000000)
    val errorContainerDarkHighContrast = Color(0xFFFFAEA4)
    val onErrorContainerDarkHighContrast = Color(0xFF220001)
    val backgroundDarkHighContrast = Color(0xFF161311)
    val onBackgroundDarkHighContrast = Color(0xFFE9E1DE)
    val surfaceDarkHighContrast = Color(0xFF161311)
    val onSurfaceDarkHighContrast = Color(0xFFFFFFFF)
    val surfaceVariantDarkHighContrast = Color(0xFF504440)
    val onSurfaceVariantDarkHighContrast = Color(0xFFFFFFFF)
    val outlineDarkHighContrast = Color(0xFFFFECE6)
    val outlineVariantDarkHighContrast = Color(0xFFD0BFB9)
    val scrimDarkHighContrast = Color(0xFF000000)
    val inverseSurfaceDarkHighContrast = Color(0xFFE9E1DE)
    val inverseOnSurfaceDarkHighContrast = Color(0xFF000000)
    val inversePrimaryDarkHighContrast = Color(0xFF614034)
    val surfaceDimDarkHighContrast = Color(0xFF161311)
    val surfaceBrightDarkHighContrast = Color(0xFF544F4D)
    val surfaceContainerLowestDarkHighContrast = Color(0xFF000000)
    val surfaceContainerLowDarkHighContrast = Color(0xFF231F1D)
    val surfaceContainerDarkHighContrast = Color(0xFF342F2E)
    val surfaceContainerHighDarkHighContrast = Color(0xFF3F3A39)
    val surfaceContainerHighestDarkHighContrast = Color(0xFF4B4644)


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