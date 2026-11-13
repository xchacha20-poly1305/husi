package fr.husi.ui.dashboard

import androidx.compose.runtime.Immutable
import fr.husi.TrafficSortMode
import kotlin.experimental.and

@Immutable
data class SystemProxyState(
    val enabled: Boolean,
    val mixedPort: Int,
    val hasInboundAuth: Boolean,
)

@Immutable
data class DashboardState(
    // toolbar
    val isPause: Boolean = false,
    val sortMode: Int = TrafficSortMode.START,
    val isDescending: Boolean = false,
    val queryOptions: Byte = SHOW_TRACKER_ACTIVELY,
    val isSearchingConnections: Boolean = false,

    val txTotal: Long = 0,
    val rxTotal: Long = 0,
    val memory: Long = 0,
    val goroutines: Int = 0,
    val supportsGoroutines: Boolean = true,
    val memoryAvailable: Boolean = true,
    val txRateProxy: Long = 0,
    val rxRateProxy: Long = 0,
    val txRateDirect: Long = 0,
    val rxRateDirect: Long = 0,
    val proxySpeedHistory: List<Float> = idleSpeedHistory(),
    val directSpeedHistory: List<Float> = idleSpeedHistory(),
    val ipProbe: IPProbeState = IPProbeState(),
    val ipv4: String? = null,
    val ipv6: String? = null,
    val selectedClashMode: String = "",
    val clashModes: List<String> = emptyList(),
    val systemProxy: SystemProxyState? = null,
    val networkInterfaces: List<NetworkInterfaceInfo> = emptyList(),

    val connections: List<ConnectionDetailState> = emptyList(),
    val activeConnectionCount: Int = 0,
    val selectedConnection: ConnectionDetailState? = null,

    val proxySets: List<ProxySet> = emptyList(),
    val proxySetOrder: Int = 0,
    val proxySetQuery: ProxySetQuery = ProxySetQuery(),
    val isRemote: Boolean = false,

    val urlTestingTags: Map<String, Int> = emptyMap(),

    val dashboardWidgets: List<DashboardWidgetEntry> = defaultDashboardWidgets(),
) {
    companion object {
        const val SHOW_TRACKER_ACTIVELY: Byte = 1
        const val SHOW_TRACKER_CLOSED: Byte = 2
    }

    val showActivate = queryOptions.showsActiveConnections()
    val showClosed = queryOptions.showsClosedConnections()

    /** Why [connections] would be empty under the current filter and search. */
    val emptyConnectionsReason: EmptyConnectionsReason
        get() = when {
            !showActivate && !showClosed -> EmptyConnectionsReason.NO_STATUS_SELECTED
            isSearchingConnections -> EmptyConnectionsReason.NO_MATCH
            showActivate && showClosed -> EmptyConnectionsReason.NO_CONNECTIONS
            showActivate -> EmptyConnectionsReason.NO_ACTIVE
            else -> EmptyConnectionsReason.NO_CLOSED
        }
}

enum class EmptyConnectionsReason {
    NO_CONNECTIONS,
    NO_ACTIVE,
    NO_CLOSED,
    NO_MATCH,
    NO_STATUS_SELECTED,
}

internal fun Byte.showsActiveConnections(): Boolean =
    this and DashboardState.SHOW_TRACKER_ACTIVELY != 0.toByte()

internal fun Byte.showsClosedConnections(): Boolean =
    this and DashboardState.SHOW_TRACKER_CLOSED != 0.toByte()

@Immutable
data class NetworkInterfaceInfo(
    val name: String,
    val addresses: List<String>,
)

object ProxySetOrder {
    const val ORIGIN = 0
    const val BY_NAME = 1
    const val BY_DELAY = 2

    val values get() = listOf(ORIGIN, BY_NAME, BY_DELAY)
}

@Immutable
data class ProxySet(
    val tag: String = "",
    val id: String = tag,
    val displayType: String = "",
    val selectable: Boolean = false,
    var selected: String = "",
    var items: List<ProxyItem> = emptyList(),
    val urlTestProgress: GroupUrlTestProgress? = null,
    val isAll: Boolean = false,
) {
    val isTesting: Boolean
        get() = urlTestProgress != null
}

internal const val ALL_PROXY_SET_ID = "__all_proxy_set__"

internal fun allProxySet(items: List<ProxyItem>): ProxySet {
    return ProxySet(
        id = ALL_PROXY_SET_ID,
        tag = "All proxies",
        displayType = "All",
        items = items,
        isAll = true,
    )
}

@Immutable
data class GroupUrlTestProgress(
    val current: Int,
    val total: Int,
)

@Immutable
data class ProxyItem(
    val tag: String = "",
    val type: String = "",
    val urlTestDelay: Int = -1,
    val displayType: String = type,
)


internal fun idleSpeedHistory(): List<Float> = List(SPEED_HISTORY_SIZE) { 0f }
internal const val SPEED_HISTORY_SIZE = 30
