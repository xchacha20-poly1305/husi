@file:OptIn(ExperimentalWasmJsInterop::class)

package fr.husi.database

import kotlin.js.JsAny
import kotlin.js.Promise
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.await
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest

class IndexedDbRemoteServerStoreTest {
    @Test
    fun `records survive reopening with stable IDs order and credentials`() = runTest {
        val name = uniqueDatabaseName()
        val first = IndexedDbRemoteServerStore(name)
        val second = IndexedDbRemoteServerStore(name)
        try {
            val original = first.save(RemoteServer(name = "Office", url = "http://192.0.2.1", secret = "密钥 🔑", backend = RemoteBackend.CLASH))
            val other = first.save(RemoteServer(name = "Other"))
            assertTrue(original.id > 0)
            assertEquals(1L, original.userOrder)
            assertEquals(2L, other.userOrder)
            first.close()

            assertEquals(listOf(original, other), second.observe().first())
            val updated = second.save(original.copy(name = "Updated", secret = "new-secret", backend = RemoteBackend.AUTO))
            assertEquals(original.id, updated.id)
            assertEquals(original.userOrder, updated.userOrder)
            second.delete(other.id)
            assertEquals(listOf(updated), second.observe().first())
        } finally {
            first.close()
            second.close()
            deleteDatabase(name).await()
        }
    }

    @Test
    fun `concurrent connections retain every insert and allocate distinct IDs and orders`() = runTest {
        val name = uniqueDatabaseName()
        val first = IndexedDbRemoteServerStore(name)
        val second = IndexedDbRemoteServerStore(name)
        try {
            val saved = (1..20).map { index ->
                async {
                    val store = if (index % 2 == 0) first else second
                    store.save(RemoteServer(name = "Server $index"))
                }
            }.awaitAll()
            assertEquals(20, saved.map { it.id }.toSet().size)
            assertEquals((1L..20L).toSet(), saved.map { it.userOrder }.toSet())
            val expected = saved.sortedWith(compareBy({ it.userOrder }, { it.id }))
            assertEquals(expected, first.observe().first())
            assertEquals(expected, second.observe().first())

            val updated = listOf(
                async { first.save(expected[0].copy(name = "Edited A")) },
                async { second.save(expected[1].copy(name = "Edited B")) },
            ).awaitAll()
            assertEquals(updated + expected.drop(2), first.observe().first())
        } finally {
            first.close()
            second.close()
            deleteDatabase(name).await()
        }
    }

    @Test
    fun `aborted inserts edits and deletes leave persisted records unchanged`() = runTest {
        val name = uniqueDatabaseName()
        val store = IndexedDbRemoteServerStore(name)
        try {
            val original = store.save(RemoteServer(name = "Original", secret = "saved"))
            for (write in listOf<suspend () -> Unit>(
                { store.save(RemoteServer(name = "Failed insert")) },
                { store.save(original.copy(secret = "failed edit")) },
                { store.delete(original.id) },
            )) {
                val failure = abortNextWrite(name)
                try {
                    assertFails { write() }
                } finally {
                    failure.close()
                }
                assertEquals(listOf(original), store.observe().first())
            }
            val next = store.save(RemoteServer(name = "Successful retry"))
            assertEquals(listOf(original, next), store.observe().first())
            store.close()
            val reopened = IndexedDbRemoteServerStore(name)
            try {
                assertEquals(listOf(original, next), reopened.observe().first())
            } finally {
                reopened.close()
            }
        } finally {
            store.close()
            deleteDatabase(name).await()
        }
    }

    @Test
    fun `another connection receives committed changes through BroadcastChannel`() = runTest {
        val name = uniqueDatabaseName()
        val writer = IndexedDbRemoteServerStore(name)
        val reader = IndexedDbRemoteServerStore(name)
        val updates = Channel<List<RemoteServer>>(Channel.UNLIMITED)
        val collector = backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) {
            reader.observe().collect { updates.send(it) }
        }
        try {
            assertEquals(emptyList(), updates.receive())
            val saved = writer.save(RemoteServer(name = "From another connection"))
            assertEquals(listOf(saved), updates.receive())
            writer.delete(saved.id)
            assertEquals(emptyList(), updates.receive())
        } finally {
            collector.cancel()
            writer.close()
            reader.close()
            deleteDatabase(name).await()
        }
    }

    @Test
    fun `focus refreshes records even when a change notification was missed`() = runTest {
        val name = uniqueDatabaseName()
        val store = IndexedDbRemoteServerStore(name)
        val updates = Channel<List<RemoteServer>>(Channel.UNLIMITED)
        try {
            val saved = store.save(RemoteServer(name = "Original"))
            val collector = backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) {
                store.observe().collect { updates.send(it) }
            }
            assertEquals(listOf(saved), updates.receive())
            renameWithoutNotification(name, saved.id.toDouble(), "Changed elsewhere").await()
            dispatchFocus()
            assertEquals(listOf(saved.copy(name = "Changed elsewhere")), updates.receive())
            collector.cancel()
        } finally {
            store.close()
            deleteDatabase(name).await()
        }
    }
}

@JsFun("() => 'husi-test-servers-' + Date.now() + '-' + Math.random()")
private external fun uniqueDatabaseName(): String

@JsFun("""(name) => new Promise((resolve, reject) => {
    const request = indexedDB.deleteDatabase(name);
    request.onsuccess = () => resolve(null);
    request.onerror = () => reject(request.error);
})""")
private external fun deleteDatabase(name: String): Promise<JsAny?>

private external interface WriteFailure : JsAny {
    fun close()
}

@JsFun("""(name) => {
    const prototype = IDBObjectStore.prototype;
    const methods = ['add', 'put', 'delete'];
    const originals = methods.map(method => prototype[method]);
    const restore = () => {
        methods.forEach((method, index) => { prototype[method] = originals[index]; });
    };
    methods.forEach((method, index) => {
        prototype[method] = function(...args) {
            const request = originals[index].apply(this, args);
            if (this.transaction.db.name === name) {
                restore();
                request.addEventListener('success', () => this.transaction.abort(), {once: true});
            }
            return request;
        };
    });
    return {close: restore};
}""")
private external fun abortNextWrite(name: String): WriteFailure

@JsFun("""(name, id, value) => new Promise((resolve, reject) => {
    const request = indexedDB.open(name);
    request.onerror = () => reject(request.error);
    request.onsuccess = () => {
        const database = request.result;
        const transaction = database.transaction('servers', 'readwrite');
        const store = transaction.objectStore('servers');
        const record = store.get(id);
        record.onsuccess = () => store.put({...record.result, name: value});
        transaction.oncomplete = () => { database.close(); resolve(null); };
        transaction.onabort = () => { database.close(); reject(transaction.error); };
    };
})""")
private external fun renameWithoutNotification(name: String, id: Double, value: String): Promise<JsAny?>

@JsFun("() => window.dispatchEvent(new Event('focus'))")
private external fun dispatchFocus()
