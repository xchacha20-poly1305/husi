package fr.husi.fmt.shadowquic

import fr.husi.fmt.LOCALHOST4
import fr.husi.ktx.blankAsNull
import fr.husi.ktx.listByLineOrComma
import fr.husi.ktx.toStringPretty
import fr.husi.libcore.Libcore
import fr.husi.logLevelString
import org.json.JSONArray
import org.json.JSONObject

fun ShadowQUICBean.buildShadowQUICConfig(port: Int, shouldProtect: Boolean, logLevel: Int): String {
    return JSONObject().apply {
        put(
            "inbound",
            JSONObject().apply {
                put("type", "socks")
                put("bind-addr", "$LOCALHOST4:$port")
            },
        )
        put(
            "outbound",
            JSONObject().apply {
                put(
                    "type",
                    if (subProtocol == ShadowQUICBean.SUB_PROTOCOL_SHADOW_QUIC) {
                        "shadowquic"
                    } else {
                        "sunnyquic"
                    },
                )
                put("addr", displayAddress())
                put("username", username)
                put("password", password)
                sni.blankAsNull()?.let {
                    put("server-name", it)
                }
                alpn.blankAsNull()?.listByLineOrComma()?.let {
                    put("alpn", JSONArray(it))
                }
                initialMTU.takeIf { it > 0 }?.let {
                    put("initial-mtu", it)
                }
                minimumMTU.takeIf { it > 0 }?.let {
                    put("minimum-mtu", it)
                }
                congestionControl.blankAsNull()?.let {
                    put("congestion-control", it)
                }
                keepAliveInterval.takeIf { it > 0 }?.let {
                    put("keep-alive-interval", it)
                }
                if (subProtocol == ShadowQUICBean.SUB_PROTOCOL_SUNNY_QUIC) {
                    extraPaths.lines()
                        .filter { it.isNotBlank() }
                        .takeIf { it.isNotEmpty() }
                        ?.let { paths ->
                            put("extra-paths", JSONArray(paths))
                            maxPaths.coerceIn(0, paths.size).takeIf { it > 0 }?.let {
                                put("max-paths", it)
                            }
                        }
                }
                if (zeroRTT) {
                    put("zero-rtt", true)
                }
                if (udpOverStream) {
                    put("over-stream", true)
                }
                if (mtuDiscovery) {
                    put("mtu-discovery", true)
                }
                put("gso", gso)
                if (shouldProtect) {
                    put("protect-path", Libcore.ProtectPath)
                }
            },
        )
        put(
            "log-level",
            when (logLevel) {
                0, 1 -> "error"
                else -> logLevelString(logLevel)
            },
        )
    }.toStringPretty()
}
