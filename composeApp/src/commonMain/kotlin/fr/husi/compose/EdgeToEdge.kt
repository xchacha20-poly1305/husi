@file:OptIn(ExperimentalLayoutApi::class)

package fr.husi.compose

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection

/**
 * Make content expand to the bottom of three-button navigation bar.
 *
 * WARNING: If there are interactive contents in the bottom,
 * make left enough space for interact! (Use [extraBottomPadding])
 */
@Composable
fun Modifier.paddingExceptBottom(paddingValues: PaddingValues): Modifier {
    return padding(
        PaddingValues(
            start = paddingValues.calculateStartPadding(LocalLayoutDirection.current),
            top = paddingValues.calculateTopPadding(),
            end = paddingValues.calculateEndPadding(LocalLayoutDirection.current),
        ),
    )
}

@Composable
fun Modifier.paddingHorizontal(paddingValues: PaddingValues): Modifier {
    return padding(
        PaddingValues(
            start = paddingValues.calculateStartPadding(LocalLayoutDirection.current),
            end = paddingValues.calculateEndPadding(LocalLayoutDirection.current),
        ),
    )
}

@Composable
fun extraBottomPadding(): PaddingValues {
    return PaddingValues(
        bottom = navigationBarsAlwaysInsets()
            .asPaddingValues()
            .calculateBottomPadding(),
    )
}

/**
 * Different from [paddingExceptBottom], [withNavigation] usually used in
 * LazyColumn's contentPadding to make content avoid navigation bar.
 */
@Composable
fun PaddingValues.withNavigation(): PaddingValues {
    return this + PaddingValues(
        start = calculateStartPadding(LocalLayoutDirection.current),
        end = calculateEndPadding(LocalLayoutDirection.current),
        bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
    )
}

// FIXME: This API is added back in compose 1.11.0-beta01.
@Stable
operator fun PaddingValues.plus(other: PaddingValues): PaddingValues =
    object : PaddingValues {
        override fun calculateLeftPadding(layoutDirection: LayoutDirection): Dp =
            this@plus.calculateLeftPadding(layoutDirection) +
                    other.calculateLeftPadding(layoutDirection)

        override fun calculateTopPadding(): Dp =
            this@plus.calculateTopPadding() + other.calculateTopPadding()

        override fun calculateRightPadding(layoutDirection: LayoutDirection): Dp =
            this@plus.calculateRightPadding(layoutDirection) +
                    other.calculateRightPadding(layoutDirection)

        override fun calculateBottomPadding(): Dp =
            this@plus.calculateBottomPadding() + other.calculateBottomPadding()
    }