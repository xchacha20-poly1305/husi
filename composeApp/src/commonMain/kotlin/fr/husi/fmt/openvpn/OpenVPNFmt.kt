package fr.husi.fmt.openvpn

import fr.husi.fmt.SingBoxOptions
import fr.husi.fmt.listable
import fr.husi.ktx.JSONMap
import fr.husi.ktx.blankAsNull
import fr.husi.ktx.getObject
import fr.husi.ktx.listByLineOrComma

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
