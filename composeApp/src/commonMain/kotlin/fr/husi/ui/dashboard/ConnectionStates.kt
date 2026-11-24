package fr.husi.ui.dashboard

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable

@Immutable
data class SmartConnectionInfo(
    val blockState: String = "",
    val target: String = "",
    val wildcardTarget: String = "",
    val exitGeoIP: String = "",
    val exitIPASN: String = "",
) {
    val canBlock: Boolean
        get() = blockState == SMART_BLOCK_NORMAL

    companion object {
        const val SMART_BLOCK_NORMAL = "normal"
        const val SMART_BLOCK_DEGRADED = "degraded"
        const val SMART_BLOCK_BLOCKED = "blocked"
    }
}

@Stable
data class ConnectionDetailState(
    val uuid: String = "",
    val inbound: String = "",
    val ipVersion: Short? = null,
    val network: String = "",
    val uploadTotal: Long = 0L,
    val downloadTotal: Long = 0L,
    val uploadSpeed: Long = 0L,
    val downloadSpeed: Long = 0L,
    val startedAt: String = "",
    val closedAt: String = "",
    val src: String = "",
    val dst: String = "",
    val host: String = "",
    val matchedRule: String = "",
    val outbound: String = "",
    val chain: String = "",
    val protocol: String? = null,
    val processes: List<String>? = null,
    val uid: Int = -1,
    val smart: SmartConnectionInfo? = null,
) {
    val isClosed: Boolean
        get() = closedAt.isNotEmpty()
}
