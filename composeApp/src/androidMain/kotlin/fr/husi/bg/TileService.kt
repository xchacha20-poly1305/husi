package fr.husi.bg

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import androidx.core.content.ContextCompat
import fr.husi.Key
import fr.husi.database.DataStore
import fr.husi.database.SagerDatabase
import fr.husi.ktx.Logs
import fr.husi.ktx.onIoDispatcher
import fr.husi.lib.R
import fr.husi.repository.resolveRepository
import fr.husi.resources.Res
import fr.husi.resources.app_name
import fr.husi.ui.VpnRequestActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import android.net.VpnService as BaseVpnService
import android.service.quicksettings.TileService as BaseTileService

class TileService : BaseTileService() {
    private val iconRest by lazy { Icon.createWithResource(this, R.drawable.ic_service_rest) }
    private val iconConnected by lazy {
        Icon.createWithResource(this, R.drawable.ic_service_active)
    }

    private val scope = CoroutineScope(Dispatchers.Main.immediate)

    private var listenJob: Job? = null

    override fun attachBaseContext(newBase: Context) {
        val languageContext = ContextCompat.getContextForLanguage(newBase)
        super.attachBaseContext(languageContext)
    }

    override fun onStartListening() {
        super.onStartListening()
        refreshTile()
        // The tile shares the ":bg" process with the services, so the state written by
        // BaseService.changeState is readable right here without any cross process hop.
        listenJob = scope.launch {
            BackendState.status.collect { status ->
                updateTile(status.state, status.profileName)
            }
        }
    }

    override fun onStopListening() {
        listenJob?.cancel()
        listenJob = null
        super.onStopListening()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onClick() {
        if (isLocked) unlockAndRun(this::toggle) else toggle()
    }

    private fun updateTile(serviceState: ServiceState, profileName: String?) {
        qsTile?.apply {
            label = null
            when (serviceState) {
                ServiceState.Connecting -> {
                    icon = iconRest
                    state = Tile.STATE_ACTIVE
                }

                ServiceState.Connected -> {
                    icon = iconConnected
                    label = profileName
                    state = Tile.STATE_ACTIVE
                }

                ServiceState.Stopping -> {
                    icon = iconRest
                    state = Tile.STATE_UNAVAILABLE
                }

                // Stopped
                else -> {
                    icon = iconRest
                    state = Tile.STATE_INACTIVE
                }
            }
            label = label ?: runBlocking {
                resolveRepository().getString(Res.string.app_name)
            }
            updateTile()
        }
    }

    private fun toggle() {
        scope.launch {
            val state = DataStore.serviceState
            when {
                state.canStop -> {
                    updateTile(ServiceState.Stopping, null)
                    resolveRepository().stopService()
                }

                state == ServiceState.Stopped || state == ServiceState.Idle -> {
                    updateTile(ServiceState.Connecting, null)
                    startServiceFromTile()
                }
            }
        }
    }

    /**
     * [Android 15: TileService onClick does not allow startForegroundService](https://issuetracker.google.com/issues/377528724)
     *
     * Inspired by: [WireGuard Android QuickTileService.kt](https://github.com/WireGuard/wireguard-android/blob/e7b3a3c118836e112620b1302a8ba1873ad4daac/ui/src/main/java/com/wireguard/android/QuickTileService.kt)
     */
    private fun startServiceFromTile() {
        if (DataStore.serviceMode == Key.MODE_VPN && BaseVpnService.prepare(this) != null) {
            // Consent is missing: the foreground service start is doomed anyway, and
            // VpnService would then try to launch the consent activity from the background,
            // which Android 10 forbids. Go straight through the activity.
            startViaRequestActivity()
            return
        }

        val started = try {
            resolveRepository().startService()
            true
        } catch (e: IllegalStateException) {
            // ForegroundServiceStartNotAllowedException on Android 12+.
            Logs.w("Tile cannot start the foreground service directly", e)
            false
        } catch (e: SecurityException) {
            Logs.w("Tile cannot start the foreground service directly", e)
            false
        }
        if (!started) startViaRequestActivity()
    }

    /**
     * Collapses the panel onto [VpnRequestActivity], which asks for VPN consent when needed and
     * then starts the service from a foreground activity context.
     */
    private fun startViaRequestActivity() {
        val intent = Intent(this, VpnRequestActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(
                PendingIntent.getActivity(
                    this,
                    0,
                    intent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                ),
            )
        } else {
            @Suppress("DEPRECATION")
            @SuppressLint("StartActivityAndCollapseDeprecated")
            startActivityAndCollapse(intent)
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
                        SagerDatabase.proxyDao.getById(profileId)?.displayNameForService()
                    }
                }
            } else {
                null
            }
            updateTile(state, profileName)
        }
    }
}
