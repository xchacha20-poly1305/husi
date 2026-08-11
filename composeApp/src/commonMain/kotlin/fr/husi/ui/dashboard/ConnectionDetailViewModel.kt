package fr.husi.ui.dashboard

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.husi.core.CoreClient
import fr.husi.core.formatConnectionTime
import fr.husi.core.isClosed
import fr.husi.core.isNew
import fr.husi.core.isUpdate
import fr.husi.ktx.Logs
import fr.husi.ktx.emptyAsNull
import fr.husi.ktx.onIoDispatcher
import fr.husi.utils.PackageResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.koin.core.context.GlobalContext
import kotlin.time.Duration.Companion.seconds

@Stable
class ConnectionDetailViewModel(
    private val uuid: String,
    private val coreClient: CoreClient = GlobalContext.get().get(),
) : ViewModel() {

    val connection: StateFlow<ConnectionDetailState>
        field = MutableStateFlow(ConnectionDetailState(uuid = uuid))

    private var job: Job? = null

    init {
        viewModelScope.launch {
            initialize(uuid)
        }
    }

    override fun onCleared() {
        job?.cancel()
        job = null
        super.onCleared()
    }

    suspend fun initialize(uuid: String) {
        job?.cancel()
        connection.value = ConnectionDetailState(uuid = uuid)
        // First reset batch is the snapshot; subsequent deltas patch the one connection.
        job = viewModelScope.launch {
            try {
                coreClient.subscribeConnections(1.seconds).collect { events ->
                    if (events.reset) {
                        val match = events.eventsList.firstOrNull { event ->
                            event.id == uuid && event.hasConnection()
                        }
                        connection.value = match?.connection?.toDetailState()
                            ?: ConnectionDetailState(uuid = uuid)
                        return@collect
                    }
                    for (event in events.eventsList) {
                        if (event.id != uuid) continue
                        when {
                            event.isUpdate() -> {
                                updateTraffic(event.uplinkDelta, event.downlinkDelta)
                            }

                            event.isNew() -> {
                                val tracker = event.connection ?: continue
                                connection.value = tracker.toDetailState()
                            }

                            event.isClosed() -> {
                                val closedAt = formatConnectionTime(event.closedAt)
                                if (closedAt.isBlank()) continue
                                updateClosedAt(closedAt)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Logs.w("subscribe connection detail", e)
            }
        }
    }

    private fun updateTraffic(uplinkDelta: Long, downlinkDelta: Long) {
        if (uplinkDelta == 0L && downlinkDelta == 0L) return
        val current = connection.value
        connection.value = current.copy(
            uploadTotal = current.uploadTotal + uplinkDelta,
            downloadTotal = current.downloadTotal + downlinkDelta,
        )
    }

    private fun updateClosedAt(closedAt: String) {
        val current = connection.value
        if (current.closedAt == closedAt) return
        connection.value = current.copy(closedAt = closedAt)
    }

    internal suspend fun resolveProcessInfo(process: String?, uid: Int): ProcessInfo? {
        return onIoDispatcher {
            if (process.isNullOrBlank() && uid < 0) return@onIoDispatcher null
            PackageResolver.awaitLoad()
            val packageName = resolvePackageName(process, uid) ?: return@onIoDispatcher null
            val label = PackageResolver.loadAppLabel(packageName) ?: return@onIoDispatcher null
            val icon = PackageResolver.loadAppIcon(packageName)
            ProcessInfo(packageName = packageName, label = label, icon = icon)
        }
    }

    private fun resolvePackageName(process: String?, uid: Int): String? {
        process.emptyAsNull()?.let { packageName ->
            if (PackageResolver.isAppInstalled(packageName)) {
                return packageName
            }
        }
        if (uid >= 0) {
            return PackageResolver.findPackagesForUid(uid)?.firstOrNull()
        }
        return null
    }

    fun closeConnection(uuid: String) = viewModelScope.launch(Dispatchers.IO) {
        try {
            coreClient.closeConnection(uuid)
        } catch (e: Exception) {
            Logs.w("close connection", e)
        }
    }
}
