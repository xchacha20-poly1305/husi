package io.nekohasekai.sagernet.fmt.shadowquic

import io.nekohasekai.sagernet.fmt.LOCALHOST4
import io.nekohasekai.sagernet.ktx.blankAsNull
import io.nekohasekai.sagernet.ktx.listByLineOrComma
import io.nekohasekai.sagernet.ktx.mapX
import io.nekohasekai.sagernet.logLevelString
import libcore.Libcore

fun ShadowQUICBean.buildShadowQUICConfig(port: Int, shouldProtect: Boolean, logLevel: Int): String =
    buildString {
        // If one day we re-add yaml library, we can use that instead of buildString

        append("inbound:\n")
        append("    type: socks\n")
        append("    bind-addr: \"$LOCALHOST4:$port\"\n")

        append("outbound:\n")
        append("    type: shadowquic\n")
        append("    addr: \"${displayAddress()}\"\n")
        append("    username: \"$username\"\n")
        append("    password: \"$password\"\n")
        sni.blankAsNull()?.let {
            append("    server-name: \"$it\"\n")
        }
        alpn.blankAsNull()?.listByLineOrComma()?.mapX { "\"$it\"" }?.let { yamlAlpn ->
            append("    alpn: [${yamlAlpn.joinToString(", ")}]\n")
        }
        initialMTU?.takeIf { it > 0 }?.let {
            append("    initial-mtu: $it\n")
        }
        minimumMTU?.takeIf { it > 0 }?.let {
            append("    minimum-mtu: $it\n")
        }
        congestionControl.blankAsNull()?.let {
            append("    congestion-control: \"$it\"\n")
        }
        if (zeroRTT) {
            append("    zero-rtt: true\n")
        }
        if (udpOverStream) {
            append("    over-stream: true\n")
        }
        if (shouldProtect) {
            append("    protect-path: \"${Libcore.ProtectPath}\"\n")
        }

        when (logLevel) {
            0, 1 -> "error"
            else -> logLevelString(logLevel)
        }.let {
            append("log-level: \"$it\"\n")
        }
    }