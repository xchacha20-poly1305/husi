package fr.husi.fmt.juicity

import fr.husi.database.DataStore
import fr.husi.fmt.LOCALHOST4
import fr.husi.fmt.SingBoxOptions
import fr.husi.ktx.blankAsNull
import fr.husi.ktx.toJsonStringKxs
import fr.husi.ktx.parseBoolean
import fr.husi.libcore.Libcore

// https://github.com/juicity/juicity/blob/4af4f68b405a6b86560ebb16963d133a7196af5c/README.md
fun parseJuicity(link: String): JuicityBean {
    val url = Libcore.parseURL(link)
    return JuicityBean().apply {
        name = url.fragment
        uuid = url.username
        password = url.password
        serverAddress = url.host
        serverPort = url.ports.toIntOrNull() ?: 443

        // url.queryParameter("congestion_control")
        sni = url.queryParameter("sni")
        url.parseBoolean("allow_insecure")
        pinSHA256 = url.queryParameter("pinned_certchain_sha256")
    }
}

fun JuicityBean.toUri(): String {
    return Libcore.newURL("juicity").apply {
        username = uuid
        password = this@toUri.password
        host = serverAddress
        ports = serverPort.toString()

        addQueryParameter("congestion_control", "bbr")
        if (sni.isNotBlank()) addQueryParameter("sni", sni)
        if (allowInsecure) addQueryParameter("allow_insecure", "1")
        if (pinSHA256.isNotBlank()) addQueryParameter("pinned_certchain_sha256", pinSHA256)
    }.string
}

fun JuicityBean.buildJuicityConfig(port: Int, shouldProtect: Boolean): String {
    return mutableMapOf<String, Any?>(
        "listen" to "$LOCALHOST4:$port",
        "server" to displayAddress(),
        "uuid" to uuid,
        "password" to password,
        "sni" to sni.takeIf { it.isNotBlank() },
        "allow_insecure" to allowInsecure.takeIf { it },
        "congestion_control" to "bbr",
        "pinned_certchain_sha256" to pinSHA256.takeIf { it.isNotBlank() },
        "protect_path" to if (shouldProtect) Libcore.ProtectPath else null,
        "log_level" to if (DataStore.logLevel > 0) "debug" else "error",
    ).toJsonStringKxs()
}

fun buildSingBoxOutboundJuicityBean(bean: JuicityBean): SingBoxOptions.Outbound_JuicityOptions {
    return SingBoxOptions.Outbound_JuicityOptions().apply {
        type = SingBoxOptions.TYPE_JUICITY
        server = bean.serverAddress
        server_port = bean.serverPort
        uuid = bean.uuid
        password = bean.password
        pin_cert_sha256 = bean.pinSHA256.blankAsNull()
        tls = SingBoxOptions.OutboundTLSOptions().apply {
            enabled = true
            server_name = bean.sni.blankAsNull()
            insecure = bean.allowInsecure || pin_cert_sha256 != null
        }
    }
}