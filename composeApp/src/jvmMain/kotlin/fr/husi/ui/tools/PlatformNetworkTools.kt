package fr.husi.ui.tools

import androidx.compose.runtime.Composable
import fr.husi.ui.NavRoutes

@Composable
internal expect fun PlatformNetworkTools(onOpenTool: (NavRoutes.ToolsPage) -> Unit)
