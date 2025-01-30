package io.nekohasekai.sagernet.fmt.ssh

import io.nekohasekai.sagernet.fmt.SingBoxOptions
import io.nekohasekai.sagernet.fmt.listable
import io.nekohasekai.sagernet.fmt.parseBoxOutbound
import io.nekohasekai.sagernet.ktx.JSONMap
import moe.matsuri.nb4a.utils.listByLineOrComma

fun buildSingBoxOutboundSSHBean(bean: SSHBean): SingBoxOptions.Outbound_SSHOptions {
    return SingBoxOptions.Outbound_SSHOptions().apply {
        type = bean.outboundType()
        server = bean.serverAddress
        server_port = bean.serverPort
        user = bean.username
        if (bean.publicKey.isNotBlank()) {
            host_key = bean.publicKey.listByLineOrComma()
        }
        when (bean.authType) {
            SSHBean.AUTH_TYPE_PRIVATE_KEY -> {
                private_key = listOf(bean.privateKey)
                private_key_passphrase = bean.privateKeyPassphrase
            }

            else -> {
                password = bean.password
            }
        }
    }
}

fun parseSSHOutbound(json: JSONMap): SSHBean = SSHBean().apply {
    parseBoxOutbound(json) { key, value ->
        when (key) {
            "user" -> username = value.toString()
            "host_key" -> publicKey = listable<String>(value)?.joinToString("\n")
            "password" -> {
                password = value.toString()
                authType = SSHBean.AUTH_TYPE_PASSWORD
            }

            "private_key_passphrase" -> privateKeyPassphrase = value.toString()
            "private_key" -> {
                privateKey = listable<String>(value)?.joinToString("\n")
                authType = SSHBean.AUTH_TYPE_PRIVATE_KEY
            }
        }
    }
}