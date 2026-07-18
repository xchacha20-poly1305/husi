@file:Suppress("UNCHECKED_CAST")

package fr.husi.fmt.wireguard

import fr.husi.fmt.SingBoxOptions
import fr.husi.fmt.listable
import fr.husi.ktx.JSONMap
import fr.husi.ktx.b64EncodeOneLine
import fr.husi.ktx.blankAsNull
import fr.husi.ktx.listByLineOrComma
import fr.husi.ktx.applyDefaultValues
import org.ini4j.Ini
import java.io.StringReader

fun parseWireGuardConfig(conf: String): List<WireGuardBean> {
    val ini = Ini(StringReader(conf))
    val iface = ini["Interface"] ?: error("Missing 'Interface' selection")
    val bean = WireGuardBean().applyDefaultValues()
    val localAddresses = iface.getAll("Address")
    if (localAddresses.isNullOrEmpty()) error("Empty address in 'Interface' selection")
    bean.localAddress = localAddresses.flatMap {
        it.split(",").map { address ->
            address.trim()
        }
    }.joinToString("\n")
    bean.privateKey = iface["PrivateKey"].orEmpty()
    bean.mtu = iface["MTU"]?.toIntOrNull() ?: 1408
    bean.listenPort = iface["ListenPort"]?.toIntOrNull() ?: 0
    val peers = ini.getAll("Peer")
    if (peers.isNullOrEmpty()) error("Missing 'Peer' selections")
    val beans = mutableListOf<WireGuardBean>()
    loopPeer@ for (peer in peers) {
        val peerBean = bean.clone()
        for ((keyName, keyValue) in peer) {
            when (keyName.lowercase()) {
                "endpoint" -> {
                    peerBean.serverPort = keyValue.substringAfterLast(":", "").toIntOrNull()
                        ?: continue@loopPeer
                    peerBean.serverAddress = keyValue.substringBeforeLast(":")
                }

                "publickey" -> peerBean.publicKey = keyValue ?: continue@loopPeer
                "presharedkey" -> peerBean.preSharedKey = keyValue
                "persistentkeepalive" -> {
                    peerBean.persistentKeepaliveInterval = keyValue.toIntOrNull() ?: 0
                }
            }
        }
        beans.add(peerBean.applyDefaultValues())
    }
    if (beans.isEmpty()) error("Empty available peer list")
    return beans
}

fun genReserved(anyStr: String): String {
    try {
        val list = anyStr.listByLineOrComma()
        val bytes = ByteArray(3)
        if (list.size == 3) {
            list.forEachIndexed { index, s ->
                val i = s
                    .replace("[", "")
                    .replace("]", "")
                    .replace(" ", "")
                    .toIntOrNull() ?: return anyStr
                bytes[index] = i.toByte()
            }
            return bytes.b64EncodeOneLine()
        } else {
            return anyStr
        }
    } catch (_: Exception) {
        return anyStr
    }
}

fun buildSingBoxEndpointWireGuardBean(bean: WireGuardBean): SingBoxOptions.Endpoint_WireGuardOptions {
    return SingBoxOptions.Endpoint_WireGuardOptions().apply {
        type = SingBoxOptions.TYPE_WIREGUARD
        peers = mutableListOf(
            SingBoxOptions.WireGuardPeer().apply {
                address = bean.serverAddress
                port = bean.serverPort
                public_key = bean.publicKey
                pre_shared_key = bean.preSharedKey.blankAsNull()
                allowed_ips = mutableListOf(
                    "0.0.0.0/0",
                    "::/0",
                )
                bean.persistentKeepaliveInterval.takeIf { it > 0 }?.let {
                    persistent_keepalive_interval = it
                }
                bean.reserved.blankAsNull()?.let { reserved = genReserved(it) }
            },
        )
        listen_port = bean.listenPort.takeIf { it > 0 }
        address = bean.localAddress.listByLineOrComma().toMutableList()
        private_key = bean.privateKey
        mtu = bean.mtu
    }
}

fun parseWireGuardEndpoint(json: JSONMap): WireGuardBean? {
    val peer = (json["peers"] as? List<*>)?.firstOrNull() as? JSONMap ?: return null

    val bean = WireGuardBean()
    bean.name = json["tag"].toString()
    bean.mtu = json["mtu"]?.toString()?.toIntOrNull() ?: 0
    bean.localAddress = listable<String>(json["address"])?.joinToString("\n").orEmpty()
    bean.listenPort = json["listen_port"]?.toString()?.toIntOrNull() ?: 0
    bean.privateKey = json["private_key"]?.toString().orEmpty()

    for (entry in peer) {
        val value = entry.value ?: continue
        when (entry.key) {
            "address" -> bean.serverAddress = value.toString()
            "port" -> bean.serverPort = value.toString().toInt()
            "public_key" -> bean.publicKey = value.toString()
            "pre_shared_key" -> bean.preSharedKey = value.toString()
            "persistent_keepalive_interval" -> value.toString().toIntOrNull()?.let {
                bean.persistentKeepaliveInterval = it
            }

            "reserved" -> bean.reserved = when (value) {
                is String -> value

                is List<*> -> value.joinToString(",") {
                    it.toString().trim()
                }

                else -> ""
            }
        }
    }

    return bean
}
