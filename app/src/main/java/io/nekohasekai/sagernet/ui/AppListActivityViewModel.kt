package io.nekohasekai.sagernet.ui

import io.nekohasekai.sagernet.database.DataStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal class AppListActivityViewModel : AbstractAppListViewModel() {
    private class UiState(override val base: BaseAppListUiState = BaseAppListUiState()) : AbstractAppListUiState
    private val _uiState = MutableStateFlow<AbstractAppListUiState>(UiState())

    override val uiState = _uiState.asStateFlow()

    override suspend fun emitBaseState(baseState: BaseAppListUiState) {
        _uiState.emit(UiState(baseState))
    }

    override var packages: Set<String>
        get() = DataStore.routePackages
        set(value) {
            DataStore.routePackages = value
        }
}