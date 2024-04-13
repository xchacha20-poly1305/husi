package io.nekohasekai.sagernet.fmt.trojan

import io.nekohasekai.sagernet.fmt.v2ray.parseDuckSoft
import libcore.URL

fun parseTrojan(url: URL): TrojanBean {
    return TrojanBean().apply {
        parseDuckSoft(url)
        url.queryParameterNotBlank("allowInsecure")
            .apply { if (this == "1" || this == "true") allowInsecure = true }
        url.queryParameterNotBlank("peer").apply { if (this.isNotBlank()) sni = this }
    }

}
