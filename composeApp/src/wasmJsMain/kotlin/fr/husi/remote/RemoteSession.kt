package fr.husi.remote

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import fr.husi.database.RemoteBackend
import fr.husi.ui.dashboard.DashboardState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope

internal enum class RemoteSubscription {
    STATS,
    PROXY_SET,
    CONNECTIONS,
    LOGS,
}

internal data class RemoteLogEntry(val level: String, val message: String)

/** Backend-neutral presentation and operations shared by both controller protocols. */
internal abstract class RemoteSession {
    abstract val backend: RemoteBackend
    var version by mutableStateOf("")
        protected set

    var dashboard by mutableStateOf(DashboardState(isRemote = true))
        protected set

    val logs = mutableStateListOf<RemoteLogEntry>()

    abstract suspend fun connect(url: String, secret: String)

    abstract suspend fun observe(
        intervals: Flow<Int>,
        subscriptions: Flow<Set<RemoteSubscription>>,
    )

    abstract suspend fun select(group: String, name: String)

    open suspend fun clearFixed(group: String) {}

    abstract suspend fun testDelay(name: String): Int?

    abstract suspend fun closeConnection(id: String)

    open suspend fun blockSmartConnection(id: String) {}

    abstract suspend fun closeConnections()

    abstract suspend fun setMode(mode: String)

    abstract suspend fun clearLogs()

    abstract fun close()
}

internal fun CoroutineScope.observeWhile(
    enabled: Flow<Boolean>,
    block: suspend CoroutineScope.() -> Unit,
) = launch {
    enabled.distinctUntilChanged().collectLatest { active ->
        if (active) {
            supervisorScope(block)
        }
    }
}
