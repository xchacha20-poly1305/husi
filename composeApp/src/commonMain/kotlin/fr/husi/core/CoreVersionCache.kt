package fr.husi.core

import fr.husi.ktx.Logs
import fr.husi.proto.v1.GetVersionResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.koin.core.context.GlobalContext

class CoreVersionCache(private val coreClient: CoreClient) {

    private val fetchMutex = Mutex()

    @Volatile
    private var version: GetVersionResponse? = null

    /** Null when the core host cannot be reached. */
    suspend fun get(): GetVersionResponse? {
        version?.let { return it }
        return fetchMutex.withLock {
            version ?: fetch()?.also { version = it }
        }
    }

    private suspend fun fetch(): GetVersionResponse? {
        return try {
            coreClient.getVersion()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logs.w("get core version", e)
            null
        }
    }
}

fun resolveCoreVersionCache(): CoreVersionCache = GlobalContext.get().get()
