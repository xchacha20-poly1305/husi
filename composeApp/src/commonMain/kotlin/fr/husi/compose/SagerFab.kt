package fr.husi.compose

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import fr.husi.bg.ServiceState
import fr.husi.repository.repo
import fr.husi.resources.*
import fr.husi.ui.StringOrRes
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource

@Composable
fun SagerFab(
    modifier: Modifier = Modifier,
    visible: Boolean = true,
    state: ServiceState,
    showSnackbar: (message: StringOrRes) -> Unit,
    onSizeChanged: ((Int) -> Unit)? = null,
) {
    val connector = rememberVpnServiceLauncher {
        showSnackbar(StringOrRes.Res(Res.string.vpn_permission_denied))
    }

    AnimatedVisibility(
        visible = visible,
        enter = scaleIn(),
        exit = scaleOut(),
        modifier = Modifier.onSizeChanged { onSizeChanged?.invoke(it.height) },
    ) {
        FloatingActionButton(
            onClick = {
                if (state.canStop) {
                    repo.stopService()
                } else {
                    connector()
                }
            },
            modifier = modifier.size(56.dp),
        ) {
            TooltipBox(
                positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
                    TooltipAnchorPosition.Above,
                ),
                tooltip = {
                    PlainTooltip {
                        Text(stringResource(Res.string.connect))
                    }
                },
                state = rememberTooltipState(),
            ) {
                if (state == ServiceState.Connected) {
                    Icon(
                        rememberVectorPainter(vectorResource(Res.drawable.ic_service_busy)),
                        stringResource(Res.string.connect),
                    )
                } else {
                    val animKey = when (state) {
                        ServiceState.Connecting -> 0
                        ServiceState.Stopping -> 1
                        else -> 2
                    }
                    key(animKey) {
                        AnimatedServiceIcon(state, stringResource(Res.string.connect))
                    }
                }
            }
        }
    }
}
