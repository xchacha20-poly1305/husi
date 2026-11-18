package fr.husi.repository

import fr.husi.ktx.deleteRecursivelyBlocking
import fr.husi.ktx.invariantDirectoryPathString
import fr.husi.ktx.listFilesOrNull
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.createDirectories
import io.github.vinceglb.filekit.name
import io.github.vinceglb.filekit.resolve
import kotlinx.coroutines.flow.StateFlow

fun resolveDesktopRepository(): DesktopRepository = resolveRepository() as DesktopRepository

open class DesktopRepository(
    val dataDir: PlatformFile,
    /**
     * Identifies a secondary instance started with `--many`, which owns a
     * private core host instead of the well-known one. Null for the primary
     * instance — the single-instance lock guarantees there is only one, so it
     * keeps `core/` itself and stays the host CLI subcommands dial.
     */
    private val instanceId: String? = null,
) : LocalRepository {

    override val isMainProcess: Boolean = true
    override val isBgProcess: Boolean = true
    override val isTv = false

    /**
     * Root of everything the out-of-process core host owns, and the working
     * directory of the primary instance's host. See [coreRunDir].
     */
    val coreDir: PlatformFile by lazy {
        dataDir.resolve("core").apply { createDirectories() }
    }

    /**
     * Where this instance's session host keeps its socket and its plugin files.
     * A session host deletes and rebinds the socket it is given, so two
     * instances pointed at one directory would steal each other's host and tear
     * it down on exit: a secondary instance gets a private directory instead.
     */
    val coreRunDir: PlatformFile by lazy {
        val dir = instanceId?.let { coreDir.resolve(INSTANCE_DIR_NAME).resolve(it) } ?: coreDir
        dir.apply { createDirectories() }
    }

    /**
     * Base path [fr.husi.core.WireCoreClient] dials (`…/core/` → `api.sock`).
     * Mutable so [CoreHostController] can point at the system daemon socket
     * parent (`/var/run/husi`) when a privileged daemon is live.
     */
    @Volatile
    private var coreSocketBasePathOverride: String? = null

    val sessionSocketBasePath: String
        get() = coreRunDir.invariantDirectoryPathString()

    fun releaseCoreRunDir() {
        if (instanceId == null) return
        coreRunDir.deleteRecursivelyBlocking()
    }

    fun pruneStaleCoreRunDirs() {
        val instancesDir = coreDir.resolve(INSTANCE_DIR_NAME)
        val leftovers = instancesDir.listFilesOrNull() ?: return
        for (dir in leftovers) {
            if (dir == coreRunDir) continue
            val pid = dir.name.toLongOrNull() ?: continue
            if (ProcessHandle.of(pid).map { it.isAlive }.orElse(false)) continue
            dir.deleteRecursivelyBlocking()
        }
    }

    var coreSocketBasePath: String
        get() = coreSocketBasePathOverride ?: sessionSocketBasePath
        set(value) {
            coreSocketBasePathOverride = value
        }

    fun resetCoreSocketBasePath() {
        coreSocketBasePathOverride = null
    }

    internal val coreHostController by lazy {
        CoreHostController(this)
    }

    val coreHostState: StateFlow<CoreHostState>
        get() = coreHostController.hostState

    /**
     * Re-probe / attach to a system daemon after elevated install or update.
     * Stops a session-mode service if one was running; leaves a daemon-hosted
     * service alone. See [CoreHostController.reattachDaemon].
     */
    suspend fun reattachDaemon() {
        coreHostController.reattachDaemon()
    }

    /**
     * Take over a daemon owned by another local user. See
     * [CoreHostController.takeOverDaemon].
     */
    suspend fun takeOverDaemon() {
        coreHostController.takeOverDaemon()
    }

    override val cacheDir: PlatformFile by lazy {
        dataDir.resolve("cache").apply { createDirectories() }
    }

    override val filesDir: PlatformFile by lazy {
        dataDir.resolve("files").apply { createDirectories() }
    }

    override val externalAssetsDir: PlatformFile by lazy {
        dataDir.resolve("external").apply { createDirectories() }
    }

    override fun resolveDatabaseFile(name: String): PlatformFile {
        return dataDir.resolve(name)
    }

    override fun startService() {
        coreHostController.start()
    }

    override fun reloadService() {
        coreHostController.reload()
    }

    override fun stopService() {
        coreHostController.stop()
    }

    companion object {
        private const val INSTANCE_DIR_NAME = "instances"

        fun currentInstanceId(): String = ProcessHandle.current().pid().toString()
    }
}
