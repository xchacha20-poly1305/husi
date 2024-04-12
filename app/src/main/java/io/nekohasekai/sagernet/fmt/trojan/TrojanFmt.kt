package io.nekohasekai.sagernet.fmt.trojan

import io.nekohasekai.sagernet.fmt.v2ray.parseDuckSoft
import libcore.Libcore

fun parseTrojan(rawUrl: String): TrojanBean {

    val link = Libcore.parseURL(rawUrl)

    return TrojanBean().apply {
        parseDuckSoft(link)
        link.queryParameterNotBlank("allowInsecure")
            .apply { if (this == "1" || this == "true") allowInsecure = true }
        link.queryParameterNotBlank("peer").apply { if (this.isNotBlank()) sni = this }
    }

}
