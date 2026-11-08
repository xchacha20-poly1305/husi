package fr.husi.fmt.snell

import fr.husi.fmt.SingBoxOptions
import fr.husi.fmt.parseBoxOutbound
import fr.husi.ktx.JSONMap
import fr.husi.ktx.blankAsNull

fun buildSingBoxOutboundSnellBean(bean: SnellBean): SingBoxOptions.Outbound_SnellOptions {
    return SingBoxOptions.Outbound_SnellOptions().apply {
        type = SingBoxOptions.TYPE_SNELL
        server = bean.serverAddress
        server_port = bean.serverPort
        version = bean.version
        psk = bean.psk
        userkey = bean.userKey.blankAsNull()
        reuse = bean.reuse.takeIf { it }
        when (bean.version) {
            SnellBean.VERSION_4 -> {
                obfs_mode = bean.obfsMode.blankAsNull()
                obfs_host = bean.obfsHost.blankAsNull()
            }

            SnellBean.VERSION_6 -> {
                mode = bean.mode.blankAsNull()
            }
        }
    }
}

fun parseSnellOutbound(json: JSONMap): SnellBean = SnellBean().apply {
    parseBoxOutbound(json) { key, value ->
        when (key) {
            "version" -> version = value.toString().toIntOrNull() ?: SnellBean.VERSION_4
            "psk" -> psk = value.toString()
            "userkey" -> userKey = value.toString()
            "reuse" -> reuse = value.toString().toBoolean()
            "obfs_mode" -> obfsMode = value.toString()
            "obfs_host" -> obfsHost = value.toString()
            "mode" -> mode = value.toString()
        }
    }
}
