package fr.husi.cli

import fr.husi.CORE_SOCKET_NAME
import fr.husi.core.BridgeCoreClient
import fr.husi.core.CoreClient
import fr.husi.ktx.Logs
import fr.husi.libcore.Libcore
import fr.husi.platform.PlatformInfo
import fr.husi.repository.CoreHostController
import kotlinx.coroutines.runBlocking
import java.io.File

fun libcoreLoadFailureMessage(error: LinkageError): String {
    return buildString {
        appendLine("Husi could not load the libcore JNI library.")
        appendLine()
        appendLine("This usually means the desktop libcore package does not match this system,")
        appendLine("or developer made mistakes.")
        appendLine()
        appendLine("System: ${System.getProperty("os.name")} ${System.getProperty("os.arch")}")
        appendLine("Java: ${System.getProperty("java.version")}")
        appendLine("Error: ${error.message ?: error::class.simpleName}")
    }.trimEnd()
}

fun connectClient(socketBasePath: String): CoreClient? {
    val client = BridgeCoreClient(socketBasePath)
    runCatching {
        runBlocking { client.probe() }
    }.onFailure {
        // Not reaching a host is the normal case for a stopped husi, so this stays
        // at debug level; callers tell the user what they could not reach.
        Logs.d("probe core host at $socketBasePath: ${it.message}")
        runCatching {
            runBlocking { client.close() }
        }
        return null
    }
    return client
}

/**
 * Connects to whichever host the UI would have attached to: the privileged
 * system daemon when its socket answers, otherwise the per-user session host.
 *
 * [CoreHostController] resolves the same order, but it stores the winner in its
 * own process. A separate process — a CLI subcommand, a scheduled task run —
 * inherits nothing, so it has to repeat the probe rather than assume the
 * session path.
 *
 * @return null when neither host answers.
 */
fun connectExistingHost(sessionBasePath: String): CoreClient? {
    val daemonBasePath = CoreHostController.daemonSocketBasePath()
    if (CoreHostController.daemonSocketPresent(daemonBasePath)) {
        connectClient(daemonBasePath)?.let { return it }
    }
    return connectClient(sessionBasePath)
}

/** Both socket paths [connectExistingHost] tries, for user-facing diagnostics. */
fun hostSocketPaths(sessionBasePath: String): List<String> {
    val daemonBasePath = CoreHostController.daemonSocketBasePath()
    val daemonSocket = if (PlatformInfo.isWindows) {
        // The Windows daemon endpoint is the named pipe itself, not a directory.
        daemonBasePath
    } else {
        File(daemonBasePath, CORE_SOCKET_NAME).path
    }
    return listOf(daemonSocket, File(sessionBasePath, CORE_SOCKET_NAME).path)
}

fun connectRemoteClient(serverURL: String, secret: String): CoreClient {
    val client = BridgeCoreClient(
        basePath = null,
        bridgeFactory = { Libcore.newRemoteBridgeClient(serverURL, secret) },
    )
    try {
        runBlocking { client.probe() }
    } catch (e: Exception) {
        runCatching { runBlocking { client.close() } }
        throw e
    }
    return client
}