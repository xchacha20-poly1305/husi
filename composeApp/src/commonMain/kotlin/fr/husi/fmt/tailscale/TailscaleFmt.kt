package fr.husi.fmt.tailscale

import fr.husi.fmt.SingBoxOptions
import fr.husi.fmt.listable
import fr.husi.ktx.JSONMap
import fr.husi.ktx.blankAsNull
import fr.husi.ktx.listByLineOrComma
import fr.husi.platform.PlatformInfo

fun buildSingBoxEndpointTailscaleBean(bean: TailscaleBean): SingBoxOptions.Endpoint_TailscaleOptions {
    return SingBoxOptions.Endpoint_TailscaleOptions().apply {
        type = SingBoxOptions.TYPE_TAILSCALE
        state_directory = bean.stateDirectory.blankAsNull()
        auth_key = bean.authKey.blankAsNull()
        control_url = bean.controlURL.blankAsNull()
        ephemeral = bean.ephemeral.takeIf { it }
        hostname = bean.hostname.blankAsNull()
        accept_routes = bean.acceptRoutes.takeIf { it }
        exit_node = bean.exitNode.blankAsNull()
        exit_node_allow_lan_access = bean.exitNodeAllowLANAccess.takeIf { it }
        advertise_routes = bean.advertiseRoutes.blankAsNull()?.listByLineOrComma()?.toMutableList()
        advertise_exit_node = bean.advertiseExitNode.takeIf { it }
        advertise_tags = bean.advertiseTags.blankAsNull()?.listByLineOrComma()?.toMutableList()
        listen_port = bean.listenPort.takeIf { it > 0 }
        if (bean.sshServer && !PlatformInfo.isAndroid) {
            ssh_server = SingBoxOptions.TailscaleSSHServerOptions().apply {
                enabled = true
            }
        }
        if (!PlatformInfo.isAndroid) {
            taildrop_directory = bean.taildropDirectory.blankAsNull()
        }
    }
}

fun parseTailscaleEndpoint(json: JSONMap): TailscaleBean = TailscaleBean().apply {
    for ((key, value) in json) {
        if (value == null) continue
        when (key) {
            "tag" -> name = value.toString()
            "state_directory" -> stateDirectory = value.toString()
            "auth_key" -> authKey = value.toString()
            "control_url" -> controlURL = value.toString()
            "ephemeral" -> ephemeral = value.toString().toBoolean()
            "hostname" -> hostname = value.toString()
            "accept_routes" -> acceptRoutes = value.toString().toBoolean()
            "exit_node" -> exitNode = value.toString()
            "exit_node_allow_lan_access" -> exitNodeAllowLANAccess = value.toString().toBoolean()
            "advertise_routes" -> advertiseRoutes =
                listable<String>(value)?.joinToString("\n").orEmpty()

            "advertise_exit_node" -> advertiseExitNode = value.toString().toBoolean()
            "advertise_tags" -> advertiseTags = listable<String>(value)?.joinToString("\n").orEmpty()
            "listen_port" -> listenPort = value.toString().toIntOrNull() ?: 0
            "ssh_server" -> sshServer = when (value) {
                is Map<*, *> -> value["enabled"]?.toString().toBoolean()
                else -> value.toString().toBoolean()
            }

            "taildrop_directory" -> taildropDirectory = value.toString()
        }
    }
}

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
                SingBoxOptions.TailcatDERPServer().apply { host = server }
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
