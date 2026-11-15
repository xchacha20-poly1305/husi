package fr.husi.ui.remote

import androidx.compose.runtime.staticCompositionLocalOf
import fr.husi.database.RemoteBackend
import fr.husi.database.RemoteServer
import fr.husi.ktx.emptyAsNull
import fr.husi.ktx.wrapIPV6Host
import kotlinx.browser.window
import org.w3c.dom.url.URL
import org.w3c.dom.url.URLSearchParams

val LocalRemoteSetupLink = staticCompositionLocalOf<RemoteSetupLink?> { null }

data class RemoteSetupLink(val server: RemoteServer, val addImmediately: Boolean)

fun readRemoteSetupLink(): RemoteSetupLink? = parseRemoteSetupLink(window.location.href)

fun parseRemoteSetupLink(value: String): RemoteSetupLink? {
    val location = readSetupLocation(value)
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

data class SetupLocation(
    val protocol: String,
    val route: String,
    val parameters: SetupParameters,
)

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

@OptIn(ExperimentalWasmJsInterop::class)
private fun readSetupLocation(value: String): SetupLocation {
    val url = URL(value)
    val fragment = url.hash.removePrefix("#")
    val route = fragment.substringBefore('?')
    val query = url.searchParams
    val fragmentQuery = URLSearchParams(fragment.substringAfter('?', "").toJsString())
    fun parameter(name: String): String? {
        val array = if (fragmentQuery.has(name)) {
            fragmentQuery
        } else {
            query
        }
        return array.getAll(name).toList().lastOrNull()?.toString()
    }
    return SetupLocation(
        protocol = url.protocol.removeSuffix(":"),
        route = route,
        parameters = SetupParameters(
            protocol = parameter("protocol"),
            hostname = parameter("hostname"),
            port = parameter("port").orEmpty(),
            secondaryPath = parameter("secondaryPath").orEmpty(),
            secret = parameter("secret").orEmpty(),
            type = parameter("type"),
            http = parameter("http"),
            https = parameter("https"),
        ),
    )
}

private fun buildSetupServerUrl(protocol: String, hostname: String, port: String, path: String): String? {
    return runCatching {
        val url = URL("$protocol://${hostname.wrapIPV6Host()}")
        url.port = port
        url.pathname = if (path.isEmpty()) {
            "/"
        } else {
            "/$path"
        }
        url.href.removeSuffix("/")
    }.getOrNull()
}
