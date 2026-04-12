package fr.husi.fmt.shadowquic

import fr.husi.database.DataStore
import fr.husi.fmt.LOCALHOST4
import fr.husi.ktx.blankAsNull
import fr.husi.ktx.listByLineOrComma
import fr.husi.ktx.toJsonStringKxs
import fr.husi.libcore.Libcore
import fr.husi.logLevelString

private const val BITS_PER_MEGABIT = 1_000_000L

fun ShadowQUICBean.buildShadowQUICConfig(port: Int, shouldProtect: Boolean, logLevel: Int): String {
    val paths = if (subProtocol == ShadowQUICBean.SUB_PROTOCOL_SUNNY_QUIC) {
        extraPaths.lines().filter { it.isNotBlank() }.takeIf { it.isNotEmpty() }
    } else {
        null
    }
    return buildMap<String, Any?> {
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
                put("max-paths", paths?.let { maxPaths.coerceIn(0, it.size).takeIf { it > 0 } })
                put("zero-rtt", zeroRTT.takeIf { it })
                put("over-stream", udpOverStream.takeIf { it })
                put("mtu-discovery", this@buildShadowQUICConfig.mtuDiscovery.takeIf { it })
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
    }.toJsonStringKxs()
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
