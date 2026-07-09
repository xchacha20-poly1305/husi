package fr.husi.ui

import androidx.compose.runtime.Composable

@Composable
internal expect fun PlatformPluginPreferences(
    isExpert: Boolean,
    needRestart: () -> Unit
)
