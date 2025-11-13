package io.nekohasekai.sagernet.compose

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.graphics.vector.AnimatedImageVector
import androidx.compose.animation.graphics.res.animatedVectorResource
import androidx.compose.animation.graphics.res.rememberAnimatedVectorPainter
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.bg.BaseService
import io.nekohasekai.sagernet.repository.repo
import io.nekohasekai.sagernet.ui.StringOrRes
import io.nekohasekai.sagernet.ui.VpnRequestActivity

@Composable
fun SagerFab(
    modifier: Modifier = Modifier,
    visible: Boolean = true,
    state: BaseService.State,
    showSnackbar: (message: StringOrRes) -> Unit,
) {
    val connector = rememberLauncherForActivityResult(VpnRequestActivity.StartService()) { failed ->
        if (failed) showSnackbar(StringOrRes.Res(R.string.vpn_permission_denied))
    }

    AnimatedVisibility(
        visible = visible,
        enter = scaleIn(),
        exit = scaleOut(),
    ) {
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
            TooltipBox(
                positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
                    TooltipAnchorPosition.Above,
                ),
                tooltip = { Text(stringResource(R.string.connect)) },
                state = rememberTooltipState(),
            ) {
                if (state == BaseService.State.Connected) {
                    Icon(
                        rememberVectorPainter(ImageVector.vectorResource(R.drawable.ic_service_busy)),
                        stringResource(R.string.connect),
                    )
                } else {
                    val animRes = when (state) {
                        BaseService.State.Connecting -> R.drawable.ic_service_connecting
                        BaseService.State.Stopping -> R.drawable.ic_service_stopping
                        else -> R.drawable.ic_service_stopped
                    }
                    key(animRes) {
                        val animatedVector = AnimatedImageVector.animatedVectorResource(animRes)
                        var atEnd by remember { mutableStateOf(false) }
                        LaunchedEffect(Unit) { atEnd = true }
                        Icon(
                            rememberAnimatedVectorPainter(animatedVector, atEnd),
                            stringResource(R.string.connect),
                        )
                    }
                }
            }
        }
    }
}
