package io.nekohasekai.sagernet.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.nekohasekai.sagernet.aidl.ISagerNetService
import io.nekohasekai.sagernet.aidl.ProxySet
import io.nekohasekai.sagernet.aidl.URLTestResult
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.ktx.onIoDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal data class ProxySetFragmentUiState(
    val proxySets: LinkedHashMap<String, ProxySetData> = LinkedHashMap(), // tag:proxySetData
)

internal data class ProxySetData(
    val proxySet: ProxySet = ProxySet(),
    var isExpanded: Boolean = false,
    val delays: Map<String, Short> = emptyMap(),
    val isTesting: Boolean = false
)

internal class ProxySetFragmentViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(ProxySetFragmentUiState())
    val uiState = _uiState.asStateFlow()

    companion object {
        private const val FRESH_INTERVAL = 1000L
    }

    private var service: ISagerNetService? = null
    private var job: Job? = null

    fun initialize(service: ISagerNetService) {
        this.service = service
        job = viewModelScope.launch {
            while (true) {
                updateState()
                delay(FRESH_INTERVAL)
            }
        }
    }

    private suspend fun updateState() {
        val service = service ?: return
        val newList = service.queryProxySet().map {
            val old = _uiState.value.proxySets[it.tag]
            ProxySetData(
                it,
                isExpanded = old?.isExpanded ?: false,
                isTesting = old?.isTesting ?: false,
                delays = old?.delays ?: emptyMap()
            )
        }
        val newSets = newList.associateByTo(LinkedHashMap(newList.size)) {
            it.proxySet.tag
        }
        _uiState.emit(_uiState.value.copy(proxySets = newSets))
    }

    fun stop() {
        job?.cancel()
        job = null
        service = null
    }

    fun performUrlTest(group: String) = viewModelScope.launch {
        if (_uiState.value.proxySets[group]?.isTesting == true) return@launch

        val currentSets = LinkedHashMap(_uiState.value.proxySets)
        val proxySetToTest = currentSets[group] ?: return@launch
        currentSets[group] = proxySetToTest.copy(isTesting = true)
        _uiState.emit(_uiState.value.copy(proxySets = currentSets))

        val result = onIoDispatcher {
            service?.groupURLTest(group, DataStore.connectionTestTimeout)
        }

        val finalSets = LinkedHashMap(_uiState.value.proxySets)
        val finalProxySet = finalSets[group] ?: return@launch
        finalSets[group] = finalProxySet.copy(
            delays = result?.data ?: finalProxySet.delays,
            isTesting = false
        )
        _uiState.emit(_uiState.value.copy(proxySets = finalSets))
    }

    fun updateDelays(group: String, result: URLTestResult) = viewModelScope.launch {
        @Suppress("UNCHECKED_CAST")
        val proxySets = _uiState.value.proxySets.clone() as LinkedHashMap<String, ProxySetData>
        val proxySet = proxySets[group] ?: return@launch
        proxySets[group] = proxySet.copy(
            delays = result.data,
        )
        _uiState.emit(_uiState.value.copy(proxySets = proxySets))
    }

        fun setExpanded(group: String, isExpanded: Boolean) = viewModelScope.launch {
        @Suppress("UNCHECKED_CAST")
        val proxySets = _uiState.value.proxySets.clone() as LinkedHashMap<String, ProxySetData>
        val proxySet = proxySets[group] ?: return@launch
        proxySets[group] = proxySet.copy(
            isExpanded = isExpanded,
        )
        _uiState.emit(_uiState.value.copy(proxySets = proxySets))
    }

    fun select(group: String, tag: String) = viewModelScope.launch {
        if (_uiState.value.proxySets[group]?.proxySet?.selected == tag) return@launch
        service?.groupSelect(group, tag)
    }
}