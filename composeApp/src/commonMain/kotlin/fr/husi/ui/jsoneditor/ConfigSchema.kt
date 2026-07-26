package fr.husi.ui.jsoneditor

import fr.husi.libcore.Libcore
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

@Serializable
enum class ConfigSchema {
    CONFIG,
    OUTBOUND,
    DNS_RULE;

    val completer: ConfigSchemaCompleter
        get() = when (this) {
            CONFIG -> configSchemaCompleter
            OUTBOUND -> outboundSchemaCompleter
            DNS_RULE -> dnsRuleSchemaCompleter
        }
}

private val configSchemaCompleter by lazy {
    schemaCompleter(Libcore.generateConfigSchema())
}

private val outboundSchemaCompleter by lazy {
    schemaCompleter(Libcore.generateOutboundSchema())
}

private val dnsRuleSchemaCompleter by lazy {
    schemaCompleter(Libcore.generateDNSRuleSchema())
}

private fun schemaCompleter(content: String) =
    ConfigSchemaCompleter(Json.parseToJsonElement(content).jsonObject, configJsonEngine)
