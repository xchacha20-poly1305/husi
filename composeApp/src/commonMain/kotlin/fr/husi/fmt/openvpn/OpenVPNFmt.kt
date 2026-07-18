package fr.husi.fmt.openvpn

import fr.husi.fmt.SingBoxOptions
import fr.husi.fmt.listable
import fr.husi.ktx.JSONMap
import fr.husi.ktx.applyDefaultValues
import fr.husi.ktx.blankAsNull
import fr.husi.ktx.getObject
import fr.husi.ktx.listByLineOrComma

fun looksLikeOpenVPNConfig(conf: String): Boolean {
    return conf.lineSequence().any { rawLine ->
        val line = rawLine.trim()
        line == "client" || line.startsWith("remote ")
    }
}

/**
# https://openvpn.net/community-docs/community-articles/openvpn-2-7-manual.html
remote 127.0.0.1 443
proto tcp
cipher AES-128-CBC
data-ciphers AES-128-CBC
auth SHA1
resolv-retry infinite
nobind
persist-key
persist-tun
client
verb 3

<ca>
xxx
</ca>

<cert>
xxx
</cert>

<key>
xxx
</key>
 */
fun parseOpenVPNConfig(conf: String): OpenVPNBean {
    val inlineBlocks = parseOpenVPNInlineBlocks(conf)
    val bean = OpenVPNBean().applyDefaultValues().also {
        it.certificate = inlineBlocks["ca"].orEmpty()
        it.clientCertificate = inlineBlocks["cert"].orEmpty()
        it.clientKey = inlineBlocks["key"].orEmpty()
        listOf("tls-auth", "tls-crypt", "tls-crypt-v2").firstOrNull { directive ->
            !inlineBlocks[directive].isNullOrBlank()
        }?.let { directive ->
            it.controlWrapType = directive.replace('-', '_')
            it.controlWrapKey = inlineBlocks[directive].orEmpty()
        }
    }
    var foundRemote = false

    for (rawLine in conf.lineSequence()) {
        val line = rawLine.trim()
        if (line.isEmpty() || line.startsWith("#") || line.startsWith(";") || line.startsWith("<")) {
            continue
        }

        val arguments = line.split(' ', '\t').filter { it.isNotEmpty() }
        val directive = arguments.first().lowercase()
        val values = arguments.drop(1)
        when (directive) {
            "remote" -> {
                val server = values.getOrNull(0).orEmpty()
                if (server.isBlank() || foundRemote) continue
                bean.serverAddress = server
                bean.serverPort = values.getOrNull(1)?.toIntOrNull() ?: bean.defaultPort
                values.getOrNull(2)?.let { bean.network = parseOpenVPNNetwork(it) }
                foundRemote = true
            }

            "proto" -> values.firstOrNull()?.let { bean.network = parseOpenVPNNetwork(it) }

            "ca" -> inlineBlocks["ca"]?.let { certificate -> bean.certificate = certificate }

            "cert" -> inlineBlocks["cert"]?.let { certificate ->
                bean.clientCertificate = certificate
            }

            "key" -> inlineBlocks["key"]?.let { key -> bean.clientKey = key }

            "tls-auth", "tls-crypt", "tls-crypt-v2" -> {
                bean.controlWrapType = directive.replace('-', '_')
                inlineBlocks[directive]?.let { key -> bean.controlWrapKey = key }
                if (directive == "tls-auth") {
                    bean.controlWrapDirection = when (values.getOrNull(1)) {
                        "0" -> "server"
                        "1" -> "client"
                        else -> ""
                    }
                }
            }

            "data-ciphers" -> bean.dataCiphers = values.joinToString("\n") { it.replace(':', '\n') }

            "cipher" -> if (bean.dataCiphers.isBlank()) bean.dataCiphers =
                values.firstOrNull().orEmpty()

            "auth" -> bean.auth = values.firstOrNull().orEmpty()

            "compress" -> bean.compression = values.firstOrNull().orEmpty()

            "redirect-gateway" -> bean.redirectGateway = true

            "tun-mtu" -> values.firstOrNull()?.toIntOrNull()?.let { bean.mtu = it }

            "verify-x509-name" -> {
                bean.serverName = values.firstOrNull().orEmpty()
                bean.serverNameType = values.getOrNull(1).orEmpty()
            }

            "remote-cert-ku" -> bean.remoteCertificateKU = values.joinToString("\n")

            "remote-cert-tls" -> bean.remoteCertificateEKU = when (values.firstOrNull()) {
                "server" -> "server"
                "client" -> "client"
                else -> ""
            }

            "peer-fingerprint" -> bean.peerFingerprint = values.joinToString("\n")
        }
    }

    check(foundRemote) { "OpenVPN configuration is missing a remote server." }
    check(bean.certificate.isNotBlank() || bean.peerFingerprint.isNotBlank()) {
        "OpenVPN configuration requires an inline <ca> block or peer-fingerprint."
    }
    check(bean.clientCertificate.isBlank() == bean.clientKey.isBlank()) {
        "OpenVPN client certificate and private key must be provided together."
    }
    check(bean.controlWrapType.isBlank() || bean.controlWrapKey.isNotBlank()) {
        "OpenVPN control channel protection requires an inline key block."
    }
    return bean.applyDefaultValues()
}

/**
<ca>
xxxxxx
</ca>
 */
