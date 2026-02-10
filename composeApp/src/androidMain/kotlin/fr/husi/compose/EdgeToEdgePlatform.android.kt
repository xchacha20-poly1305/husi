package fr.husi.compose

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBarsIgnoringVisibility
import androidx.compose.runtime.Composable

@OptIn(ExperimentalLayoutApi::class)
@Composable
actual fun navigationBarsAlwaysInsets(): WindowInsets =
    WindowInsets.navigationBarsIgnoringVisibility