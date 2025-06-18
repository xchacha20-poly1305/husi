package io.nekohasekai.sagernet.fmt.wireguard

import io.nekohasekai.sagernet.fmt.listable
import io.nekohasekai.sagernet.ktx.JSONMap
import io.nekohasekai.sagernet.ktx.blankAsNull
import io.nekohasekai.sagernet.ktx.map
import io.nekohasekai.sagernet.ktx.mapX
import moe.matsuri.nb4a.SingBoxOptions
import moe.matsuri.nb4a.utils.Util
import moe.matsuri.nb4a.utils.listByLineOrComma
import org.json.JSONArray

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
            pre_shared_key = bean.preSharedKey.blankAsNull()
            allowed_ips = listOf(
                "0.0.0.0/0",
                "::/0",
            )
            bean.persistentKeepaliveInterval.takeIf { it > 0 }?.let {
                persistent_keepalive_interval = it
            }
            bean.reserved.blankAsNull()?.let { reserved = genReserved(it) }
        })
        listen_port = bean.listenPort.takeIf { it > 0 }
        address = bean.localAddress.listByLineOrComma()
        private_key = bean.privateKey
        mtu = bean.mtu
    }
}

fun parseWireGuardEndpoint(json: JSONMap): WireGuardBean? {
    val peer = (json["peers"] as? JSONArray)?.optJSONObject(0) ?: return null

    val bean = WireGuardBean()
    bean.name = json["tag"].toString()
    bean.mtu = json["mtu"]?.toString()?.toIntOrNull()
    bean.localAddress = listable<String>(json["address"])?.joinToString("\n")
    bean.listenPort = json["listen_port"]?.toString()?.toIntOrNull()
    bean.privateKey = json["private_key"]?.toString()

    for (entry in peer.map) {
        val value = entry.value ?: continue
        when (entry.key) {
            "address" -> bean.serverAddress = value.toString()
            "port" -> bean.serverPort = value.toString().toInt()
            "public_key" -> bean.publicKey = value.toString()
            "pre_shared_key" -> bean.preSharedKey = value.toString()
            "persistent_keepalive_interval" -> {
                bean.persistentKeepaliveInterval = value.toString().toIntOrNull()
            }
            "reserved" -> bean.reserved = when (value) {
                is String -> value

                is List<*> -> value.mapX {
                    it.toString().trim()
                }.joinToString(",")

                else -> null
            }
        }
    }

    return bean
}