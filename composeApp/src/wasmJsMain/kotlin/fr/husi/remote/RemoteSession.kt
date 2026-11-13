package fr.husi.remote

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import fr.husi.database.RemoteBackend
import fr.husi.ui.dashboard.DashboardState
import kotlinx.coroutines.flow.Flow

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

    abstract suspend fun observe(intervals: Flow<Int>)

    abstract suspend fun select(group: String, name: String)

    abstract suspend fun testDelay(name: String): Int?

    abstract suspend fun closeConnection(id: String)

    abstract suspend fun closeConnections()

    abstract suspend fun setMode(mode: String)

    abstract suspend fun clearLogs()

    abstract fun close()
}
