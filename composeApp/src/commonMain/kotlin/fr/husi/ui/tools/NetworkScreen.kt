package fr.husi.ui.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp
import fr.husi.compose.extraBottomPadding
import fr.husi.compose.rememberScrollHideState
import fr.husi.ui.NavRoutes
import fr.husi.resources.*

@Composable
internal fun NetworkScreen(
    modifier: Modifier = Modifier,
    onVisibleChange: (Boolean) -> Unit,
    onOpenTool: (NavRoutes.ToolsPage) -> Unit,
) {
    val scrollState = rememberScrollState()
    val visible by rememberScrollHideState(scrollState)

    LaunchedEffect(visible) {
        onVisibleChange(visible)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(extraBottomPadding())
            .padding(horizontal = 16.dp),
    ) {
        Spacer(modifier = Modifier.height(16.dp))
        ActivityCard(
            title = stringResource(Res.string.stun_test),
            description = stringResource(Res.string.stun_test_summary),
            launch = {
                onOpenTool(NavRoutes.ToolsPage.Stun)
            },
        )
        ActivityCard(
            title = stringResource(Res.string.get_cert),
            description = stringResource(Res.string.get_cert_summary),
            launch = {
                onOpenTool(NavRoutes.ToolsPage.GetCert)
            },
        )
        PlatformNetworkTools(onOpenTool)
        ActivityCard(
            title = stringResource(Res.string.speed_test),
            description = "",
            launch = {
                onOpenTool(NavRoutes.ToolsPage.SpeedTest)
            },
        )
        ActivityCard(
            title = stringResource(Res.string.rule_set_match),
            description = "",
            launch = {
                onOpenTool(NavRoutes.ToolsPage.RuleSetMatch)
            },
        )
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
internal fun ActivityCard(
    modifier: Modifier = Modifier,
    title: String,
    description: String,
    launch: () -> Unit,
) {
    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
            )
            if (description.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                Button(
                    onClick = launch,
                ) {
                    Text(stringResource(Res.string.start))
                }
            }
        }
    }
}
