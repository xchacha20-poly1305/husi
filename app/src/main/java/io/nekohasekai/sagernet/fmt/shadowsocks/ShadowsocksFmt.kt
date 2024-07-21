package io.nekohasekai.sagernet.fmt.shadowsocks

import io.nekohasekai.sagernet.ktx.decodeBase64UrlSafe
import io.nekohasekai.sagernet.ktx.getIntOrNull
import io.nekohasekai.sagernet.ktx.getStr
import io.nekohasekai.sagernet.ktx.unUrlSafe
import libcore.Libcore
import libcore.URL
import moe.matsuri.nb4a.SingBoxOptions
import moe.matsuri.nb4a.utils.Util
import org.json.JSONObject

const val SIMPLE_OBFS = "simple-obfs"
const val OBFS_LOCAL = "obfs-local"

fun ShadowsocksBean.pluginToLocal() {
    if (plugin.startsWith(SIMPLE_OBFS)) {
        plugin = plugin.replaceFirst(SIMPLE_OBFS, OBFS_LOCAL)
    }
}

fun pluginToStandard(plugin: String): String {
    if (plugin.startsWith(OBFS_LOCAL)) return plugin.replaceFirst(OBFS_LOCAL, SIMPLE_OBFS)
    return plugin
}

fun parseShadowsocks(rawUrl: String): ShadowsocksBean {

    if (rawUrl.substringBefore("#").contains("@")) {
        // ss-android style
        var url = Libcore.parseURL(rawUrl)

        if (url.username.isBlank()) { // fix justmysocks' shit link
            url = Libcore.parseURL(rawUrl.substringBefore("#").decodeBase64UrlSafe())
            url.fragment = rawUrl.substringAfter("#")
        }

        val pass = try {
            url.password
        } catch (_: Exception) {
            null
        }
        // not base64 user info
        if (pass != null) {
            return ShadowsocksBean().apply {
                serverAddress = url.host
                serverPort = url.ports.toIntOrNull() ?: 8388
                method = url.username
                password = pass
                plugin = url.queryParameterNotBlank("plugin")
                name = url.fragment
                pluginToLocal()
            }
        }

        // base64 user info
        return ShadowsocksBean().apply {
            url.username.decodeBase64UrlSafe().let {
                method = it.substringBefore(":")
                password = it.substringAfter(":")
            }
            serverAddress = url.host
            serverPort = url.ports.toIntOrNull() ?: 8388
            plugin = url.queryParameterNotBlank("plugin")
            name = url.fragment
            pluginToLocal()
        }
    } else {
        // v2rayN style
        var v2Url = rawUrl

        if (v2Url.contains("#")) v2Url = v2Url.substringBefore("#")

        val url = Libcore.parseURL(v2Url)

        return ShadowsocksBean().apply {
            serverAddress = url.host
            serverPort = url.ports.toIntOrNull() ?: 8388
            method = url.username
            try {
                password = url.password
            } catch (_: Exception) {
            }
            plugin = ""
            val remarks = rawUrl.substringAfter("#").unUrlSafe()
            if (remarks.isNotBlank()) name = remarks
        }
    }

}

// https://shadowsocks.org/doc/sip002.html
fun ShadowsocksBean.toUri(): String {
    val builder = Libcore.newURL("ss").apply {
        encodeShadowsocksUserInfo(this@toUri.password, method)
        host = serverAddress
        ports = serverPort.toString()
    }

    if (plugin.isNotBlank()) {
        // The last `/` should be appended if plugin is present,
        // but is optional if only tag is present.
        builder.rawPath = "/"
        builder.addQueryParameter("plugin", pluginToStandard(plugin))
    }
    if (name.isNotBlank()) builder.fragment = name

    return builder.string
}

fun JSONObject.parseShadowsocks(): ShadowsocksBean {
    return ShadowsocksBean().apply {
        serverAddress = getStr("server")
        serverPort = getIntOrNull("server_port")
        password = getStr("password")
        method = getStr("method")
        name = optString("remarks", "")

        val pId = getStr("plugin")
        if (!pId.isNullOrBlank()) {
            plugin = pId + ";" + optString("plugin_opts", "")
            pluginToLocal()
        }
    }
}

fun buildSingBoxOutboundShadowsocksBean(bean: ShadowsocksBean): SingBoxOptions.Outbound_ShadowsocksOptions {
    return SingBoxOptions.Outbound_ShadowsocksOptions().apply {
        type = "shadowsocks"
        server = bean.serverAddress
        server_port = bean.serverPort
        password = bean.password
        method = bean.method
        if (bean.plugin.isNotBlank()) {
            plugin = bean.plugin.substringBefore(";")
            plugin_opts = bean.plugin.substringAfter(";")
        }
    }
}

/**
 * Note that encoding userinfo with Base64URL is recommended
 * but optional for Stream and AEAD (SIP004).
 * But for AEAD-2022 (SIP022), userinfo MUST NOT be encoded with Base64URL.
 * When userinfo is not encoded, method and password MUST be percent encoded.
 */
fun URL.encodeShadowsocksUserInfo(pass: String, method: String) {
    if (method.startsWith("2022-")) {
        username = method
        password = pass
        return
    }

    // use base64 to stay compatible
    username = Util.b64EncodeUrlSafe("${method}:${pass}")
}
