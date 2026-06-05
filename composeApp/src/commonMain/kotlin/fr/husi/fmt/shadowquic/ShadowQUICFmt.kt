package fr.husi.fmt.shadowquic

import fr.husi.database.DataStore
import fr.husi.fmt.LOCALHOST4
import fr.husi.ktx.JSONMap
import fr.husi.ktx.blankAsNull
import fr.husi.ktx.listByLineOrComma
import fr.husi.ktx.queryParameterNotBlank
import fr.husi.ktx.toJsonStringKxs
import fr.husi.libcore.Libcore
import fr.husi.logLevelString
import java.io.File

private const val BITS_PER_MEGABIT = 1_000_000L
private const val DEFAULT_SHARE_MTU = 1280

/**
Official ShadowQUIC share-link format: https://github.com/RealBikiniBottom/QuicProxy/discussions/2

But after decompiling the `QuicProxy` APK (by GPT-5.5), we found several deviations
from the official standard. The current code follows the official standard for
parsing and generating share links. However, if compatibility with the `QuicProxy` APK
is desired, be aware of these differences:

1. `sni` field:
- Official: REQUIRED field.
- APK: OPTIONAL. When missing or empty, defaults to `host` as SNI.

2. `zero_rtt` semantics:
- Official: "any value means true" (presence-based).
- APK: parses as boolean string (true/false/1/0/yes/no). Invalid values error.

3. `mtu` default value:
- Official: 1280 (used for both min-mtu and initial-mtu).
- APK: 1380 (written to both min_mtu and initial_mtu in core config).

4. `udp_mode` validation:
- Official: enum must be "stream" or "datagram".
- APK: no enum check. Invalid values are saved as-is; kernel may fail later.

5. `udp_mod` vs `udp_mode`:
- Official: field name is `udp_mode`.
- APK: reads `udp_mode` first, falls back to non-standard `udp_mod`. Exports as `udp_mod`.

6. `alpn` validation:
- Official: valid values are "h3", "h2", "http/1.1".
- APK: splits by comma, trims items, but does NOT validate enum. Unknown values accepted.

7. fragment / tag:
- Official: examples show fragment, no strict requirement mentioned.
- APK: REQUIRED. Errors if missing. Also accepts `tag` query param as fallback.
 */
fun parseShadowQUIC(link: String): ShadowQUICBean {
    val url = Libcore.parseURL(link)
    return ShadowQUICBean().apply {
        subProtocol = ShadowQUICBean.SUB_PROTOCOL_SHADOW_QUIC
        name = url.fragment
        username = url.username
        password = url.password
        serverAddress = url.host
        serverPort = url.ports.toIntOrNull() ?: 443
        sni = url.queryParameterNotBlank("sni") ?: error("shadowquic sni is empty")
        udpOverStream = when (url.queryParameterNotBlank("udp_mode")) {
            "stream", null -> true
            "datagram" -> false
            else -> true // ?
        }
        // "不填为 false（不开启），填任意内容就为 true（开启）"
        zeroRTT = url.queryParameterNotBlank("zero_rtt") != null
        val mtu = url.queryParameterNotBlank("mtu")?.toIntOrNull() ?: DEFAULT_SHARE_MTU
        initialMTU = mtu
        minimumMTU = mtu
        alpn = url.queryParameter("alpn")

        require(username.isNotBlank()) { "shadowquic username is empty" }
        require(password.isNotBlank()) { "shadowquic password is empty" }
        require(serverAddress.isNotBlank()) { "shadowquic server address is empty" }
    }
}

fun ShadowQUICBean.toUri(): String {
    if (subProtocol != ShadowQUICBean.SUB_PROTOCOL_SHADOW_QUIC) {
        error("SunnyQUIC does not support standard share links")
    }
    // "推荐优先使用 sq"
    return Libcore.newURL("sq").apply {
        username = this@toUri.username
        password = this@toUri.password
        host = serverAddress
        ports = serverPort.toString()
        addQueryParameter("sni", sni)
        addQueryParameter(
            "udp_mode",
            if (udpOverStream) {
                "stream"
            } else {
                "datagram"
            },
        )
        if (zeroRTT) {
            addQueryParameter("zero_rtt", "true")
        }
        if (initialMTU > 0 && initialMTU == minimumMTU) {
            addQueryParameter("mtu", initialMTU.toString())
        }
        alpn.blankAsNull()?.let {
            addQueryParameter("alpn", it)
        }
        fragment = name.blankAsNull()
    }.string
}

