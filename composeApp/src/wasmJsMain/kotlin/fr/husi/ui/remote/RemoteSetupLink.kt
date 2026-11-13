@file:OptIn(ExperimentalWasmJsInterop::class)

package fr.husi.ui.remote

import androidx.compose.runtime.staticCompositionLocalOf
import fr.husi.database.RemoteBackend
import fr.husi.database.RemoteServer
import fr.husi.ktx.emptyAsNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private val setupLinkJson = Json {
    ignoreUnknownKeys = true
}

val LocalRemoteSetupLink = staticCompositionLocalOf<RemoteSetupLink?> { null }

data class RemoteSetupLink(val server: RemoteServer, val addImmediately: Boolean)

fun readRemoteSetupLink(): RemoteSetupLink? = parseRemoteSetupLink(pageUrl())

fun parseRemoteSetupLink(value: String): RemoteSetupLink? {
    val location = setupLinkJson.decodeFromString<SetupLocation>(readSetupLocation(value))
    if (location.route !in listOf("", "/", "/setup", "/setup/")) {
        return null
    }
    val parameters = location.parameters
    val hostname = parameters.hostname?.trim().emptyAsNull() ?: return null
    val protocol = when {
        parameters.protocol != null -> parameters.protocol.lowercase()
        parameters.https != null -> "https"
        parameters.http != null -> "http"
        else -> location.protocol
    }
    val backend = when (parameters.type?.lowercase()) {
        "clash" -> RemoteBackend.CLASH
        "sing-box" -> RemoteBackend.SING_BOX
        else -> RemoteBackend.AUTO
    }
    val port = parameters.port
    if (port.isNotEmpty() && (port.toIntOrNull() !in 1..65535)) {
        return null
    }
    val path = parameters.secondaryPath.trim('/')
    val url = buildSetupServerUrl(protocol, hostname, port, path) ?: return null
    return RemoteSetupLink(
        server = RemoteServer(
            name = url,
            url = url,
            secret = parameters.secret,
            backend = backend,
        ),
        addImmediately = location.route == "/setup" || location.route == "/setup/",
    )
}

@Serializable
data class SetupLocation(
    val protocol: String,
    val route: String,
    val parameters: SetupParameters,
)

@Serializable
data class SetupParameters(
    val protocol: String? = null,
    val hostname: String? = null,
    val port: String = "",
    val secondaryPath: String = "",
    val secret: String = "",
    val type: String? = null,
    val http: String? = null,
    val https: String? = null,
)

@JsFun("() => location.href")
private external fun pageUrl(): String

@JsFun("""(value) => {
    const url = new URL(value);
    const fragment = url.hash.slice(1);
    const separator = fragment.indexOf('?');
    const route = separator < 0 ? fragment : fragment.slice(0, separator);
    const parameters = new URLSearchParams(url.search);
    if (separator >= 0) {
        for (const [key, value] of new URLSearchParams(fragment.slice(separator + 1))) {
            parameters.set(key, value);
        }
    }
    return JSON.stringify({
        protocol: url.protocol.slice(0, -1), route,
        parameters: Object.fromEntries(parameters)
    });
}""")
private external fun readSetupLocation(value: String): String

@JsFun("""(protocol, hostname, port, path) => {
    try {
        const host = hostname.includes(':') && !hostname.startsWith('[') ? '[' + hostname + ']' : hostname;
        const url = new URL(protocol + '://' + host);
        if (url.username || url.password || url.pathname !== '/' || url.search || url.hash) return null;
        url.port = port;
        url.pathname = path ? '/' + path : '/';
        return url.href.endsWith('/') ? url.href.slice(0, -1) : url.href;
    } catch (_) {
        return null;
    }
}""")
private external fun buildSetupServerUrl(protocol: String, hostname: String, port: String, path: String): String?
