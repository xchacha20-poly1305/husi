package fr.husi.ui

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.ProvidedValue
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import fr.husi.ktx.showAndDismissOld
import fr.husi.repository.resolveRepository
import fr.husi.resources.Res
import fr.husi.resources.ok
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch

@Immutable
class SnackbarMessage(
    val message: StringOrRes,
    val actionLabel: StringOrRes? = null,
    val onResult: ((SnackbarResult) -> Unit)? = null,
)

@Stable
class SnackbarEmitter {
    val messages: SharedFlow<SnackbarMessage>
        field = MutableSharedFlow(
            extraBufferCapacity = 8,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )

    fun show(message: StringOrRes) {
        messages.tryEmit(SnackbarMessage(message))
    }

    fun show(
        message: StringOrRes,
        actionLabel: StringOrRes,
        onResult: (SnackbarResult) -> Unit,
    ) {
        messages.tryEmit(SnackbarMessage(message, actionLabel, onResult))
    }
}

object LocalSnackbarEmitter {
    private val LocalSnackbarEmitter: ProvidableCompositionLocal<SnackbarEmitter?> =
        compositionLocalOf { null }

    val current: SnackbarEmitter
        @Composable
        get() = requireNotNull(LocalSnackbarEmitter.current) { "No SnackbarEmitter has been provided" }

    infix fun provides(
        emitter: SnackbarEmitter,
    ): ProvidedValue<SnackbarEmitter?> {
        return LocalSnackbarEmitter.provides(emitter)
    }
}

/**
 * Shows every [SnackbarEmitter.messages] on [hostState].
 *
 * Messages without an action get an OK action so they can be dismissed early.
 */
@Composable
fun SnackbarEmitterEffect(
    emitter: SnackbarEmitter,
    hostState: SnackbarHostState,
) {
    LaunchedEffect(emitter, hostState) {
        emitter.messages.collect { snackbarMessage ->
            this@LaunchedEffect.launch {
                val result = hostState.showAndDismissOld(
                    message = getStringOrRes(snackbarMessage.message),
                    actionLabel = snackbarMessage.actionLabel?.let { getStringOrRes(it) }
                        ?: resolveRepository().getString(Res.string.ok),
                    duration = SnackbarDuration.Short,
                )
                snackbarMessage.onResult?.invoke(result)
            }
        }
    }
}
