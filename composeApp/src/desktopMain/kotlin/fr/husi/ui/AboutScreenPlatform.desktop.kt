package fr.husi.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

@Composable
internal actual fun rememberShouldRequestBatteryOptimizations(): Boolean = false

@Composable
internal actual fun rememberRequestIgnoreBatteryOptimizations(): () -> Unit = remember { {} }
