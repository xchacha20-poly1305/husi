package io.nekohasekai.sagernet.fmt.wireguard

import moe.matsuri.nb4a.SingBoxOptions
import moe.matsuri.nb4a.utils.Util
import moe.matsuri.nb4a.utils.listByLineOrComma

fun genReserved(anyStr: String): String {
    try {
        val list = anyStr.listByLineOrComma()
        val ba = ByteArray(3)
        if (list.size == 3) {
            list.forEachIndexed { index, s ->
                val i = s
                    .replace("[", "")
                    .replace("]", "")
                    .replace(" ", "")
                    .toIntOrNull() ?: return anyStr
                ba[index] = i.toByte()
            }
            return Util.b64EncodeOneLine(ba)
        } else {
            return anyStr
        }
    } catch (_: Exception) {
        return anyStr
    }
}

fun buildSingBoxEndpointWireGuardBean(bean: WireGuardBean): SingBoxOptions.Endpoint_WireGuardOptions {
    return SingBoxOptions.Endpoint_WireGuardOptions().apply {
        type = bean.outboundType()
        peers = listOf(SingBoxOptions.WireGuardPeer().apply {
            address = bean.serverAddress
            port = bean.serverPort
            public_key = bean.publicKey
            pre_shared_key = bean.preSharedKey
            allowed_ips = listOf(
                "0.0.0.0/0",
                "::/0",
            )
            if (bean.reserved.isNotBlank()) reserved = genReserved(bean.reserved)
        })
        listen_port = bean.listenPort.takeIf { it > 0 }
        address = bean.localAddress.listByLineOrComma()
        private_key = bean.privateKey
        mtu = bean.mtu
    }
}
