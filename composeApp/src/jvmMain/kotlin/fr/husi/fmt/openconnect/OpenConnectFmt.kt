package fr.husi.fmt.openconnect

import fr.husi.fmt.SingBoxOptions
import fr.husi.fmt.listable
import fr.husi.ktx.JSONMap
import fr.husi.ktx.applyDefaultValues
import fr.husi.ktx.blankAsNull
import fr.husi.ktx.getObject
import fr.husi.ktx.listByLineOrComma

/**
 * https://www.infradead.org/openconnect/manual.html
 */
fun parseOpenConnectConfig(conf: String): OpenConnectBean {
    val bean = OpenConnectBean().applyDefaultValues()
    var hasServer = false

    for (line in conf.lineSequence()) {
        val (name, value) = parseOpenConnectConfigOption(line) ?: continue
        when (name) {
            "server" -> {
                bean.server = value.orEmpty()
                hasServer = bean.server.isNotBlank()
            }

            "protocol" -> bean.flavor = value.orEmpty()
            "user" -> bean.username = value.orEmpty()
            "usergroup", "authgroup" -> bean.authGroup = value.orEmpty()
            "os" -> bean.reportedOS = value.orEmpty()
            "useragent" -> bean.userAgent = value.orEmpty()
            "allow-insecure-crypto" -> bean.allowInsecureCrypto = true
            "cafile" -> bean.certificateAuthority = value.orEmpty()
            "certificate" -> bean.clientCertificate = value.orEmpty()
            "sslkey" -> bean.clientKey = value.orEmpty()
            "key-password" -> bean.clientKeyPassword = value.orEmpty()
            "mca-certificate" -> bean.mcaCertificate = value.orEmpty()
            "mca-key" -> bean.mcaKey = value.orEmpty()
            "mca-key-password" -> bean.mcaKeyPassword = value.orEmpty()
            "form-entry" -> value?.toOpenConnectFormEntry()?.let { entry ->
                bean.formEntries += entry
            }
        }
    }

    require(hasServer) { "missing server" }
    return bean
}

private fun parseOpenConnectConfigOption(rawLine: String): Pair<String, String?>? {
    val line = rawLine.trim()
    if (line.isEmpty() || line.startsWith('#')) return null

    val separator = line.indexOfFirst { it == '=' || it.isWhitespace() }
    if (separator == -1) return line.lowercase() to null

    val name = line.substring(0, separator).lowercase()
    val value = line.substring(separator)
        .trimStart()
        .removePrefix("=")
        .trimStart()
    return name to value
}

private fun String.toOpenConnectFormEntry(): OpenConnectFormEntry? {
    val key = substringBefore('=')
    val formId = key.substringBefore(':')
    val name = key.substringAfter(':', missingDelimiterValue = "")
    if (formId.isBlank() || name.isBlank() || '=' !in this) return null

    return OpenConnectFormEntry(
        formId = formId,
        name = name,
        value = substringAfter('='),
    )
}

fun buildSingBoxEndpointOpenConnectBean(bean: OpenConnectBean): SingBoxOptions.Endpoint_OpenConnectOptions {
    return SingBoxOptions.Endpoint_OpenConnectOptions().apply {
        type = SingBoxOptions.TYPE_OPENCONNECT
        server = bean.server
        flavor = bean.flavor.blankAsNull()
        username = bean.username.blankAsNull()
        password = bean.password.blankAsNull()
        auth_group = bean.authGroup.blankAsNull()
        reported_os = bean.reportedOS.blankAsNull()
        user_agent = bean.userAgent.blankAsNull()
        local_hostname = bean.localHostname.blankAsNull()
        allow_insecure_crypto = bean.allowInsecureCrypto.takeIf { it }
        form_entries = bean.formEntries.takeIf { it.isNotEmpty() }?.map { entry ->
            SingBoxOptions.OpenConnectFormEntryOptions().apply {
                form_id = entry.formId.blankAsNull()
                submission_key = entry.submissionKey.blankAsNull()
                name = entry.name.blankAsNull()
                value = entry.value
            }
        }?.toMutableList()
        tls = SingBoxOptions.OpenConnectTLSOptions().apply {
            if (bean.tlsInsecure) {
                insecure = true
            }
            server_name = bean.tlsServerName.blankAsNull()
            peer_fingerprint = bean.tlsPeerFingerprint
                .blankAsNull()
                ?.listByLineOrComma()
                ?.toMutableList()
            certificate_authority = bean.certificateAuthority
                .blankAsNull()
                ?.listByLineOrComma()
                ?.toMutableList()
            client_certificate = bean.clientCertificate.blankAsNull()?.let { mutableListOf(it) }
            client_key = bean.clientKey.blankAsNull()?.let { mutableListOf(it) }
            client_key_password = bean.clientKeyPassword.blankAsNull()
            mca_certificate = bean.mcaCertificate.blankAsNull()?.let { mutableListOf(it) }
            mca_key = bean.mcaKey.blankAsNull()?.let { mutableListOf(it) }
            mca_key_password = bean.mcaKeyPassword.blankAsNull()
        }
    }
}

fun parseOpenConnectEndpoint(json: JSONMap): OpenConnectBean = OpenConnectBean().apply {
    name = json["tag"]?.toString().orEmpty()
    server = json["server"]?.toString().orEmpty()
    flavor = json["flavor"]?.toString().orEmpty()
    username = json["username"]?.toString().orEmpty()
    password = json["password"]?.toString().orEmpty()
    authGroup = json["auth_group"]?.toString().orEmpty()
    reportedOS = json["reported_os"]?.toString().orEmpty()
    userAgent = json["user_agent"]?.toString().orEmpty()
    localHostname = json["local_hostname"]?.toString().orEmpty()
    allowInsecureCrypto = json["allow_insecure_crypto"].toString().toBoolean()

    (json["form_entries"] as? List<*>)?.let { entries ->
        formEntries = entries.mapNotNull { rawEntry ->
            val entry = rawEntry as? Map<*, *> ?: return@mapNotNull null
            OpenConnectFormEntry(
                formId = entry["form_id"]?.toString().orEmpty(),
                submissionKey = entry["submission_key"]?.toString().orEmpty(),
                name = entry["name"]?.toString().orEmpty(),
                value = entry["value"]?.toString().orEmpty(),
            )
        }.toList()
    }

    val tls = json.getObject("tls") ?: return@apply
    tlsInsecure = tls["insecure"].toString().toBoolean()
    tlsServerName = tls["server_name"]?.toString().orEmpty()
    tlsPeerFingerprint = listable<String>(tls["peer_fingerprint"])?.joinToString("\n").orEmpty()
    certificateAuthority = listable<String>(tls["certificate_authority"])?.joinToString("\n").orEmpty()
    clientCertificate = listable<String>(tls["client_certificate"])?.joinToString("\n").orEmpty()
    clientKey = listable<String>(tls["client_key"])?.joinToString("\n").orEmpty()
    clientKeyPassword = tls["client_key_password"]?.toString().orEmpty()
    mcaCertificate = listable<String>(tls["mca_certificate"])?.joinToString("\n").orEmpty()
    mcaKey = listable<String>(tls["mca_key"])?.joinToString("\n").orEmpty()
    mcaKeyPassword = tls["mca_key_password"]?.toString().orEmpty()
}
