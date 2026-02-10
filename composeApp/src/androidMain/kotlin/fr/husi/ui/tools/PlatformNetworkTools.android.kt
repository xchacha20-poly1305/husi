package fr.husi.ui.tools

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.stringResource
import fr.husi.ui.NavRoutes
import fr.husi.resources.*

@Composable
internal actual fun PlatformNetworkTools(onOpenTool: (NavRoutes.ToolsPage) -> Unit) {
    ActivityCard(
        title = stringResource(Res.string.scan_vpn_app),
        description = stringResource(Res.string.scan_vpn_app_introduce),
        launch = { onOpenTool(NavRoutes.ToolsPage.VPNScanner) },
    )
}
