package fr.husi.ui

import androidx.compose.foundation.lazy.LazyListScope

internal expect fun LazyListScope.platformPluginPreferences(
    isExpert: Boolean,
    needRestart: () -> Unit
)
