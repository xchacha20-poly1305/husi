package fr.husi.cli

import fr.husi.core.BridgeCoreClient
import fr.husi.core.CoreClient
import fr.husi.ktx.Logs
import kotlinx.coroutines.runBlocking

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

fun connectExistingClient(socketBasePath: String): CoreClient? {
    val client = BridgeCoreClient(socketBasePath)
    runCatching {
        runBlocking { client.probe() }
    }.onFailure {
        Logs.w("probe existing desktop instance", it)
        runCatching {
            runBlocking { client.close() }
        }
        return null
    }
    return client
}