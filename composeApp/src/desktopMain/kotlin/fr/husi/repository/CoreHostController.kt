package fr.husi.repository

import fr.husi.Key
import fr.husi.bg.BackendState
import fr.husi.bg.OpenConnectAuthWatcher
import fr.husi.bg.ServiceAlert
import fr.husi.bg.ServiceState
import fr.husi.bg.buildPluginSpecs
import fr.husi.bg.initPlugins
import fr.husi.bg.proto.TrafficLooper
import fr.husi.core.BridgeCoreClient
import fr.husi.core.CoreClient
import fr.husi.database.DataStore
import fr.husi.database.ProfileManager
import fr.husi.fmt.buildConfig
import fr.husi.ktx.Logs
import fr.husi.ktx.readableMessage
import fr.husi.platform.Platform
import fr.husi.platform.PlatformInfo
import fr.husi.plugin.PluginNotFoundException
import fr.husi.proto.v1.GetDaemonInfoResponse
import fr.husi.proto.v1.Hosting
import fr.husi.proto.v1.clientMetadata
import fr.husi.proto.v1.startServiceRequest
import fr.husi.resources.Res
import fr.husi.resources.invalid_server
import fr.husi.resources.profile_empty
import fr.husi.resources.service_failed
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.koin.core.context.GlobalContext
import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.io.InputStreamReader
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Reactive snapshot of whether the UI is attached to the privileged system
 * daemon, whether that daemon's API revision matches this app, and whether
 * another local user currently owns it.
 */
data class CoreHostState(
    val isDaemon: Boolean = false,
    val apiVersionMismatch: Boolean = false,
    val foreignOwner: DaemonOwner? = null,
)

/** Human-readable owner of a daemon this UI is attached to but does not own. */
data class DaemonOwner(
    val name: String,
    val id: String,
)

/**
 * Desktop service orchestrator that prefers a privileged system daemon when
 * available, otherwise runs the proxy core out-of-process via
 * `husi-core session` and drives it over gRPC ([CoreClient] DaemonService).
 */
