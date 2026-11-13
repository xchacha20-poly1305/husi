package fr.husi.ui.remote

import androidx.compose.runtime.Immutable
import fr.husi.database.RemoteBackend

/** Outcome of the connection test that runs against the edited server. */
@Immutable
sealed interface RemoteServerTestState {
    /** Nothing to test yet: the address is still empty. */
    data object Idle : RemoteServerTestState

    data object Testing : RemoteServerTestState

    /** The address cannot be parsed, so no request was made. */
    data object InvalidURL : RemoteServerTestState

    data class Success(val version: String) : RemoteServerTestState

    data class Failure(val message: String) : RemoteServerTestState
}

@Immutable
data class RemoteServerEditUiState(
    val backend: RemoteBackend? = null,
    val name: String = "",
    val url: String = "",
    val secret: String = "",
    val urlError: Boolean = false,
    val test: RemoteServerTestState = RemoteServerTestState.Idle,
    val isNew: Boolean = true,
)
