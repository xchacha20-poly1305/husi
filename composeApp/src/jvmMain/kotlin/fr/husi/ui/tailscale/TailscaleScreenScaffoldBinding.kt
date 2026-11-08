package fr.husi.ui.tailscale

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import fr.husi.tailscale.TailscaleUiEvent
import fr.husi.ui.LocalSnackbarEmitter
import fr.husi.ui.StringOrRes
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch

@Composable
internal fun CollectTailscaleEvents(
    events: SharedFlow<TailscaleUiEvent>,
    closed: SharedFlow<Unit>,
    onBackPress: () -> Unit,
) {
    val snackbar = LocalSnackbarEmitter.current
    val back = rememberUpdatedState(onBackPress)
    LaunchedEffect(events, closed) {
        launch {
            closed.collect { back.value() }
        }
        launch {
            events.collect { event ->
                when (event) {
                    is TailscaleUiEvent.Message -> snackbar.show(StringOrRes.Direct(event.message))
                }
            }
        }
    }
}

