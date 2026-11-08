package fr.husi.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
internal expect fun AppListScreen(
    initialPackages: Set<String>,
    resultKey: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
)
