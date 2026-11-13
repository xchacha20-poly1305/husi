package fr.husi.test

import fr.husi.remote.ClashRequest
import fr.husi.remote.clashJson
import fr.husi.remote.ClashStream
import fr.husi.remote.ClashApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject

internal class FakeClashApi : ClashApi {
    val requests = mutableListOf<Pair<String, JsonObject>>()
    var closed = false
    var handler: suspend (String, JsonObject) -> JsonElement = { operation, _ ->
        error("Unexpected request: $operation")
    }
    var streams: (String, Int) -> Flow<JsonElement> = { _, _ ->
        flow {
            awaitCancellation()
        }
    }

    override suspend fun <T> request(request: ClashRequest<T>): T {
        val input = clashJson.encodeToJsonElement(request.input).jsonObject
        requests += request.operation to input
        return request.decode(handler(request.operation, input))
    }

    override fun <T> watch(stream: ClashStream<T>, interval: Int): Flow<T> {
        return streams(stream.kind, interval).map(stream.decode)
    }

    override fun close() {
        closed = true
    }
}
