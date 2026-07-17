package fr.husi.fmt.openconnect

import fr.husi.fmt.SingBoxOptions
import fr.husi.fmt.listable
import fr.husi.ktx.JSONMap
import fr.husi.ktx.blankAsNull
import fr.husi.ktx.getObject
import fr.husi.ktx.listByLineOrComma

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
    certificateAuthority = listable<String>(tls["certificate_authority"])?.joinToString("\n").orEmpty()
    clientCertificate = listable<String>(tls["client_certificate"])?.joinToString("\n").orEmpty()
    clientKey = listable<String>(tls["client_key"])?.joinToString("\n").orEmpty()
    clientKeyPassword = tls["client_key_password"]?.toString().orEmpty()
    mcaCertificate = listable<String>(tls["mca_certificate"])?.joinToString("\n").orEmpty()
    mcaKey = listable<String>(tls["mca_key"])?.joinToString("\n").orEmpty()
    mcaKeyPassword = tls["mca_key_password"]?.toString().orEmpty()
}
