package io.nekohasekai.sagernet.ui.tools

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.nekohasekai.sagernet.BuildConfig
import io.nekohasekai.sagernet.ktx.currentSocks5
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import libcore.Libcore

@Immutable
internal data class StunActivityUiState(
    val server: String = "stun.voipgate.com:3478",
    val proxy: String = "",
    val isDoing: Boolean = false,
    val result: String = "",
)

@Stable
internal class StunActivityViewModel : ViewModel() {

    companion object {
        private const val STUN_SOFTWARE_NAME = "husi ${BuildConfig.VERSION_NAME}"
    }

    private val _uiState = MutableStateFlow(StunActivityUiState())
    val uiState = _uiState.asStateFlow()

    fun initialize() {
        _uiState.update {
            it.copy(proxy = currentSocks5()?.string.orEmpty())
        }
    }

    fun doTest() = viewModelScope.launch(Dispatchers.IO) {
        val uiState = _uiState.value
        _uiState.update { it.copy(isDoing = true) }
        val result = Libcore.stunTest(uiState.server, uiState.proxy, STUN_SOFTWARE_NAME)
        _uiState.update {
            it.copy(
                isDoing = false,
                result = result,
            )
        }
    }

    fun setServer(server: String) {
        _uiState.update { it.copy(server = server) }
    }

    fun setProxy(proxy: String) {
        _uiState.update { it.copy(proxy = proxy) }
    }
}
