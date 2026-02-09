package fr.husi.bg

import android.content.Context
import android.graphics.drawable.Icon
import android.service.quicksettings.Tile
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import fr.husi.R
import fr.husi.database.DataStore
import fr.husi.database.SagerDatabase
import fr.husi.ktx.onIoDispatcher
import fr.husi.repository.repo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import android.service.quicksettings.TileService as BaseTileService

@RequiresApi(24)
class TileService : BaseTileService() {
    private val iconRest by lazy { Icon.createWithResource(this, R.drawable.ic_service_rest) }
    private val iconConnected by lazy {
        Icon.createWithResource(this, R.drawable.ic_service_active)
    }

    private val scope = CoroutineScope(Dispatchers.Main.immediate)

    override fun attachBaseContext(newBase: Context) {
        val languageContext = ContextCompat.getContextForLanguage(newBase)
        super.attachBaseContext(languageContext)
    }

    override fun onStartListening() {
        super.onStartListening()
        refreshTile()
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
            val state = DataStore.serviceState
            when {
                state.canStop -> {
                    updateTile(BaseService.State.Stopping, null)
                    repo.stopService()
                }

                state == BaseService.State.Stopped || state == BaseService.State.Idle -> {
                    updateTile(BaseService.State.Connecting, null)
                    repo.startService()
                }
            }
        }
    }

    private fun refreshTile() {
        scope.launch {
            val state = DataStore.serviceState
            val profileName = if (state.connected) {
                onIoDispatcher {
                    val profileId = DataStore.currentProfile
                    if (profileId <= 0L) {
                        null
                    } else {
                        SagerDatabase.proxyDao.getById(profileId)?.let {
                            ServiceNotification.genTitle(it)
                        }
                    }
                }
            } else {
                null
            }
            updateTile(state, profileName)
        }
    }
}
