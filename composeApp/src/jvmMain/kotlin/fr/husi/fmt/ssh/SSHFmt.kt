package fr.husi.fmt.ssh

import fr.husi.fmt.SingBoxOptions
import fr.husi.fmt.listable
import fr.husi.fmt.parseBoxOutbound
import fr.husi.ktx.JSONMap
import fr.husi.ktx.listByLineOrComma

fun buildSingBoxOutboundSSHBean(bean: SSHBean): SingBoxOptions.Outbound_SSHOptions {
    return SingBoxOptions.Outbound_SSHOptions().apply {
        type = SingBoxOptions.TYPE_SSH
        server = bean.serverAddress
        server_port = bean.serverPort
        user = bean.username
        if (bean.publicKey.isNotBlank()) {
            host_key = bean.publicKey.listByLineOrComma().toMutableList()
        }
        when (bean.authType) {
            SSHBean.AUTH_TYPE_PRIVATE_KEY -> {
                private_key = mutableListOf(bean.privateKey)
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
            "host_key" -> publicKey = listable<String>(value)?.joinToString("\n").orEmpty()
            "password" -> {
                password = value.toString()
                authType = SSHBean.AUTH_TYPE_PASSWORD
            }

            "private_key_passphrase" -> privateKeyPassphrase = value.toString()
            "private_key" -> {
                privateKey = listable<String>(value)?.joinToString("\n").orEmpty()
                authType = SSHBean.AUTH_TYPE_PRIVATE_KEY
            }
        }
    }
}