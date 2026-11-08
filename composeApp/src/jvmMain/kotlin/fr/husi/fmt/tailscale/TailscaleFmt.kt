package fr.husi.fmt.tailscale

import fr.husi.fmt.SingBoxOptions
import fr.husi.fmt.listable
import fr.husi.ktx.JSONMap
import fr.husi.ktx.blankAsNull
import fr.husi.ktx.listByLineOrComma
import fr.husi.platform.PlatformInfo

fun buildSingBoxEndpointTailscaleBean(
    bean: TailscaleBean,
    extraRoutes: List<String> = emptyList(),
): SingBoxOptions.Endpoint_TailscaleOptions {
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
        advertise_routes = (bean.advertiseRoutes.blankAsNull()?.listByLineOrComma().orEmpty() + extraRoutes)
            .distinct()
            .takeIf { it.isNotEmpty() }
            ?.toMutableList()
        advertise_exit_node = bean.advertiseExitNode.takeIf { it }
        advertise_tags = bean.advertiseTags.blankAsNull()?.listByLineOrComma()?.toMutableList()
        listen_port = bean.listenPort.takeIf { it > 0 }
        if (!PlatformInfo.isAndroid) {
            taildrop_directory = bean.taildropDirectory.blankAsNull()
            if (bean.sshServer) {
                ssh_server = SingBoxOptions.TailscaleSSHServerOptions().apply {
                    enabled = true
                }
            }
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
