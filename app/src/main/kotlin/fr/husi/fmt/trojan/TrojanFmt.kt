package fr.husi.fmt.trojan

import fr.husi.fmt.v2ray.parseDuckSoft
import fr.husi.ktx.parseBoolean
import fr.husi.ktx.queryParameterNotBlank
import fr.husi.libcore.Libcore

fun parseTrojan(link: String): TrojanBean {
    val url = Libcore.parseURL(link)
    return TrojanBean().apply {
        parseDuckSoft(url)
        url.parseBoolean("allowInsecure")
        url.queryParameterNotBlank("peer")?.let {
            sni = it
        }
    }

}
