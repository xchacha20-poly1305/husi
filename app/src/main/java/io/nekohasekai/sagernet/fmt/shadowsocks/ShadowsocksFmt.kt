package io.nekohasekai.sagernet.fmt.shadowsocks

import io.nekohasekai.sagernet.ktx.decodeBase64UrlSafe
import io.nekohasekai.sagernet.ktx.getIntNya
import io.nekohasekai.sagernet.ktx.getStr
import io.nekohasekai.sagernet.ktx.unUrlSafe
import libcore.Libcore
import moe.matsuri.nb4a.SingBoxOptions
import moe.matsuri.nb4a.utils.Util
import org.json.JSONObject

fun ShadowsocksBean.fixPluginName() {
    if (plugin.startsWith("simple-obfs")) {
        plugin = plugin.replaceFirst("simple-obfs", "obfs-local")
    }
}

fun parseShadowsocks(rawUrl: String): ShadowsocksBean {

    if (rawUrl.substringBefore("#").contains("@")) {
        // ss-android style
        var url = Libcore.parseURL(rawUrl)

        if (url.username.isBlank()) { // fix justmysocks' shit link
            url = Libcore.parseURL(rawUrl.substringBefore("#").decodeBase64UrlSafe())
            url.setRawFragment(rawUrl.substringAfter("#"))
        }

        if (url.password.isNotBlank()) {
            return ShadowsocksBean().apply {
                serverAddress = url.host
                serverPort = url.ports.toIntOrNull() ?: 8388
                method = url.username
                try {
                    password = url.password
                } catch (_: Exception) {
                }
                plugin = url.queryParameterNotBlank("plugin")
                name = url.fragment
                fixPluginName()
            }
        }

        val methodAndPswd = url.username.decodeBase64UrlSafe()

        return ShadowsocksBean().apply {
            serverAddress = url.host
            serverPort = url.ports.toIntOrNull() ?: 8388
            method = methodAndPswd.substringBefore(":")
            password = methodAndPswd.substringAfter(":")
            plugin = url.queryParameterNotBlank("plugin")
            name = url.fragment
            fixPluginName()
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

fun ShadowsocksBean.toUri(): String {

    val builder = Libcore.newURL("ss").apply {
        username = Util.b64EncodeUrlSafe("$method:$password")
        host = serverAddress
        ports = serverPort.toString()
    }

    if (plugin.isNotBlank()) {
        builder.addQueryParameter("plugin", plugin)
    }

    if (name.isNotBlank()) {
        builder.setRawFragment(name)
    }

    return builder.string.replace("$serverPort/", "$serverPort")

}

fun JSONObject.parseShadowsocks(): ShadowsocksBean {
    return ShadowsocksBean().apply {
        serverAddress = getStr("server")
        serverPort = getIntNya("server_port")
        password = getStr("password")
        method = getStr("method")
        name = optString("remarks", "")

        val pId = getStr("plugin")
        if (!pId.isNullOrBlank()) {
            plugin = pId + ";" + optString("plugin_opts", "")
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
