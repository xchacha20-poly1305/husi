package io.nekohasekai.sagernet.compose

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.bg.BaseService
import io.nekohasekai.sagernet.repository.repo
import io.nekohasekai.sagernet.ui.StringOrRes
import io.nekohasekai.sagernet.ui.VpnRequestActivity

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SagerFab(
    modifier: Modifier = Modifier,
    state: BaseService.State,
    showProgress: Boolean,
    showSnackbar: (message: StringOrRes) -> Unit,
) {
    val connector = rememberLauncherForActivityResult(VpnRequestActivity.StartService()) { failed ->
        if (failed) showSnackbar(StringOrRes.Res(R.string.vpn_permission_denied))
    }

    Box(
        contentAlignment = Alignment.Center,
    ) {
        if (showProgress) CircularWavyProgressIndicator(
            modifier = Modifier.size(64.dp),
            stroke = Stroke(width = 4f),
        )
        FloatingActionButton(
            onClick = {
                if (state.canStop) {
                    repo.stopService()
                } else {
                    connector.launch(null)
                }
            },
            modifier = modifier.size(56.dp),
        ) {
            val icon = when (state) {
                BaseService.State.Connecting -> R.drawable.ic_service_connecting
                BaseService.State.Connected -> R.drawable.ic_service_connected
                BaseService.State.Stopping -> R.drawable.ic_service_stopping
                else -> R.drawable.ic_service_stopped
            }
            TooltipBox(
                positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
                    TooltipAnchorPosition.Above,
                ),
                tooltip = { Text(stringResource(R.string.connect)) },
                state = rememberTooltipState(),
            ) {
                Icon(ImageVector.vectorResource(icon), stringResource(R.string.connect))
            }
        }
    }
}