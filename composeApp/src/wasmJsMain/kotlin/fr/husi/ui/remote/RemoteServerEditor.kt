@file:OptIn(ExperimentalWasmJsInterop::class)

package fr.husi.ui.remote

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.husi.database.RemoteServer
import fr.husi.remote.RemoteState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

internal class RemoteServerEditor(private val id: Long?, private val remote: RemoteState) :
    ViewModel() {
    private val original = remote.servers.firstOrNull { it.id == id }
    val nameState = TextFieldState(original?.name.orEmpty())
    val urlState = TextFieldState(original?.url ?: sameOrigin())
    val secretState = TextFieldState(original?.secret.orEmpty())
    private var invalidUrl by mutableStateOf<String?>(null)
    private var test by mutableStateOf<RemoteServerTestState>(RemoteServerTestState.Idle)

    val state: RemoteServerEditUiState
        get() = RemoteServerEditUiState(
            name = nameState.text.toString(),
            url = urlState.text.toString(),
            secret = secretState.text.toString(),
            urlError = invalidUrl == urlState.text.toString(),
            test = test,
            isNew = original == null,
        )

    private var testJob: Job? = null
    private var saveJob: Job? = null

    init {
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            snapshotFlow { urlState.text.toString() to secretState.text.toString() }
                .drop(1)
                .collect {
                    testConnection(debounce = true)
                }
        }
    }

    fun setName(value: String) {
        nameState.setTextAndPlaceCursorAtEnd(value)
    }

    fun setUrl(value: String) {
        urlState.setTextAndPlaceCursorAtEnd(value)
    }

    fun setSecret(value: String) {
        secretState.setTextAndPlaceCursorAtEnd(value)
    }

    fun save(onSaved: (RemoteServer) -> Unit) {
        if (saveJob?.isActive == true) return
        val input = state
        val url = runCatching { remote.client.normalizeUrl(input.url) }.getOrNull()
        if (url == null) {
            invalidUrl = input.url
            return
        }
        val server = RemoteServer(
            id = id ?: 0L,
            userOrder = original?.userOrder ?: 0L,
            name = input.name.ifBlank { url },
            url = url,
            secret = input.secret,
        )
        saveJob = viewModelScope.launch {
            try {
                onSaved(remote.save(server))
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                remote.error = failure.message.orEmpty()
            }
        }
    }

    fun testConnection(debounce: Boolean = false) {
        testJob?.cancel()
        val input = state
        val url = runCatching { remote.client.normalizeUrl(input.url) }.getOrNull()
        if (url == null) {
            test = RemoteServerTestState.InvalidURL
            return
        }
        test = RemoteServerTestState.Testing
        testJob = viewModelScope.launch {
            if (debounce) {
                delay(500.milliseconds)
            }
            val result =
                try {
                    RemoteServerTestState.Success(remote.client.testConnection(url, input.secret))
                } catch (failure: Exception) {
                    if (failure is CancellationException) throw failure
                    RemoteServerTestState.Failure(failure.message.orEmpty())
                }
            test = result
        }
    }
}

/** The page's own origin: the API address when sing-box serves the dashboard itself. */
@JsFun("() => location.origin") private external fun sameOrigin(): String
