package fr.husi.ui.tools

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.husi.core.remote.RemoteControlManager
import fr.husi.ui.remote.RemoteTargetMenuSection
import org.koin.compose.koinInject

@Composable
internal fun StunScreen(
    modifier: Modifier = Modifier,
    onBackPress: () -> Unit,
    onOpenRemoteControl: () -> Unit,
    remoteControl: RemoteControlManager = koinInject(),
    viewModel: StunScreenViewModel = viewModel {
        StunScreenViewModel(remoteControl = remoteControl)
    },
) {
    val targetConnected by remoteControl.targetConnected.collectAsStateWithLifecycle()
    val presenter =
        remember(viewModel) {
            object : StunPresenter {
                override val uiState = viewModel.uiState
                override val uiEvent = viewModel.uiEvent

                override fun setServer(value: String) {
                    viewModel.setServer(value)
                }

                override fun setOutboundTag(value: String) {
                    viewModel.setOutboundTag(value)
                }

                override fun doTest(value: Boolean) {
                    viewModel.doTest(value)
                }

                override fun cancel() {
                    viewModel.cancel()
                }
            }
        }
    StunScreenContent(
        viewModel = presenter,
        targetConnected = targetConnected,
        onBackPress = onBackPress,
        targetMenu = { dismiss ->
            RemoteTargetMenuSection(0, 1, onOpenRemoteControl, dismiss)
        },
        outboundSelector = { selected, select, modifier ->
            OutboundSelector(selected, select, modifier, remoteControl)
        },
        modifier = modifier,
    )
}
