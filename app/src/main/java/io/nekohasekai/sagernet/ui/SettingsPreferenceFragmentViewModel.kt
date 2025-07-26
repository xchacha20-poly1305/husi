package io.nekohasekai.sagernet.ui

import androidx.lifecycle.ViewModel
import io.nekohasekai.sagernet.SagerNet
import io.nekohasekai.sagernet.database.DataStore
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.onSuccess
import kotlinx.coroutines.runBlocking

class SettingsPreferenceFragmentViewModel : ViewModel() {

    fun initDataStore() {
        DataStore.initGlobal()
    }

    /** Reload service if started. */
    fun tryReloadService() {
        if (DataStore.serviceState.started) {
            SagerNet.reloadService()
        }
    }

    fun tryStopService() {
        if (DataStore.serviceState.started) {
            SagerNet.stopService()
        }
    }

    private val _shouldRestart = Channel<Unit>(Channel.BUFFERED)
    fun storeShouldRestart() {
        runBlocking {
            _shouldRestart.send(Unit)
        }
    }

    fun tryReceiveShouldRestart(): Boolean {
        _shouldRestart.tryReceive().onSuccess {
            return true
        }
        return false
    }
}