package io.nekohasekai.sagernet.compose

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection

/**
 * Make content expand to the bottom of three-button navigation bar.
 *
 * WARNING: If there are interactive contents in the bottom,
 * make left enough space for interact!
 * (`Spacer(modifier = Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))`)
 */
@Composable
fun Modifier.paddingExceptBottom(paddingValues: PaddingValues): Modifier {
    val paddingExceptBottom = PaddingValues(
        top = paddingValues.calculateTopPadding(),
        start = paddingValues.calculateStartPadding(LocalLayoutDirection.current),
        end = paddingValues.calculateEndPadding(LocalLayoutDirection.current),
    )
    return padding(paddingExceptBottom)
}

/**
 * Different from [paddingExceptBottom], [paddingWithNavigation] usually used in
 * LazyColumn's contentPadding to make content avoid navigation bar.
 */
@Composable
fun PaddingValues.paddingWithNavigation(): PaddingValues {
    return PaddingValues(
        start = calculateStartPadding(LocalLayoutDirection.current),
        top = calculateTopPadding(),
        end = calculateEndPadding(LocalLayoutDirection.current),
        bottom = calculateBottomPadding() +
                WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
    )
}