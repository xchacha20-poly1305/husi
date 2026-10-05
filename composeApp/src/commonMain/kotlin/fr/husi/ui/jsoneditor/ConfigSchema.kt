package fr.husi.ui.jsoneditor

import fr.husi.core.CoreClient
import fr.husi.proto.v1.SchemaKind
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

@Serializable
enum class ConfigSchema(val kind: SchemaKind) {
    CONFIG(SchemaKind.SCHEMA_KIND_CONFIG),
    OUTBOUND(SchemaKind.SCHEMA_KIND_OUTBOUND),
    DNS_RULE(SchemaKind.SCHEMA_KIND_DNS_RULE),
}

private val completerMutex = Mutex()
private val completers = mutableMapOf<ConfigSchema, ConfigSchemaCompleter>()

/**
 * Builds the completer from the schema the core host generates, since the host's
 * protocol registry decides which options exist. Schemas do not change while the
 * app runs, so each one is fetched once.
 */
suspend fun ConfigSchema.loadCompleter(coreClient: CoreClient): ConfigSchemaCompleter {
    return completerMutex.withLock {
        completers.getOrPut(this) {
            val schema = coreClient.generateSchema(kind)
            ConfigSchemaCompleter(Json.parseToJsonElement(schema).jsonObject)
        }
    }
}
