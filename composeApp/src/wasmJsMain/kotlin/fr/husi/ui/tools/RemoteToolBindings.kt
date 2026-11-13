package fr.husi.ui.tools

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import fr.husi.compose.IconMaskColors
import fr.husi.compose.MaskedIcon
import fr.husi.compose.Preference
import fr.husi.compose.material3.Text
import fr.husi.compose.preferenceGroup
import fr.husi.ktx.Logs
import fr.husi.remote.RemoteState
import fr.husi.resources.Res
import fr.husi.resources.clash_dns
import fr.husi.resources.tools_network
import fr.husi.resources.wifi
import fr.husi.ui.LocalNavigator
import fr.husi.ui.RemoteRoute
import fr.husi.ui.remote.RemoteMenu
import fr.husi.ui.settings.SettingsPage
import kotlinx.coroutines.CancellationException
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@Composable
internal fun RemoteNetworkBinding() {
    val navigator = LocalNavigator.current
    val remote = koinInject<RemoteState>()
    if (remote.isClash) {
        SettingsPage(Res.string.tools_network, onBackPress = { navigator.popBackStack() }) {
            preferenceGroup {
                Preference(title = { Text(stringResource(Res.string.clash_dns)) },
                    icon = { MaskedIcon(Res.drawable.wifi, IconMaskColors.IconLightBlue) },
                    onClick = { navigator.navigateTo(RemoteRoute.Dns) })
            }
        }
        return
    }
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
            remote.client.service.subscribeOutbounds().collect { update ->
                tags = listOf("") + update.outbounds.map { it.tag }
            }
        } catch (failure: Exception) {
            if (failure is CancellationException) throw failure
            Logs.w("RemoteToolBindings operation failed", failure)
        }
    }
    OutboundSelector(value, select, tags, modifier)
}
