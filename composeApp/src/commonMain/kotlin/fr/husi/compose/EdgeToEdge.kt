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
import androidx.compose.foundation.layout.plus
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection

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
        )
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