package fr.husi.repository

import fr.husi.AlertType
import fr.husi.Key
import fr.husi.bg.BackendState
import fr.husi.bg.GuardedProcessPool
import fr.husi.bg.ServiceState
import fr.husi.bg.initPlugins
import fr.husi.bg.launchPlugins
import fr.husi.database.DataStore
import fr.husi.database.ProfileManager
import fr.husi.ktx.Logs
import fr.husi.ktx.readableMessage
import fr.husi.libcore.Service
import fr.husi.plugin.PluginNotFoundException
import fr.husi.resources.Res
import fr.husi.resources.invalid_server
import fr.husi.resources.profile_empty
import fr.husi.resources.service_failed
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.net.UnknownHostException

internal class DesktopServiceRuntime(
    private val boxService: Service?,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val access = Mutex()

    private var serviceStarted = false
    private var runningProfileName: String? = null
    private var processes: GuardedProcessPool? = null
    private val cacheFiles = ArrayList<java.io.File>()

    fun start() {
        runExclusive { startLocked() }
    }

    fun reload() {
        runExclusive {
            when {
                DataStore.selectedProxy == 0L -> {
                    stopLocked(repo.getString(Res.string.profile_empty))
                }

                DataStore.serviceState == ServiceState.Stopped || DataStore.serviceState == ServiceState.Idle -> {
                    startLocked()
                }

                DataStore.serviceState.canStop -> {
                    stopLocked()
                    startLocked()
                }

                else -> Logs.w("Illegal state ${DataStore.serviceState} when invoking reload")
            }
        }
    }

    fun stop() {
        runExclusive { stopLocked() }
    }

    private fun runExclusive(block: suspend () -> Unit): Job = scope.launch {
        access.withLock { block() }
    }

    private suspend fun startLocked() {
        val state = DataStore.serviceState
        if (state.canStop || state == ServiceState.Stopping) return

        val profile = ProfileManager.getProfile(DataStore.selectedProxy)
        if (profile == null) {
            stopLocked(repo.getString(Res.string.profile_empty))
            return
        }

        val service = boxService
        if (service == null) {
            stopLocked("${repo.getString(Res.string.service_failed)}: Service unavailable")
            return
        }

        changeState(ServiceState.Connecting)
        BackendState.setConnected(false)

        try {
            ensureServiceStarted(service)

            val config = fr.husi.fmt.buildConfig(profile)
            cacheFiles.clear()
            val pluginConfigs = initPlugins(
                config = config,
                isVPN = DataStore.serviceMode == Key.MODE_VPN,
                cacheFiles = cacheFiles,
            )
            val pool = GuardedProcessPool { throwable ->
                handleFatal(throwable)
            }
            processes = pool
            launchPlugins(
                config = config,
                pluginConfigs = pluginConfigs,
                processes = pool,
                cacheFiles = cacheFiles,
            )

            service.newInstance(config.config)
            service.startInstance()

            DataStore.currentProfile = profile.id
            runningProfileName = profile.displayNameForService()
            changeState(ServiceState.Connected, runningProfileName)
            BackendState.setConnected(true)
        } catch (e: Throwable) {
            when (e) {
                is UnknownHostException -> stopLocked(repo.getString(Res.string.invalid_server))
                is PluginNotFoundException ->
                    stopLocked(e.readableMessage, AlertType.MISSING_PLUGIN, e.plugin)

                else -> stopLocked(
                    "${repo.getString(Res.string.service_failed)}: ${e.readableMessage}",
                )
            }
        }
    }

    private suspend fun stopLocked(
        message: String? = null,
        alertType: Int = AlertType.COMMON,
        alertMessage: String = message.orEmpty(),
    ) {
        if (DataStore.serviceState == ServiceState.Stopping) return

        changeState(ServiceState.Stopping, runningProfileName)
        BackendState.setConnected(false)

        cleanupLocked()
        runningProfileName = null

        BackendState.reset()
        changeState(ServiceState.Stopped)

        if (alertMessage.isNotBlank()) {
            BackendState.emitAlert(alertType, alertMessage)
        }
        if (!message.isNullOrBlank()) {
            Logs.w(message)
        }
    }

    private suspend fun cleanupLocked() {
        val service = boxService
        val pool = processes
        processes = null

        pool?.close(scope)

        runCatching {
            if (service?.hasInstance() == true) {
                service.stopInstance()
            }
        }.onFailure {
            Logs.w(it)
        }

        cacheFiles.forEach { file ->
            runCatching { file.delete() }
        }
        cacheFiles.clear()
    }

    private suspend fun handleFatal(throwable: Throwable) {
        access.withLock {
            if (!DataStore.serviceState.canStop) return
            stopLocked("${repo.getString(Res.string.service_failed)}: ${throwable.readableMessage}")
        }
    }

    private fun ensureServiceStarted(service: Service) {
        if (serviceStarted) return
        service.start()
        serviceStarted = true
    }

    private fun changeState(state: ServiceState, profileName: String? = null) {
        DataStore.serviceState = state
        BackendState.updateState(state, profileName)
    }
}
