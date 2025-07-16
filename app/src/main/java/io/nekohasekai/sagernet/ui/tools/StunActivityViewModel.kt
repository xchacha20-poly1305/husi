package io.nekohasekai.sagernet.ui.tools

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.nekohasekai.sagernet.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import libcore.Libcore

sealed class StunUiState {
    object Idle : StunUiState()
    object Doing : StunUiState()
    data class Done(val result: String) : StunUiState()
}

class StunActivityViewModel : ViewModel() {

    companion object {
        private const val STUN_SOFTWARE_NAME = "husi ${BuildConfig.VERSION_NAME}"
    }

    private val _uiState = MutableLiveData<StunUiState>(StunUiState.Idle)
    val uiState: LiveData<StunUiState> = _uiState

    fun doTest(server: String, socks5: String?) {
        _uiState.value = StunUiState.Doing
        viewModelScope.launch(Dispatchers.IO) {
            val result =  Libcore.stunTest(server, socks5, STUN_SOFTWARE_NAME)
            _uiState.postValue(StunUiState.Done(result))
        }
    }
}
