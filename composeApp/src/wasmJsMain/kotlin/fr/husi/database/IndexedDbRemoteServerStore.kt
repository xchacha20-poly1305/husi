@file:OptIn(ExperimentalWasmJsInterop::class)

package fr.husi.database

import kotlin.js.JsAny
import kotlin.js.JsString
import kotlin.js.Promise
import kotlinx.coroutines.await
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

internal class IndexedDbRemoteServerStore(databaseName: String = "husi-remote") : RemoteServerStore {
    private val storage = createStorage(databaseName)

    override fun observe(): Flow<List<RemoteServer>> = callbackFlow {
        val subscription = storage.subscribe { trySend(Unit) }
        trySend(Unit)
        awaitClose { subscription.close() }
    }.buffer(Channel.CONFLATED).map {
        Json.decodeFromString<List<RemoteServer>>(stringValue(storage.list().await()))
    }

    override suspend fun save(server: RemoteServer): RemoteServer =
        Json.decodeFromString(stringValue(storage.save(Json.encodeToString(server)).await()))

    override suspend fun delete(id: Long) {
        storage.delete(id.toString()).await()
    }

    override fun close() = storage.close()
}

private external interface ServerStorage : JsAny {
    fun list(): Promise<JsString>
    fun save(server: String): Promise<JsString>
    fun delete(id: String): Promise<JsAny?>
    fun subscribe(onChange: () -> Unit): StorageSubscription
    fun close()
}

private external interface StorageSubscription : JsAny {
    fun close()
}

@JsFun("(name) => husiBrowser.createRemoteServerStorage(name)")
private external fun createStorage(name: String): ServerStorage

@JsFun("(value) => value")
private external fun stringValue(value: JsString): String
