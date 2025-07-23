package io.nekohasekai.sagernet.ui.tools

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.nekohasekai.sagernet.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import libcore.Libcore

internal sealed interface StunUiState {
    object Idle : StunUiState
    object Doing : StunUiState
    data class Done(val result: String) : StunUiState
}

internal class StunActivityViewModel : ViewModel() {

    companion object {
        private const val STUN_SOFTWARE_NAME = "husi ${BuildConfig.VERSION_NAME}"
    }

    private val _uiState = MutableStateFlow<StunUiState>(StunUiState.Idle)
    val uiState = _uiState.asStateFlow()

    fun doTest(server: String, socks5: String?) {
        _uiState.value = StunUiState.Doing
        viewModelScope.launch(Dispatchers.IO) {
            val result = Libcore.stunTest(server, socks5, STUN_SOFTWARE_NAME)
            _uiState.update { StunUiState.Done(result) }
        }
    }
}
