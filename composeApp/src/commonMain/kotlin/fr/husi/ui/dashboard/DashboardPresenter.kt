package fr.husi.ui.dashboard

import androidx.compose.foundation.text.input.TextFieldState
import kotlinx.coroutines.flow.StateFlow

internal interface DashboardPresenter {
    val uiState: StateFlow<DashboardState>
    val searchTextFieldState: TextFieldState
    val proxySetSearchTextFieldState: TextFieldState
    val proxyGroupSearchTextFieldState: TextFieldState
    suspend fun initialize(isConnected: Boolean)
    suspend fun resolveProcessInfo(process: String?, uid: Int): ProcessInfo?
    fun setDashboardActive(active: Boolean)
    fun togglePause()
    fun clearSearchQuery()
    fun toggleProxySetSearchMode()
    fun clearProxySetGlobalQuery()
    fun openProxyGroupSearch(id: String)
    fun closeProxyGroupSearch()
    fun clearProxySetSearch()
    fun setSortDescending(descending: Boolean)
    fun setSortMode(mode: Int)
    fun setProxySetOrder(order: Int)
    fun setDashboardWidgets(entries: List<DashboardWidgetEntry>)
    fun resetDashboardWidgets()
    fun setQueryActivate(queryActivate: Boolean)
    fun setQueryClosed(queryClosed: Boolean)
    fun selectConnection(uuid: String?)
    fun closeConnection(uuid: String)
    fun selectOutbound(groupName: String, tag: String)
    fun urlTestForSingle(tag: String)
    fun urlTestForGroup(id: String)
    fun detectIP()
    fun resetNetwork()
    fun setClashMode(mode: String)
    fun setSystemProxyEnabled(enabled: Boolean)
}
