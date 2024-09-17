package io.nekohasekai.sagernet.fmt.ssh

import moe.matsuri.nb4a.Listable
import moe.matsuri.nb4a.SingBoxOptions
import moe.matsuri.nb4a.utils.listByLineOrComma
import moe.matsuri.nb4a.utils.toListable

fun buildSingBoxOutboundSSHBean(bean: SSHBean): SingBoxOptions.Outbound_SSHOptions {
    return SingBoxOptions.Outbound_SSHOptions().apply {
        type = "ssh"
        server = bean.serverAddress
        server_port = bean.serverPort
        user = bean.username
        if (bean.publicKey.isNotBlank()) {
            host_key = bean.publicKey.listByLineOrComma().toListable()
        }
        when (bean.authType) {
            SSHBean.AUTH_TYPE_PRIVATE_KEY -> {
                private_key = Listable.fromArgs(bean.privateKey)
                private_key_passphrase = bean.privateKeyPassphrase
            }

            else -> {
                password = bean.password
            }
        }
    }
}
