package fr.husi.repository

import fr.husi.ktx.invariantDirectoryPathString
import fr.husi.libcore.Service
import kotlinx.coroutines.flow.StateFlow
import org.jetbrains.compose.resources.PluralStringResource
import org.jetbrains.compose.resources.StringResource
import java.io.File
import org.jetbrains.compose.resources.getPluralString as getComposePluralString
import org.jetbrains.compose.resources.getString as getComposeString

fun resolveDesktopRepository(): DesktopRepository = resolveRepository() as DesktopRepository

class DesktopRepository(
    val dataDir: File,
) : Repository {

    override val isMainProcess: Boolean = true
    override val isBgProcess: Boolean = true
    override val isTv = false

    /**
     * Desktop no longer hosts the core in-process. Kept nullable for the
     * shared [Repository] contract; always null on desktop after the session split.
     */
    override val boxService: Service? = null

    /** Working directory and socket parent for the out-of-process core host. */
    val coreDir: File by lazy {
        dataDir.resolve("core").apply { mkdirs() }
    }

    /**
     * Base path [fr.husi.core.BridgeCoreClient] dials (`…/core/` → `api.sock`).
     * Mutable so [CoreHostController] can point at the system daemon socket
     * parent (`/var/run/husi`) when a privileged daemon is live.
     */
    @Volatile
    private var coreSocketBasePathOverride: String? = null

    val sessionSocketBasePath: String
        get() = coreDir.invariantDirectoryPathString()

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

    override val cacheDir: File by lazy {
        dataDir.resolve("cache").apply { mkdirs() }
    }

    override val filesDir: File by lazy {
        dataDir.resolve("files").apply { mkdirs() }
    }

    override val externalAssetsDir: File by lazy {
        dataDir.resolve("external").apply { mkdirs() }
    }

    override fun resolveDatabaseFile(name: String): File {
        return dataDir.resolve(name)
    }

    override suspend fun getString(resource: StringResource) = getComposeString(resource)
    override suspend fun getString(resource: StringResource, vararg formatArgs: Any) =
        getComposeString(resource, *formatArgs)

    override suspend fun getPluralString(
        resource: PluralStringResource,
        quantity: Int,
        vararg formatArgs: Any,
    ) = getComposePluralString(resource, quantity, *formatArgs)

    override fun startService() {
        coreHostController.start()
    }

    override fun reloadService() {
        coreHostController.reload()
    }

    override fun stopService() {
        coreHostController.stop()
    }
}
