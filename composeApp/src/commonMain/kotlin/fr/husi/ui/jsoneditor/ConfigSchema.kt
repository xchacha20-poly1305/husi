package fr.husi.ui.jsoneditor

import fr.husi.core.CoreClient
import fr.husi.libcore.Libcore
import fr.husi.proto.v1.SchemaKind
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.koin.core.context.GlobalContext
import kotlin.time.Duration.Companion.seconds

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
    schemaCompleter(loadSchema(ConfigSchema.CONFIG))
}

private val outboundSchemaCompleter by lazy {
    schemaCompleter(loadSchema(ConfigSchema.OUTBOUND))
}

private val dnsRuleSchemaCompleter by lazy {
    schemaCompleter(loadSchema(ConfigSchema.DNS_RULE))
}

/**
 * Prefer [CoreClient.generateSchema] (ApplicationService) when a host is
 * reachable. Fall back to bound generators for unit tests without Koin/host
 * and for Android main-process offline use (no :bg). Pure JSON Schema
 * generation is safe to keep on the bound surface.
 */
private fun loadSchema(schema: ConfigSchema): String {
    val kind = when (schema) {
        ConfigSchema.CONFIG -> SchemaKind.SCHEMA_KIND_CONFIG
        ConfigSchema.OUTBOUND -> SchemaKind.SCHEMA_KIND_OUTBOUND
        ConfigSchema.DNS_RULE -> SchemaKind.SCHEMA_KIND_DNS_RULE
    }
    val client = GlobalContext.getOrNull()?.get<CoreClient>()
    if (client != null) {
        try {
            return runBlocking {
                withTimeout(10.seconds) { client.generateSchema(kind) }
            }
        } catch (_: Exception) {
            // Host down / timeout — use bound pure generators.
        }
    }
    return when (schema) {
        ConfigSchema.CONFIG -> Libcore.generateConfigSchema()
        ConfigSchema.OUTBOUND -> Libcore.generateOutboundSchema()
        ConfigSchema.DNS_RULE -> Libcore.generateDNSRuleSchema()
    }
}

private fun schemaCompleter(content: String) =
    ConfigSchemaCompleter(Json.parseToJsonElement(content).jsonObject, configJsonEngine)
