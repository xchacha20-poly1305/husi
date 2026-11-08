package fr.husi.remote

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.JsonObject

/** RPC client of a sing-box API service. Requests and responses are protobuf JSON. */
internal interface RemoteClient {
    /** Replaces the current session with one to [url] and returns its `GetVersion` response. */
    suspend fun connect(url: String, secret: String): JsonObject

    suspend fun call(method: String, request: JsonObject = JsonObject(emptyMap())): JsonObject

    fun stream(method: String, request: JsonObject = JsonObject(emptyMap())): Flow<JsonObject>

    fun bidi(method: String, requests: Flow<JsonObject>): Flow<JsonObject>

    fun close()

    /** Asks [url] for its version without touching the current session. */
    suspend fun testConnection(url: String, secret: String): String

    /** The form of [value] that [connect] dials; throws when it is not an HTTP(S) API URL. */
    fun normalizeUrl(value: String): String
}
