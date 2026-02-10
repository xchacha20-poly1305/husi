package fr.husi.fmt.shadowquic

import fr.husi.fmt.LOCALHOST4
import fr.husi.ktx.blankAsNull
import fr.husi.ktx.listByLineOrComma
import fr.husi.ktx.toJsonStringKxs
import fr.husi.libcore.Libcore
import fr.husi.logLevelString

fun ShadowQUICBean.buildShadowQUICConfig(port: Int, shouldProtect: Boolean, logLevel: Int): String {
    val paths = if (subProtocol == ShadowQUICBean.SUB_PROTOCOL_SUNNY_QUIC) {
        extraPaths.lines().filter { it.isNotBlank() }.takeIf { it.isNotEmpty() }
    } else {
        null
    }
    return mutableMapOf<String, Any?>(
        "inbound" to mapOf(
            "type" to "socks",
            "bind-addr" to "$LOCALHOST4:$port",
        ),
        "outbound" to mutableMapOf<String, Any?>(
            "type" to if (subProtocol == ShadowQUICBean.SUB_PROTOCOL_SHADOW_QUIC) "shadowquic" else "sunnyquic",
            "addr" to displayAddress(),
            "username" to username,
            "password" to password,
            "server-name" to sni.blankAsNull(),
            "alpn" to alpn.blankAsNull()?.listByLineOrComma(),
            "initial-mtu" to initialMTU.takeIf { it > 0 },
            "min-mtu" to minimumMTU.takeIf { it > 0 },
            "congestion-control" to congestionControl.blankAsNull(),
            "keep-alive-interval" to keepAliveInterval.takeIf { it > 0 },
            "extra-paths" to paths,
            "max-paths" to paths?.let { maxPaths.coerceIn(0, it.size).takeIf { it > 0 } },
            "zero-rtt" to zeroRTT.takeIf { it },
            "over-stream" to udpOverStream.takeIf { it },
            "mtu-discovery" to this.mtuDiscovery.takeIf { it },
            "gso" to gso,
            "protect-path" to if (shouldProtect) Libcore.ProtectPath else null,
        ),
        "log-level" to when (logLevel) {
            0, 1 -> "error"
            else -> logLevelString(logLevel)
        },
    ).toJsonStringKxs()
}
