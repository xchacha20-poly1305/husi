@file:OptIn(ExperimentalLayoutApi::class)

package io.nekohasekai.sagernet.compose

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsIgnoringVisibility
import androidx.compose.foundation.layout.padding
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
    val paddingExceptBottom = PaddingValues(
        start = paddingValues.calculateStartPadding(LocalLayoutDirection.current),
        top = paddingValues.calculateTopPadding(),
        end = paddingValues.calculateEndPadding(LocalLayoutDirection.current),
    )
    return padding(paddingExceptBottom)
}

@Composable
fun extraBottomPadding(): PaddingValues {
    return PaddingValues(
        bottom = WindowInsets.navigationBarsIgnoringVisibility
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
    return PaddingValues(
        start = calculateStartPadding(LocalLayoutDirection.current),
        top = calculateTopPadding(),
        end = calculateEndPadding(LocalLayoutDirection.current),
        bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
    )
}