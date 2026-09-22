package fr.husi.fmt.http

import fr.husi.fmt.buildHeader
import fr.husi.fmt.parseBoxOutbound
import fr.husi.fmt.parseBoxTLS
import fr.husi.fmt.parseHeader
import fr.husi.fmt.v2ray.setTLS
import fr.husi.ktx.JSONMap
import fr.husi.ktx.blankAsNull
import fr.husi.ktx.toJSONMap
import fr.husi.libcore.Libcore

fun parseHttp(link: String): HttpBean = HttpBean().apply {
    val url = Libcore.parseURL(link)

    serverAddress = url.host
    serverPort = url.ports.toIntOrNull() ?: if (url.scheme == "https") 443 else 80
    username = url.username
    password = url.password
    sni = url.queryParameter("sni")
    name = url.fragment
    setTLS(url.scheme == "https")
    path = url.path
}

fun HttpBean.toUri(): String {
    val url = Libcore.newURL(if (isTLS) "https" else "http").apply {
        host = serverAddress
    }

    if (serverPort in 1..65535) {
        url.ports = serverPort.toString()
    }

    username.blankAsNull()?.let { url.username = it }
    password.blankAsNull()?.let { url.password = it }
    path.blankAsNull()?.let { url.rawPath = it }
    sni.blankAsNull()?.let { url.addQueryParameter("sni", it) }
    name.blankAsNull()?.let { url.fragment = it }

    return url.string
}

fun parseHttpOutbound(json: JSONMap): HttpBean = HttpBean().apply {
    parseBoxOutbound(json) { key, value ->
        when (key) {
            "username" -> username = value.toString()
            "password" -> password = value.toString()
            "path" -> path = value.toString()
            "version" -> value.toString().toIntOrNull()
                ?.takeIf(HttpBean::isValidHttpVersion)
                ?.let { httpVersion = it }

            "disable_version_fallback" -> disableVersionFallback = value.toString().toBoolean()
            "headers" -> (value as? Map<*, *>)?.let {
                val parsedHeaders = parseHeader(it).toMutableMap()
                parsedHeaders.removeHostHeader()?.let { hostHeader -> host = hostHeader }
                headers = parsedHeaders.map { entry ->
                    entry.key + ":" + entry.value.joinToString(",")
                }.joinToString("\n")
            }

            "tls" -> {
                val tlsJson = (value as? Map<*, *>)?.let {
                    toJSONMap(it)
                } ?: return@parseBoxOutbound
                val tls = parseBoxTLS(tlsJson)
                if (tls.enabled != true) return@parseBoxOutbound

                setTLS(true)
                sni = tls.server_name.orEmpty()
                alpn = tls.alpn?.joinToString(",").orEmpty()
                utlsFingerprint = tls.utls?.fingerprint.orEmpty()
                allowInsecure = tls.insecure == true
                disableSNI = tls.disable_sni == true
                certificates = tls.certificate?.joinToString("\n").orEmpty()
                certificateSha256 = tls.certificate_sha256?.joinToString("\n").orEmpty()
                certPublicKeySha256 =
                    tls.certificate_public_key_sha256?.joinToString("\n").orEmpty()
                clientCert = tls.client_certificate?.joinToString("\n").orEmpty()
                clientKey = tls.client_key?.joinToString("\n").orEmpty()
                tls.reality?.let {
                    realityPublicKey = it.public_key.orEmpty()
                    realityShortID = it.short_id.orEmpty()
                }
                tls.ech?.let {
                    ech = it.enabled == true
                    echConfig = it.config?.joinToString("\n").orEmpty()
                }
            }
        }
    }
}

private const val HOST_HEADER = "Host"

class HttpRequestTarget(
    val path: String?,
    val headers: MutableMap<String, MutableList<String>>?,
)

fun HttpBean.buildRequestTarget(): HttpRequestTarget {
    val requestHeaders = buildHeader(headers).toMutableMap()
    val hostHeader = requestHeaders.removeHostHeader()
    if (httpVersion != HttpBean.HTTP_VERSION_1) {
        return HttpRequestTarget(path = null, headers = requestHeaders.ifEmpty { null })
    }

    val requestPath = path.blankAsNull()
    val requestHost = host.blankAsNull() ?: hostHeader
    if (requestPath == null && requestHost != null) {
        requestHeaders[HOST_HEADER] = mutableListOf(requestHost)
    }
    return HttpRequestTarget(path = requestPath, headers = requestHeaders.ifEmpty { null })
}

private fun MutableMap<String, MutableList<String>>.removeHostHeader(): String? {
    val hostKeys = keys.filter { it.equals(HOST_HEADER, ignoreCase = true) }
    val hostValue = hostKeys.firstNotNullOfOrNull { key ->
        getValue(key).firstNotNullOfOrNull { it.blankAsNull() }
    }
    hostKeys.forEach(::remove)
    return hostValue
}
