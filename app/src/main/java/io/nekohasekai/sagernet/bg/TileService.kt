package io.nekohasekai.sagernet.bg

import android.content.Context
import android.graphics.drawable.Icon
import android.service.quicksettings.Tile
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.repository.repo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import android.service.quicksettings.TileService as BaseTileService

@RequiresApi(24)
class TileService : BaseTileService() {
    private val iconRest by lazy { Icon.createWithResource(this, R.drawable.ic_service_rest) }
    private val iconConnected by lazy {
        Icon.createWithResource(this, R.drawable.ic_service_active)
    }
    private var tapPending = false

    private val connection = SagerConnection(SagerConnection.CONNECTION_ID_TILE)
    private var observeJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main.immediate)

    override fun attachBaseContext(newBase: Context) {
        val languageContext = ContextCompat.getContextForLanguage(newBase)
        super.attachBaseContext(languageContext)
    }

    override fun onStartListening() {
        super.onStartListening()
        connection.connect(this)
        observeJob = scope.launch {
            combine(connection.status, connection.service) { status, service ->
                status to service
            }.collectLatest { (status, service) ->
                if (service != null) {
                    updateTile(status.state, status.profileName)
                    if (tapPending) {
                        tapPending = false
                        onClick()
                    }
                }
            }
        }
    }

    override fun onStopListening() {
        observeJob?.cancel()
        observeJob = null
        connection.disconnect(this)
        super.onStopListening()
    }

    override fun onClick() {
        if (isLocked) unlockAndRun(this::toggle) else toggle()
    }

    private fun updateTile(serviceState: BaseService.State, profileName: String?) {
        qsTile?.apply {
            label = null
            when (serviceState) {
                BaseService.State.Connecting -> {
                    icon = iconRest
                    state = Tile.STATE_ACTIVE
                }

                BaseService.State.Connected -> {
                    icon = iconConnected
                    label = profileName
                    state = Tile.STATE_ACTIVE
                }

                BaseService.State.Stopping -> {
                    icon = iconRest
                    state = Tile.STATE_UNAVAILABLE
                }

                // Stopped
                else -> {
                    icon = iconRest
                    state = Tile.STATE_INACTIVE
                }
            }
            label = label ?: getString(R.string.app_name)
            updateTile()
        }
    }

    private fun toggle() {
        scope.launch {
            val service = connection.service.value
            if (service == null) {
                tapPending = true
            } else {
                val state = BaseService.State.entries[service.state]
                when {
                    state.canStop -> repo.stopService()
                    state == BaseService.State.Stopped -> repo.startService()
                }
            }
        }
    }
}
