package fr.husi.fmt.tailscale

import fr.husi.fmt.SingBoxOptions
import fr.husi.ktx.JSONMap
import fr.husi.ktx.blankAsNull
import fr.husi.ktx.listByLineOrComma

fun buildSingBoxOutboundTailcatBean(bean: TailcatBean): SingBoxOptions.Outbound_TailcatOptions {
    return SingBoxOptions.Outbound_TailcatOptions().apply {
        type = SingBoxOptions.TYPE_TAILCAT
        private_key = bean.privateKey.blankAsNull()
        server_public_key = bean.serverPublicKey
        server_disco_key = bean.serverDiscoKey
        pre_shared_key = bean.preSharedKey.blankAsNull()
        val servers = bean.derpServers.blankAsNull()?.listByLineOrComma()
        if (servers.isNullOrEmpty()) {
            derp_map_url = bean.derpMapURL.blankAsNull()
            derp_region = bean.derpRegion.takeIf { it > 0 }
        } else {
            derp_servers = servers.mapTo(mutableListOf()) { server ->
                SingBoxOptions.TailcatDERPServer().apply {
                    host = server
                }
            }
        }
    }
}

fun parseTailcatOutbound(json: JSONMap): TailcatBean = TailcatBean().apply {
    for ((key, value) in json) {
        if (value == null) continue
        when (key) {
            "tag" -> name = value.toString()
            "private_key" -> privateKey = value.toString()
            "server_public_key" -> serverPublicKey = value.toString()
            "server_disco_key" -> serverDiscoKey = value.toString()
            "pre_shared_key" -> preSharedKey = value.toString()
            "derp_map_url" -> derpMapURL = value.toString()
            "derp_region" -> derpRegion = value.toString().toIntOrNull() ?: 0
            "derp_servers" -> derpServers = (value as? List<*>).orEmpty().mapNotNull { server ->
                when (server) {
                    is String -> server
                    is Map<*, *> -> server["host"]?.toString()
                    else -> null
                }
            }.joinToString("\n")
        }
    }
}