internal class CoreHostController(
    private val repository: DesktopRepository,
    private val resolveCoreClient: () -> CoreClient = { GlobalContext.get().get() },
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val access = Mutex()

    private var runningProfileName: String? = null
    var trafficLooper: TrafficLooper? = null
        private set
    private val cacheFiles = ArrayList<File>()

    private var sessionProcess: Process? = null
    private var sessionStdin: java.io.OutputStream? = null
    private var hostReady = false

    /** True when the shared [CoreClient] is dialing the system daemon socket. */
    private var connectedToDaemon = false

    /** Only probe the daemon socket once per UI process unless it drops. */
    private var triedDaemon = false

    /**
     * Set when [GetDaemonInfoResponse.apiVersion] does not match the API
     * version this UI expects. Blocks start while connected to a mismatched
     * daemon.
     */
    private var apiVersionMismatch = false

    /**
     * Set when the attached daemon is owned by another local user. The UI
     * stays read-only until the user takes over from Settings.
     */
    private var foreignOwner: DaemonOwner? = null

    /**
     * Mirrors [connectedToDaemon] / [apiVersionMismatch] / [foreignOwner] for
     * Settings and other UI. Private fields remain the source of truth.
     */
    val hostState: StateFlow<CoreHostState>
        field = MutableStateFlow(CoreHostState())

    /** Whether the shared client is attached to the privileged system daemon. */
    val isDaemonMode: Boolean
        get() = connectedToDaemon

    private val coreDir: File
        get() = repository.coreDir

    private val socketBasePath: String
        get() = repository.coreSocketBasePath

    private val coreClient: CoreClient
        get() = resolveCoreClient()

    /**
     * Connects to a live system daemon if present, otherwise spawns
     * `husi-core session` and waits until the gRPC bridge answers.
     * Blocks the caller; safe to invoke from UI bootstrap before the event loop starts.
     */
    fun ensureHost() {
        runBlocking {
            access.withLock { ensureHostLocked() }
        }
    }

    fun start() {
        runExclusive { startLocked() }
    }

    fun reload() {
        runExclusive {
            when {
                DataStore.selectedProxy == 0L -> {
                    stopLocked(resolveRepository().getString(Res.string.profile_empty))
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

    /**
     * UI process shutdown. Always stops the box instance, then detaches from
     * a system daemon (the daemon process stays installed) or kills a
     * session child.
     */
    fun shutdownHost() {
        runBlocking {
            access.withLock {
                stopLocked()
                if (connectedToDaemon) {
                    detachDaemonClientLocked()
                } else {
                    closeSessionLocked()
                }
            }
        }
    }

    /**
     * Drop any session host, clear prior daemon-attach attempts, and try to
     * attach to the system daemon again (e.g. after elevated
     * `husi-core service install`). Safe while stopped.
     *
     * If a **session-mode** proxy was running, it is stopped so the session
     * child can be torn down. An already-running **daemon** service is left
     * alone; only the UI client's connection is re-established.
     */
    suspend fun reattachDaemon() {
        access.withLock {
            if (connectedToDaemon) {
                detachDaemonClientLocked()
            } else {
                if (hostReady && DataStore.serviceState.canStop) {
                    stopLocked()
                }
                closeSessionLocked()
            }
            triedDaemon = false
            apiVersionMismatch = false
            foreignOwner = null
            publishHostState()
            ensureHostLocked()
        }
    }

    /**
     * Explicit takeover of a daemon owned by another local user. Disconnects
     * that owner's client. Failures propagate so the UI can surface them.
     */
    suspend fun takeOverDaemon() {
        access.withLock {
            coreClient.takeOverService()
            val ownership = coreClient.getDaemonInfo().ownership
            if (!ownership.claimed) {
                coreClient.claimService()
            }
            foreignOwner = null
            publishHostState()
        }
    }

    private fun publishHostState() {
        hostState.value = CoreHostState(
            isDaemon = connectedToDaemon,
            apiVersionMismatch = apiVersionMismatch,
            foreignOwner = foreignOwner,
        )
    }

    private fun runExclusive(block: suspend () -> Unit): Job = scope.launch {
        access.withLock { block() }
    }

    private suspend fun ensureHostLocked() {
        // Already on a live daemon — just re-probe.
        if (connectedToDaemon && hostReady) {
            if (probeHost()) return
            Logs.w("daemon connection lost; falling back to session")
            detachDaemonClientLocked()
            triedDaemon = false
        }

        // Already on a live session — re-probe.
        if (!connectedToDaemon && hostReady && sessionProcess?.isAlive == true) {
            if (probeHost()) return
            hostReady = false
        }

        // Try the system daemon first (once per attach cycle).
        if (!connectedToDaemon && !triedDaemon) {
            triedDaemon = true
            if (tryAttachDaemonLocked()) {
                return
            }
        }

        // Session fallback.
        if (sessionProcess?.isAlive != true) {
            spawnSessionLocked()
        }
        waitForHostReady()
        hostReady = true
    }

    /**
     * Probe the platform-standard daemon socket. On success, claim ownership,
     * point the shared [CoreClient] at the daemon, and mark ready.
     */
    private suspend fun tryAttachDaemonLocked(): Boolean {
        val daemonPath = daemonSocketBasePath()
        if (!daemonSocketPresent(daemonPath)) {
            Logs.d("no daemon socket at $daemonPath")
            return false
        }

        val probeClient = BridgeCoreClient(daemonPath)
        try {
            probeClient.probe()
            val info = probeClient.getDaemonInfo()
            if (info.hosting != Hosting.HOSTING_DAEMON) {
                Logs.d("socket at $daemonPath is not a daemon (hosting=${info.hosting})")
                return false
            }
            checkApiVersion(info)
            tryClaimDaemon(probeClient, info)
        } catch (e: Exception) {
            Logs.d("No daemon found, falling back to session: ${e.message}")
            return false
        } finally {
            runCatching { probeClient.close() }
        }

        switchToDaemonClient(daemonPath)
        connectedToDaemon = true
        hostReady = true
        publishHostState()
        Logs.i("connected to system daemon at $daemonPath")
        return true
    }

    private suspend fun switchToDaemonClient(daemonPath: String) {
        runCatching { coreClient.close() }
        repository.coreSocketBasePath = daemonPath
    }

    /**
     * Drop the shared client connection without stopping the daemon service.
     * Resets the dial path to the session working dir for a later fallback.
     */
    private suspend fun detachDaemonClientLocked() {
        OpenConnectAuthWatcher.stop()
        trafficLooper?.stop()
        trafficLooper = null
        runCatching { coreClient.close() }
        repository.resetCoreSocketBasePath()
        connectedToDaemon = false
        hostReady = false
        apiVersionMismatch = false
        foreignOwner = null
        publishHostState()
    }

    private fun checkApiVersion(info: GetDaemonInfoResponse) {
        val daemonVersion = info.apiVersion
        if (daemonVersion != EXPECTED_API_VERSION) {
            apiVersionMismatch = true
            val message =
                "Daemon version mismatch (daemon: $daemonVersion, app: $EXPECTED_API_VERSION). Update required."
            Logs.w(message)
            BackendState.emitAlert(ServiceAlert.Common(message))
        } else {
            apiVersionMismatch = false
        }
        publishHostState()
    }

    internal suspend fun tryClaimDaemon(client: CoreClient, info: GetDaemonInfoResponse) {
        val ownership = info.ownership
        if (ownership.claimed && !ownership.ownedByCaller) {
            Logs.w(
                "Daemon owned by ${ownership.ownerName} (${ownership.ownerId}); attaching read-only",
            )
            foreignOwner = DaemonOwner(
                name = ownership.ownerName,
                id = ownership.ownerId,
            )
            publishHostState()
            return
        }
        foreignOwner = null
        if (!ownership.claimed) {
            client.claimService()
        }
        publishHostState()
    }

    private suspend fun startLocked() {
        val state = DataStore.serviceState
        if (state.canStop || state == ServiceState.Stopping) return

        val profile = ProfileManager.getProfile(DataStore.selectedProxy)
        if (profile == null) {
            stopLocked(resolveRepository().getString(Res.string.profile_empty))
            return
        }

        changeState(ServiceState.Connecting)
        BackendState.setConnected(false)

        try {
            ensureHostLocked()

            if (apiVersionMismatch && connectedToDaemon) {
                stopLocked(
                    "Daemon version mismatch (daemon api != app $EXPECTED_API_VERSION). Update required.",
                )
                return
            }

            val owner = foreignOwner
            if (connectedToDaemon && owner != null) {
                val ownerLabel = owner.name.ifBlank { owner.id }
                stopLocked(
                    "Daemon is in use by $ownerLabel. Resolve the conflict in Settings.",
                )
                return
            }

            val isVPN = DataStore.serviceMode == Key.MODE_VPN
            if (isVPN && !connectedToDaemon) {
                stopLocked(
                    "TUN mode requires the system daemon. Install it via Settings or switch to proxy mode.",
                )
                return
            }

            val config = buildConfig(profile)
            cacheFiles.clear()
            val pluginConfigs = initPlugins(
                config = config,
                isVPN = isVPN,
                cacheFiles = cacheFiles,
            )
            val pluginSpecs = buildPluginSpecs(
                config = config,
                pluginConfigs = pluginConfigs,
                isVPN = isVPN,
            )

            val request = startServiceRequest {
                this.config = config.config
                plugins.addAll(pluginSpecs)
                clientMetadata = clientMetadata {
                    profileId = profile.id
                    profileName = profile.displayNameForService()
                }
            }
            coreClient.startService(request)
            OpenConnectAuthWatcher.start()

            trafficLooper = TrafficLooper(
                coreClient = coreClient,
                config = config,
                scope = scope,
            )
            trafficLooper?.start()

            DataStore.currentProfile = profile.id
            runningProfileName = profile.displayNameForService()
            changeState(ServiceState.Connected, runningProfileName)
            BackendState.setConnected(true)
        } catch (e: Throwable) {
            when (e) {
                is UnknownHostException -> stopLocked(resolveRepository().getString(Res.string.invalid_server))
                is PluginNotFoundException ->
                    stopLocked(e.readableMessage, ServiceAlert.MissingPlugin(e.plugin))

                else -> stopLocked(
                    "${resolveRepository().getString(Res.string.service_failed)}: ${e.readableMessage}",
                )
            }
        }
    }

    private suspend fun stopLocked(
        message: String? = null,
        alert: ServiceAlert? = message?.takeIf { it.isNotBlank() }?.let { ServiceAlert.Common(it) },
    ) {
        if (DataStore.serviceState == ServiceState.Stopping) return

        changeState(ServiceState.Stopping, runningProfileName)
        BackendState.setConnected(false)

        cleanupLocked()
        runningProfileName = null

        BackendState.reset()
        changeState(ServiceState.Stopped)

        if (alert != null) {
            BackendState.emitAlert(alert)
        }
        if (!message.isNullOrBlank()) {
            Logs.w(message)
        }
    }

    private suspend fun cleanupLocked() {
        OpenConnectAuthWatcher.stop()

        trafficLooper?.stop()
        trafficLooper = null

        runCatching {
            if (hostReady) {
                coreClient.stopService()
            }
        }.onFailure {
            Logs.w(it)
        }

        cacheFiles.forEach { file ->
            runCatching { file.delete() }
        }
        cacheFiles.clear()
    }

    private suspend fun spawnSessionLocked() {
        closeSessionLocked()
        // Ensure the shared client dials the session working dir, not a stale daemon path.
        repository.resetCoreSocketBasePath()
        connectedToDaemon = false
        apiVersionMismatch = false
        foreignOwner = null
        publishHostState()

        coreDir.mkdirs()
        val binary = resolveHusiCoreBinary()
            ?: throw IOException(
                "husi-core binary not found (looked in: ${describeHusiCoreSearchLocations()})",
            )

        val socketPath = coreDir.resolve("api.sock")
        if (socketPath.exists()) {
            runCatching { socketPath.delete() }
        }

        val command = listOf(
            binary.absolutePath,
            "session",
            "--dir",
            coreDir.absolutePath,
            "--socket",
            socketPath.absolutePath,
        )
        Logs.i("starting core host: ${command.joinToString(" ")}")

        val process = ProcessBuilder(command)
            .directory(coreDir)
            .redirectErrorStream(false)
            .start()

        sessionProcess = process
        sessionStdin = process.outputStream

        Thread({
            try {
                BufferedReader(InputStreamReader(process.errorStream)).use { reader ->
                    reader.lineSequence().forEach { line ->
                        Logs.d("[husi-core] $line")
                    }
                }
            } catch (_: IOException) {
            }
        }, "husi-core-stderr").apply {
            isDaemon = true
            start()
        }

        Thread({
            try {
                BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                    reader.lineSequence().forEach { line ->
                        Logs.d("[husi-core] $line")
                    }
                }
            } catch (_: IOException) {
            }
        }, "husi-core-stdout").apply {
            isDaemon = true
            start()
        }

        scope.launch {
            val exit = runInterruptible(Dispatchers.IO) { process.waitFor() }
            Logs.w("husi-core session exited with code $exit")
            access.withLock {
                if (sessionProcess === process) {
                    sessionProcess = null
                    sessionStdin = null
                    hostReady = false
                }
                if (DataStore.serviceState.canStop) {
                    stopLocked(
                        "${resolveRepository().getString(Res.string.service_failed)}: core host exited ($exit)",
                    )
                }
            }
        }
    }

    private suspend fun waitForHostReady() {
        var delayDuration = 50.milliseconds
        val deadline = System.nanoTime() + HOST_READY_TIMEOUT.inWholeNanoseconds
        while (System.nanoTime() < deadline) {
            if (sessionProcess?.isAlive != true) {
                throw IOException("husi-core session exited before becoming ready")
            }
            if (probeHost()) return
            delay(delayDuration)
            delayDuration = (delayDuration * 2).coerceAtMost(500.milliseconds)
        }
        throw IOException("timed out waiting for husi-core session on $socketBasePath")
    }

    private suspend fun probeHost(): Boolean {
        return runCatching {
            coreClient.probe()
            true
        }.getOrDefault(false)
    }

    private suspend fun closeSessionLocked() {
        hostReady = false
        runCatching {
            GlobalContext.getOrNull()?.get<CoreClient>()?.close()
        }

        val stdin = sessionStdin
        sessionStdin = null
        runCatching { stdin?.close() }

        val process = sessionProcess
        sessionProcess = null
        if (process != null && process.isAlive) {
            val exited = runInterruptible(Dispatchers.IO) {
                process.waitFor(2, TimeUnit.SECONDS)
            }
            if (!exited) {
                process.destroy()
                val forceExited = runInterruptible(Dispatchers.IO) {
                    process.waitFor(1, TimeUnit.SECONDS)
                }
                if (!forceExited) {
                    process.destroyForcibly()
                }
            }
        }
    }

    private fun changeState(state: ServiceState, profileName: String? = null) {
        DataStore.serviceState = state
        BackendState.updateState(state, profileName)
    }

    /** Test-only: pretend the shared client is attached to a live host. */
    internal fun attachHostForTest(daemon: Boolean) {
        connectedToDaemon = daemon
        hostReady = true
        publishHostState()
    }

    companion object {
        private val HOST_READY_TIMEOUT = 15.seconds

        /**
         * Must match `github.com/sagernet/sing-box/daemon.APIVersion` for the
         * sing-box revision pinned in `libcore/go.mod`. Bump with the pin (or
         * expose via the bridge later).
         */
        private const val EXPECTED_API_VERSION = 3

        /**
         * Parent directory of the Unix daemon UDS
         * (`libcore/daemonhost.DefaultDaemonSocketPath` = `/var/run/husi/api.sock`).
         * [BridgeClient] dials `basePath/api.sock`.
         */
        private const val DAEMON_SOCKET_DIR_UNIX = "/var/run/husi"

        /**
         * Windows protected named pipe
         * (`libcore/coresvc.DaemonPipePath` /
         * `libcore/daemonhost.DefaultDaemonPipePath`).
         * [BridgeClient] / [Libcore.newBridgeClient] pass this as `basePath`;
         * Go `coresvc.ClientEndpoint` detects the pipe prefix and dials it
         * as-is (no `api.sock` join).
         */
        private const val DAEMON_PIPE_PATH_WINDOWS =
            """\\.\pipe\ProtectedPrefix\Administrators\husi"""

        /**
         * Base path for [BridgeCoreClient] / [Libcore.newBridgeClient].
         * Unix: directory containing `api.sock` (joined by
         * `coresvc.ClientEndpoint` / `SocketPath`).
         * Windows: full pipe path returned as-is by `coresvc.ClientEndpoint`.
         */
        internal fun daemonSocketBasePath(): String {
            return when (PlatformInfo.platform) {
                Platform.Linux, Platform.MacOs -> DAEMON_SOCKET_DIR_UNIX
                Platform.Windows -> DAEMON_PIPE_PATH_WINDOWS
                Platform.Android -> error("impossible")
            }
        }

        /**
         * Cheap existence check before dialing. Unix UDS is a filesystem path;
         * Windows named pipes are not, so we always attempt dial there.
         */
        internal fun daemonSocketPresent(basePath: String): Boolean {
            return PlatformInfo.isWindows || File(basePath).resolve("api.sock").exists()
        }
    }
}

/**
 * Locates the `husi-core` binary for the current host:
 * 1. Next to the packaged launcher / application
 * 2. `libcore/build/<os>_<arch>/husi-core` relative to the working directory (dev)
 * 3. `PATH`
 */
internal fun resolveHusiCoreBinary(): File? {
    val binaryName = husiCoreBinaryName()

    resolvePackagedCoreSibling(binaryName)?.takeIf { it.canExecuteOrWindows() }?.let { return it }

    devHusiCoreCandidates(binaryName)
        .firstOrNull { it.isFile && it.canExecuteOrWindows() }
        ?.let { return it.absoluteFile }

    return resolveOnPath(binaryName)
}

private fun husiCoreBinaryName(): String {
    return if (PlatformInfo.isWindows) "husi-core.exe" else "husi-core"
}

private fun devHusiCoreCandidates(binaryName: String): List<File> {
    val relativePath = "libcore/build/${hostCoreBuildDirName()}/$binaryName"
    // Libcore.initCore chdirs the whole process to the data dir, so these
    // probes must anchor on the JVM launch directory, never the OS cwd.
    val launchDir = File(System.getProperty("user.dir").orEmpty()).absoluteFile
    return listOfNotNull(
        launchDir.resolve(relativePath),
        launchDir.parentFile?.resolve(relativePath),
    )
}

/**
 * Absolute locations probed by [resolveHusiCoreBinary], for error messages.
 */
internal fun describeHusiCoreSearchLocations(): String {
    val locations = buildList {
        add("packaged sibling of the launcher/app")
        devHusiCoreCandidates(husiCoreBinaryName()).forEach {
            add("${it.absolutePath} [isFile=${it.isFile} canExecute=${it.canExecute()}]")
        }
        add("PATH")
    }
    return locations.joinToString(", ")
}

/**
 * Directory containing the packaged anja sidecar library (`libhusicore.so` /
 * `libhusicore.dylib` / `husicore.dll`), or null when running from a fat
 * classpath jar (dev). Same sibling layout as [resolveHusiCoreBinary].
 */
internal fun resolvePackagedAnjaNativesDir(): File? {
    val libraryName = when (PlatformInfo.platform) {
        Platform.Windows -> "husicore.dll"
        Platform.MacOs -> "libhusicore.dylib"
        Platform.Linux -> "libhusicore.so"
        Platform.Android -> return null
    }
    return resolvePackagedCoreSibling(libraryName)?.parentFile?.absoluteFile
}

/**
 * Packaged-install sibling of the launcher / app tree (not the dev build dir).
 */
private fun resolvePackagedCoreSibling(fileName: String): File? {
    val launcher = fr.husi.resolvePackagedLauncherExecutable()
    if (launcher != null) {
        val sibling = launcher.parentFile?.resolve(fileName)
        if (sibling != null && sibling.isFile) {
            return sibling.absoluteFile
        }
    }

    val codeSource = CoreHostController::class.java.protectionDomain?.codeSource?.location
        ?: return null
    val runtimePath = runCatching {
        File(codeSource.toURI())
    }.getOrElse {
        File(codeSource.path)
    }
    val appDir = runtimePath.parentFile
        ?.takeIf { runtimePath.isFile && it.name == "app" }
        ?: return null
    val appRoot = appDir.parentFile ?: return null

    val searchDirs = when (PlatformInfo.platform) {
        Platform.Linux -> listOf(appRoot.resolve("bin"), appRoot)
        Platform.MacOs -> listOf(appRoot.resolve("MacOS"), appRoot)
        Platform.Windows -> listOf(appRoot)
        Platform.Android -> emptyList()
    }
    for (dir in searchDirs) {
        val candidate = dir.resolve(fileName)
        if (candidate.isFile) {
            return candidate.absoluteFile
        }
    }
    return null
}

private fun hostCoreBuildDirName(): String {
    val os = when (PlatformInfo.platform) {
        Platform.Linux -> "linux"
        Platform.MacOs -> "darwin"
        Platform.Windows -> "windows"
        Platform.Android -> "linux"
    }
    val arch = when (val raw = System.getProperty("os.arch").orEmpty().lowercase()) {
        "amd64", "x86_64" -> "amd64"
        "aarch64", "arm64" -> "arm64"
        else -> raw
    }
    return "${os}_$arch"
}

private fun resolveOnPath(binaryName: String): File? {
    val path = System.getenv("PATH") ?: return null
    val separator = File.pathSeparatorChar
    for (entry in path.split(separator)) {
        if (entry.isBlank()) continue
        val candidate = File(entry, binaryName)
        if (candidate.isFile && candidate.canExecuteOrWindows()) {
            return candidate.absoluteFile
        }
    }
    return null
}

private fun File.canExecuteOrWindows(): Boolean {
    return PlatformInfo.isWindows || canExecute()
}