fun ShadowQUICBean.buildShadowQUICConfig(port: Int, shouldProtect: Boolean, logLevel: Int): String {
    return buildShadowQUICConfig(port, shouldProtect, logLevel, null)
}

fun ShadowQUICBean.buildShadowQUICConfig(
    port: Int,
    shouldProtect: Boolean,
    logLevel: Int,
    cacheFile: ((type: String) -> File)?,
): String {
    val paths = if (subProtocol == ShadowQUICBean.SUB_PROTOCOL_SUNNY_QUIC) {
        extraPaths.lines().filter { it.isNotBlank() }.takeIf { it.isNotEmpty() }
    } else {
        null
    }
    val certPath = if (subProtocol == ShadowQUICBean.SUB_PROTOCOL_SUNNY_QUIC) {
        cacheFile?.let {
            certificates.blankAsNull()?.let { certs ->
                val certFile = cacheFile("cert.pem")
                certFile.writeText(certs)
                certFile.absolutePath
            }
        }
    } else {
        null
    }
    val config: JSONMap = buildMap<String, Any?> {
        put(
            "inbound",
            buildMap<String, Any?> {
                put("type", "socks")
                put("bind-addr", "$LOCALHOST4:$port")
            },
        )
        put(
            "outbound",
            buildMap {
                put(
                    "type",
                    if (subProtocol == ShadowQUICBean.SUB_PROTOCOL_SHADOW_QUIC) "shadowquic" else "sunnyquic",
                )
                put("addr", displayAddress())
                put("username", username)
                put("password", password)
                put("server-name", sni.blankAsNull())
                put("alpn", alpn.blankAsNull()?.listByLineOrComma())
                put("initial-mtu", initialMTU.takeIf { it > 0 })
                put("min-mtu", minimumMTU.takeIf { it > 0 })
                put("congestion-control", buildCongestionControl())
                put("keep-alive-interval", keepAliveInterval.takeIf { it > 0 })
                put("extra-paths", paths)
                put("max-path-num", paths?.let { maxPaths.coerceIn(0, it.size).takeIf { it > 0 } })
                put("cert-path", certPath)
                put("zero-rtt", zeroRTT.takeIf { it })
                put("over-stream", udpOverStream.takeIf { it })
                if (mtuDiscovery) {
                    put("mtu-discovery", true)
                    put("blackhole-detection", blackholeDetection.takeIf { it })
                }
                put("gso", gso)
                put("protect-path", if (shouldProtect) Libcore.ProtectPath else null)
            },
        )
        put(
            "log-level",
            when (logLevel) {
                0, 1 -> "error"
                else -> logLevelString(logLevel)
            },
        )
    }.toMutableMap()
    return config.toJsonStringKxs()
}

private fun ShadowQUICBean.buildCongestionControl(): Any? {
    if (congestionControl != ShadowQUICBean.CONGESTION_CONTROL_BRUTAL) {
        return congestionControl.blankAsNull()
    }
    return buildMap<String, Any?> {
        put(
            ShadowQUICBean.CONGESTION_CONTROL_BRUTAL,
            buildMap<String, Any?> {
                unifyBrutalBandwidthBps(DataStore.uploadSpeed)?.let {
                    put("bandwidth", it)
                }
            },
        )
    }
}

private fun unifyBrutalBandwidthBps(speed: Int): Long? {
    // DataStore speed follows hysteria2's "mbps" convention, while shadowquic's "100m" shorthand is
    // parsed with a 1024^2 multiplier in Rust. Emit raw decimal bps here to keep the configured unit exact.
    return speed.takeIf { it > 0 }?.toLong()?.times(BITS_PER_MEGABIT)
}
