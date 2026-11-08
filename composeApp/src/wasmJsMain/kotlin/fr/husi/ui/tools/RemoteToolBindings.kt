package fr.husi.ui.tools

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import fr.husi.remote.RemoteState
import fr.husi.remote.items
import fr.husi.remote.text
import fr.husi.ui.LocalNavigator
import fr.husi.ui.RemoteRoute
import fr.husi.ui.remote.RemoteMenu
import kotlinx.coroutines.CancellationException
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@Composable
internal fun RemoteNetworkBinding() {
    val navigator = LocalNavigator.current
    NetworkToolsContent(
        onBackPress = { navigator.popBackStack() },
        onOpenStun = { navigator.navigateTo(RemoteRoute.Stun) },
        onOpenNetworkQuality = { navigator.navigateTo(RemoteRoute.NetworkQuality) },
    )
}

@Composable
internal fun RemoteStunBinding() {
    val remote = koinInject<RemoteState>()
    val presenter = koinViewModel<RemoteStunPresenter>()
    val navigator = LocalNavigator.current
    StunScreenContent(
        presenter,
        remote.connected,
        { navigator.popBackStack() },
        { RemoteMenu(1, it) },
        { value, select, modifier -> RemoteOutboundBinding(value, select, modifier) },
    )
}

@Composable
internal fun RemoteQualityBinding() {
    val remote = koinInject<RemoteState>()
    val presenter = koinViewModel<RemoteNetworkQualityPresenter>()
    val navigator = LocalNavigator.current
    NetworkQualityScreenContent(
        presenter,
        remote.connected,
        { navigator.popBackStack() },
        { RemoteMenu(1, it) },
        { value, select, modifier -> RemoteOutboundBinding(value, select, modifier) },
    )
}

@Composable
private fun RemoteOutboundBinding(value: String, select: (String) -> Unit, modifier: Modifier) {
    val remote = koinInject<RemoteState>()
    var tags by remember(remote.selected) { mutableStateOf(listOf("")) }
    LaunchedEffect(remote.selected) {
        try {
            remote.client.stream("SubscribeOutbounds").collect { update ->
                tags = listOf("") + update.items("outbounds").map { it.text("tag") }
            }
        } catch (failure: Exception) {
            if (failure is CancellationException) throw failure
            remote.error = failure.message.orEmpty()
        }
    }
    OutboundSelector(value, select, tags, modifier)
}
