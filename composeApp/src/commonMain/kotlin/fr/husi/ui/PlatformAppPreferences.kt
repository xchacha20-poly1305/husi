package fr.husi.ui

import androidx.compose.foundation.lazy.LazyListScope

internal expect fun LazyListScope.appSelectPreference(
    packages: Set<String>,
    onSelectApps: (Set<String>) -> Unit,
)

internal expect fun LazyListScope.proxyAppsPreferences(
    openAppManager: () -> Unit,
)
