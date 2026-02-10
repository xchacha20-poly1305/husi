package fr.husi.compose

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.Composable

@Composable
actual fun navigationBarsAlwaysInsets(): WindowInsets = WindowInsets(0, 0, 0, 0)