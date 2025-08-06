package io.nekohasekai.sagernet.fmt.juicity

import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.fmt.LOCALHOST4
import io.nekohasekai.sagernet.ktx.parseBoolean
import io.nekohasekai.sagernet.ktx.queryParameterNotBlank
import io.nekohasekai.sagernet.ktx.toStringPretty
import libcore.Libcore
import org.json.JSONObject

// https://github.com/juicity/juicity/blob/4af4f68b405a6b86560ebb16963d133a7196af5c/README.md
fun parseJuicity(link: String): JuicityBean {
    val url = Libcore.parseURL(link)
    return JuicityBean().apply {
        name = url.fragment
        uuid = url.username
        password = url.password
        serverAddress = url.host
        serverPort = url.ports.toIntOrNull() ?: 443

        // url.queryParameterNotBlank("congestion_control")
        sni = url.queryParameterNotBlank("sni")
        url.parseBoolean("allow_insecure")
        pinSHA256 = url.queryParameterNotBlank("pinned_certchain_sha256")
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
        if (password.isNotBlank()) addQueryParameter("pinned_certchain_sha256", pinSHA256)
    }.string
}

fun JuicityBean.buildJuicityConfig(port: Int, shouldProtect: Boolean): String {
    return JSONObject().apply {
        put("listen", "$LOCALHOST4:$port")
        put("server", displayAddress())
        put("uuid", uuid)
        put("password", password)
        if (sni.isNotBlank()) put("sni", sni)
        if (allowInsecure) put("allow_insecure", allowInsecure)
        put("congestion_control", "bbr")
        if (pinSHA256.isNotBlank()) put("pinned_certchain_sha256", pinSHA256)
        if (shouldProtect) put("protect_path", Libcore.ProtectPath)
        put(
            "log_level",
            if (DataStore.logLevel > 0) "debug" else "error",
        )
    }.toStringPretty()
}