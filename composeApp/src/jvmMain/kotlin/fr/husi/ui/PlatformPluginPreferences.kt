package fr.husi.ui

import androidx.compose.foundation.lazy.LazyListScope

internal expect fun LazyListScope.platformPluginPreferences(
    isCustomPluginPrefixEnabled: Boolean,
    needRestart: () -> Unit,
)
