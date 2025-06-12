package io.nekohasekai.sagernet.ktx

import io.nekohasekai.sagernet.BuildConfig
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.fmt.AbstractBean
import io.nekohasekai.sagernet.fmt.LOCALHOST4
import libcore.Libcore
import libcore.URL
import moe.matsuri.nb4a.utils.NGUtil
import java.net.InetSocketAddress
import java.net.Socket
import java.security.KeyStore
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

var URL.pathSegments: List<String>
    get() = path.split("/").filter { it.isNotBlank() }
    set(value) {
        path = value.joinToString("/")
    }

fun URL.addPathSegments(vararg segments: String) {
    pathSegments = pathSegments.toMutableList().apply {
        addAll(segments)
    }
}

fun URL.queryParameter(key: String): String? {
    return queryParameterNotBlank(key).takeIf { it.isNotEmpty() }
}

fun URL.parseBoolean(key: String): Boolean = when (queryParameterNotBlank(key)) {
    "1", "true" -> true
    else -> false
}

fun currentSocks5(): URL? = if (!DataStore.serviceState.started) {
    null
} else {
    Libcore.newURL("socks5").apply {
        host = LOCALHOST4
        ports = DataStore.mixedPort.toString()

        // Avoid creating User field if not have.
        val username = DataStore.inboundUsername
        if (username.isNotEmpty()) {
            this.username = username
            password = DataStore.inboundPassword
        }
    }
}

fun String.isIpAddress(): Boolean {
    return NGUtil.isIpv4Address(this) || NGUtil.isIpv6Address(this)
}

fun String.isIpAddressV6(): Boolean {
    return NGUtil.isIpv6Address(this)
}

// [2001:4860:4860::8888] -> 2001:4860:4860::8888
fun String.unwrapIPV6Host(): String {
    if (startsWith("[") && endsWith("]")) {
        return substring(1, length - 1).unwrapIPV6Host()
    }
    return this
}

// [2001:4860:4860::8888] or 2001:4860:4860::8888 -> [2001:4860:4860::8888]
fun String.wrapIPV6Host(): String {
    val unwrapped = this.unwrapIPV6Host()
    return if (unwrapped.isIpAddressV6()) {
        "[$unwrapped]"
    } else {
        this
    }
}

fun AbstractBean.wrapUri(): String {
    return "${finalAddress.wrapIPV6Host()}:$finalPort"
}

fun mkPort(): Int {
    val socket = Socket()
    socket.reuseAddress = true
    socket.bind(InetSocketAddress(0))
    val port = socket.localPort
    socket.close()
    return port
}

val USER_AGENT by lazy { "husi/${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}; sing-box ${Libcore.versionBox()})" }

/**
Replace all version-about escapes in userAent
 */
fun generateUserAgent(userAgent: String): String {
    if (userAgent.isBlank()) return USER_AGENT
    return userAgent.replace("\$version", BuildConfig.VERSION_NAME)
        .replace("\$version_code", BuildConfig.VERSION_CODE.toString())
        .replace("\$box_version", Libcore.versionBox())
}

@OptIn(ExperimentalEncodingApi::class)
val systemCertificates by lazy {
    val certificates = mutableListOf<String>()
    val keyStore = KeyStore.getInstance("AndroidCAStore")
    if (keyStore != null) {
        keyStore.load(null, null)
        val aliases = keyStore.aliases()
        while (aliases.hasMoreElements()) {
            val cert = keyStore.getCertificate(aliases.nextElement())
            certificates.add(
                "-----BEGIN CERTIFICATE-----\n" + Base64.encode(cert.encoded) + "\n-----END CERTIFICATE-----"
            )
        }
    }
    certificates
}