private fun parseOpenVPNInlineBlocks(conf: String): Map<String, String> {
    val supportedBlockNames = setOf("ca", "cert", "key", "tls-auth", "tls-crypt", "tls-crypt-v2")
    val blocks = mutableMapOf<String, String>()
    var activeBlockName: String? = null
    val content = StringBuilder()

    for (rawLine in conf.lineSequence()) {
        val line = rawLine.trim()
        val currentBlockName = activeBlockName
        if (currentBlockName == null) {
            if (line.startsWith('<') && line.endsWith('>') && !line.startsWith("</")) {
                val blockName = line.substring(1, line.length - 1).lowercase()
                if (blockName in supportedBlockNames) {
                    activeBlockName = blockName
                    content.clear()
                }
            }
        } else if (line.equals("</$currentBlockName>", ignoreCase = true)) {
            blocks[currentBlockName] = content.toString().trim()
            activeBlockName = null
        } else {
            if (content.isNotEmpty()) content.append('\n')
            content.append(rawLine)
        }
    }

    check(activeBlockName == null) { "OpenVPN inline <$activeBlockName> block is not closed." }
    return blocks
}

private fun parseOpenVPNNetwork(value: String): String {
    return if (value.lowercase().startsWith("tcp")) "tcp" else "udp"
}

fun buildSingBoxEndpointOpenVPNBean(bean: OpenVPNBean): SingBoxOptions.Endpoint_OpenVPNClientOptions {
    return SingBoxOptions.Endpoint_OpenVPNClientOptions().apply {
        type = SingBoxOptions.TYPE_OPENVPN_CLIENT
        server = bean.serverAddress
        server_port = bean.serverPort
        network = bean.network
        username = bean.username.blankAsNull()
        password = bean.password.blankAsNull()
        data_ciphers = bean.dataCiphers.blankAsNull()?.listByLineOrComma()?.toMutableList()
        auth = bean.auth.blankAsNull()
        compression = bean.compression.blankAsNull()
        if (bean.redirectGateway) {
            redirect_gateway = true
        }
        mtu = bean.mtu
        tls = SingBoxOptions.OpenVPNOutboundTLSOptions().apply {
            server_name = bean.serverName.blankAsNull()
            server_name_type = bean.serverNameType.blankAsNull()
            certificate = bean.certificate.blankAsNull()?.let { mutableListOf(it) }
            client_certificate = bean.clientCertificate.blankAsNull()?.let { mutableListOf(it) }
            client_key = bean.clientKey.blankAsNull()?.let { mutableListOf(it) }
            peer_fingerprint = bean.peerFingerprint
                .blankAsNull()
                ?.listByLineOrComma()
                ?.toMutableList()
            remote_certificate_ku = bean.remoteCertificateKU
                .blankAsNull()
                ?.listByLineOrComma()
                ?.toMutableList()
            remote_certificate_eku = bean.remoteCertificateEKU.blankAsNull()
            if (bean.controlWrapType.isNotBlank() || bean.controlWrapKey.isNotBlank()) {
                control_wrap = SingBoxOptions.OpenVPNControlWrapOptions().apply {
                    type = bean.controlWrapType.blankAsNull()
                    key = bean.controlWrapKey.blankAsNull()?.let { mutableListOf(it) }
                    direction = bean.controlWrapDirection.blankAsNull()
                }
            }
        }
    }
}

fun parseOpenVPNEndpoint(json: JSONMap): OpenVPNBean = OpenVPNBean().apply {
    name = json["tag"]?.toString().orEmpty()
    serverAddress = json["server"]?.toString().orEmpty()
    json["server_port"]?.toString()?.toIntOrNull()?.let { serverPort = it }
    network = json["network"]?.toString().orEmpty()
    username = json["username"]?.toString().orEmpty()
    password = json["password"]?.toString().orEmpty()
    dataCiphers = listable<String>(json["data_ciphers"])?.joinToString("\n").orEmpty()
    auth = json["auth"]?.toString().orEmpty()
    compression = json["compression"]?.toString().orEmpty()
    redirectGateway = json["redirect_gateway"].toString().toBoolean()
    json["mtu"]?.toString()?.toIntOrNull()?.let { mtu = it }

    val tls = json.getObject("tls") ?: return@apply
    serverName = tls["server_name"]?.toString().orEmpty()
    serverNameType = tls["server_name_type"]?.toString().orEmpty()
    certificate = listable<String>(tls["certificate"])?.joinToString("\n").orEmpty()
    clientCertificate = listable<String>(tls["client_certificate"])?.joinToString("\n").orEmpty()
    clientKey = listable<String>(tls["client_key"])?.joinToString("\n").orEmpty()
    peerFingerprint = listable<String>(tls["peer_fingerprint"])?.joinToString("\n").orEmpty()
    remoteCertificateKU = listable<String>(tls["remote_certificate_ku"])?.joinToString("\n").orEmpty()
    remoteCertificateEKU = tls["remote_certificate_eku"]?.toString().orEmpty()

    val controlWrap = tls.getObject("control_wrap") ?: return@apply
    controlWrapType = controlWrap["type"]?.toString().orEmpty()
    controlWrapKey = listable<String>(controlWrap["key"])?.joinToString("\n").orEmpty()
    controlWrapDirection = controlWrap["direction"]?.toString().orEmpty()
}
