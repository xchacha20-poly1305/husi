package io.nekohasekai.sagernet.fmt.trojan_go

import io.nekohasekai.sagernet.IPv6Mode
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.fmt.LOCALHOST4
import io.nekohasekai.sagernet.ktx.*
import libcore.Libcore
import libcore.URL
import moe.matsuri.nb4a.Protocols
import org.json.JSONArray
import org.json.JSONObject

fun parseTrojanGo(url: URL): TrojanGoBean {
    return TrojanGoBean().apply {
        serverAddress = url.host
        serverPort = url.ports.toIntOrNull() ?: 443
        try {
            password = url.username
        } catch (_: Exception) {
        }
        url.queryParameterNotBlank("sni").let {
            sni = it
        }
        url.queryParameterNotBlank("type").let { lType ->
            type = lType

            when (type) {
                "ws" -> {
                    url.queryParameterNotBlank("host").let {
                        host = it
                    }
                    url.queryParameterNotBlank("path").let {
                        path = it
                    }
                }

                else -> {
                }
            }
        }
        url.queryParameterNotBlank("encryption").let {
            encryption = it
        }
        url.queryParameterNotBlank("plugin").let {
            plugin = it
        }
        url.fragment.takeIf { !it.isNullOrBlank() }.let {
            name = it
        }
    }
}

fun TrojanGoBean.toUri(): String {
    val builder = Libcore.newURL("trojan-go").apply {
        username = this@toUri.password
        host = serverAddress
        ports = serverPort.toString()
    }
    if (sni.isNotBlank()) {
        builder.addQueryParameter("sni", sni)
    }
    if (type.isNotBlank() && type != "original") {
        builder.addQueryParameter("type", type)

        when (type) {
            "ws" -> {
                if (host.isNotBlank()) {
                    builder.addQueryParameter("host", host)
                }
                if (path.isNotBlank()) {
                    builder.addQueryParameter("path", path)
                }
            }
        }
    }
    if (type.isNotBlank() && type != "none") {
        builder.addQueryParameter("encryption", encryption)
    }
    if (plugin.isNotBlank()) {
        builder.addQueryParameter("plugin", plugin)
    }

    if (name.isNotBlank()) {
        builder.setRawFragment(name)
    }

    return builder.string
}

fun TrojanGoBean.buildTrojanGoConfig(port: Int): String {
    return JSONObject().apply {
        put("run_type", "client")
        put("local_addr", LOCALHOST4)
        put("local_port", port)
        put("remote_addr", finalAddress)
        put("remote_port", finalPort)
        put("password", JSONArray().apply {
            put(password)
        })
        put("log_level", if (DataStore.logLevel > 0) 0 else 2)
        if (Protocols.shouldEnableMux("trojan-go")) put("mux", JSONObject().apply {
            put("enabled", true)
            put("concurrency", DataStore.muxConcurrency)
        })
        put("tcp", JSONObject().apply {
            put("prefer_ipv4", DataStore.ipv6Mode <= IPv6Mode.ENABLE)
        })

        when (type) {
            "original" -> {
            }

            "ws" -> put("websocket", JSONObject().apply {
                put("enabled", true)
                put("host", host)
                put("path", path)
            })
        }

        if (sni.isBlank() && finalAddress == LOCALHOST4 && !serverAddress.isIpAddress()) {
            sni = serverAddress
        }

        put("ssl", JSONObject().apply {
            if (sni.isNotBlank()) put("sni", sni)
            if (allowInsecure) put("verify", !(allowInsecure || DataStore.globalAllowInsecure))
        })

        when {
            encryption == "none" -> {
            }

            encryption.startsWith("ss;") -> put("shadowsocks", JSONObject().apply {
                put("enabled", true)
                put("method", encryption.substringAfter(";").substringBefore(":"))
                put("password", encryption.substringAfter(":"))
            })
        }
    }.toStringPretty()
}

fun JSONObject.parseTrojanGo(): TrojanGoBean {
    return TrojanGoBean().applyDefaultValues().apply {
        serverAddress = optString("remote_addr", serverAddress)
        serverPort = optInt("remote_port", serverPort)
        when (val pass = get("password")) {
            is String -> {
                password = pass
            }

            is List<*> -> {
                password = pass[0] as String
            }
        }
        optJSONArray("ssl")?.apply {
            sni = optString("sni", sni)
        }
        optJSONArray("websocket")?.apply {
            if (optBoolean("enabled", false)) {
                type = "ws"
                host = optString("host", host)
                path = optString("path", path)
            }
        }
        optJSONArray("shadowsocks")?.apply {
            if (optBoolean("enabled", false)) {
                encryption = "ss;${optString("method", "")}:${optString("password", "")}"
            }
        }
    }
}