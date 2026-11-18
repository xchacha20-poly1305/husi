package fr.husi.remote

import fr.husi.proto.daemon.GrpcStartedServiceClient
import fr.husi.proto.daemon.Version
import kotlinx.browser.window
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.w3c.dom.url.URL
import kotlin.time.Duration.Companion.seconds

internal class BrowserClient(
    private val openChannel: (String, String) -> BrowserGrpcClient = { url, secret ->
        BrowserGrpcClient(url, secret)
    },
) : RemoteClient {
    private var channel: BrowserGrpcClient? = null

    init {
        window.addEventListener("pagehide") {
            close()
        }
    }

    override val service get() = GrpcStartedServiceClient(channel ?: throw closedSession())

    override suspend fun connect(url: String, secret: String): Version {
        close()
        val opened = openChannel(normalizeUrl(url), secret)
        channel = opened
        try {
            val version = probe(opened)
            currentCoroutineContext().ensureActive()
            if (channel !== opened) {
                throw closedSession()
            }
            return version
        } catch (failure: Throwable) {
            if (channel === opened) {
                channel = null
            }
            opened.close()
            throw failure
        }
    }

    override fun close() {
        val previous = channel
        channel = null
        previous?.close()
    }

    override suspend fun testConnection(url: String, secret: String): String {
        return openChannel(normalizeUrl(url), secret).use {
            probe(it).version
        }
    }

    private suspend fun probe(opened: BrowserGrpcClient): Version {
        val call = GrpcStartedServiceClient(opened).GetVersion()
        (call.timeout as BrowserCallTimeout).duration = PROBE_TIMEOUT
        return call.execute(Unit)
    }

    private companion object {
        val PROBE_TIMEOUT = 10.seconds
    }

    override fun normalizeUrl(value: String): String {
        val url = URL(value)
        require(url.protocol == "http:" || url.protocol == "https:") {
            "Use an HTTP or HTTPS API URL"
        }
        return url.href.removeSuffix("/")
    }
}
