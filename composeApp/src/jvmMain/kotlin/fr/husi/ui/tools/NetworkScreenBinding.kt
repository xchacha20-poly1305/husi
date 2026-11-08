package fr.husi.ui.tools

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import fr.husi.resources.Res
import fr.husi.resources.get_cert
import fr.husi.resources.get_cert_summary
import fr.husi.resources.rule_set_match
import fr.husi.ui.NavRoutes
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun NetworkScreen(
    modifier: Modifier = Modifier,
    onBackPress: () -> Unit,
    onOpenTool: (NavRoutes.ToolsPage) -> Unit,
) {
    NetworkToolsContent(
        onBackPress = onBackPress,
        onOpenStun = { onOpenTool(NavRoutes.ToolsPage.Stun) },
        onOpenNetworkQuality = { onOpenTool(NavRoutes.ToolsPage.NetworkQuality) },
        modifier = modifier,
        additionalTools = {
            ActivityCard(
                title = stringResource(Res.string.get_cert),
                description = stringResource(Res.string.get_cert_summary),
                launch = { onOpenTool(NavRoutes.ToolsPage.GetCert) },
            )
            PlatformNetworkTools(onOpenTool)
        },
        trailingTools = {
            ActivityCard(
                title = stringResource(Res.string.rule_set_match),
                description = "",
                launch = { onOpenTool(NavRoutes.ToolsPage.RuleSetMatch) },
            )
        },
    )
}
