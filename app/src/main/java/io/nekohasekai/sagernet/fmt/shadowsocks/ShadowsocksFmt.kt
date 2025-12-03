package io.nekohasekai.sagernet.fmt.shadowsocks

import io.nekohasekai.sagernet.fmt.SingBoxOptions
import io.nekohasekai.sagernet.fmt.buildSingBoxMux
import io.nekohasekai.sagernet.fmt.parseBoxOutbound
import io.nekohasekai.sagernet.fmt.parseBoxUot
import io.nekohasekai.sagernet.ktx.JSONMap
import io.nekohasekai.sagernet.ktx.b64DecodeToString
import io.nekohasekai.sagernet.ktx.b64EncodeOneLine
import io.nekohasekai.sagernet.ktx.b64EncodeUrlSafe
import io.nekohasekai.sagernet.ktx.blankAsNull
import io.nekohasekai.sagernet.ktx.getIntOrNull
import io.nekohasekai.sagernet.ktx.getStr
import io.nekohasekai.sagernet.ktx.substringBetween
import io.nekohasekai.sagernet.ktx.urlSafe
import libcore.Libcore
import libcore.URL
import org.json.JSONObject

const val SIMPLE_OBFS = "simple-obfs"
const val OBFS_LOCAL = "obfs-local"

fun ShadowsocksBean.pluginToLocal() {
    if (plugin?.startsWith(SIMPLE_OBFS) == true) {
        plugin = plugin.replaceFirst(SIMPLE_OBFS, OBFS_LOCAL)
    }
}

fun pluginToStandard(plugin: String): String {
    if (plugin.startsWith(OBFS_LOCAL)) return plugin.replaceFirst(OBFS_LOCAL, SIMPLE_OBFS)
    return plugin
}

/**
 * https://shadowsocks.org/doc/sip002.html
 */
fun parseShadowsocks(rawUrl: String): ShadowsocksBean {
    val fixedURL = try {
        // https://shadowsocks.org/doc/configs.html#uri-and-qr-code
        // ss://BASE64-ENCODED-STRING-WITHOUT-PADDING#TAG
        // ss://YmYtY2ZiOnRlc3QvIUAjOkAxOTIuMTY4LjEwMC4xOjg4ODg#example-server
        // Legacy shadosocks-android format

        val content = rawUrl.substringBetween("ss://", "#")
        if (content.contains("@")) {
            // Break: this is not legacy format
            throw IllegalArgumentException()
        }
        val fragment = rawUrl.substringAfter("#", "").blankAsNull()
        val decoded = content.b64DecodeToString()
        // **last** @
        val lastAtIndex = decoded.lastIndexOf('@')
        if (lastAtIndex < 0) throw IllegalArgumentException()
        val userInfo = decoded.take(lastAtIndex)
        val rest = decoded.substring(lastAtIndex + 1)
        val encodedUserInfo = userInfo.b64EncodeOneLine() // Adapt strange char
        var noFragment = "ss://$encodedUserInfo@$rest"
        fragment?.let {
            noFragment += "#$it"
        }
        noFragment
    } catch (_: Exception) {
        rawUrl
    }

    var url = Libcore.parseURL(fixedURL)
    if (url.username.isBlank()) { // fix justmysocks' non-standard link
        url = Libcore.parseURL(fixedURL.substringBefore("#").b64DecodeToString())
        url.fragment = fixedURL.substringAfter("#", "")
    }

    val pass = url.password
    return ShadowsocksBean().apply {
        if (pass.isEmpty()) {
            // ss://cmM0LW1kNTpwYXNzd2Q@192.168.100.1:8888/?plugin=obfs-local%3Bobfs%3Dhttp#Example2
            url.username.b64DecodeToString().let {
                method = it.substringBefore(":")
                password = it.substringAfter(":")
            }
        } else {
            // ss://2022-blake3-aes-256-gcm:YctPZ6U7xPPcU%2Bgp3u%2B0tx%2FtRizJN9K8y%2BuKlW2qjlI%3D@192.168.100.1:8888/?plugin=v2ray-plugin%3Bserver#Example3
            method = url.username
            password = pass
        }
        serverAddress = url.host
        serverPort = url.ports.toIntOrNull() ?: 8388
        plugin = url.queryParameter("plugin")
        name = url.fragment
        pluginToLocal()
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
        type = bean.outboundType()
        server = bean.serverAddress
        server_port = bean.serverPort
        password = bean.password
        method = bean.method
        if (bean.plugin.isNotBlank()) {
            plugin = bean.plugin.substringBefore(";")
            plugin_opts = bean.plugin.substringAfter(";")
        }
        if (!bean.udpOverTcp) {
            multiplex = buildSingBoxMux(bean)
        }
    }
}

/**
 * Note that encoding userinfo with Base64URL is recommended
 * but optional for Stream and AEAD (SIP004).
 * But for AEAD-2022 (SIP022), userinfo MUST NOT be encoded with Base64URL.
 * When userinfo is not encoded, method and password MUST be percent encoded.
 */
private fun URL.encodeShadowsocksUserInfo(pass: String, method: String) {
    if (method.startsWith("2022-")) {
        username = method
        password = pass
        return
    }

    // use base64 to stay compatible
    username = "${method}:${pass}".b64EncodeUrlSafe()
}

fun parseShadowsocksOutbound(json: JSONMap): ShadowsocksBean = ShadowsocksBean().apply {
    var pluginName = ""
    var pluginOpts = ""

    parseBoxOutbound(json) { key, value ->
        when (key) {
            "password" -> password = value.toString()
            "method" -> method = value.toString()
            "plugin" -> pluginName = value.toString()
            "plugin_opts" -> pluginOpts = value.toString()
            "udp_over_tcp" -> udpOverTcp = parseBoxUot(value)
        }
    }

    if (pluginName.isNotBlank()) {
        plugin = "$pluginName;$pluginOpts"
    }
    pluginToLocal()
}