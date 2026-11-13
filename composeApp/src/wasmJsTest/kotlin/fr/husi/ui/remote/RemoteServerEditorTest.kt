@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package fr.husi.ui.remote

import androidx.compose.runtime.snapshots.Snapshot
import androidx.lifecycle.ViewModelStore
import fr.husi.Key
import fr.husi.database.RemoteBackend
import fr.husi.database.RemoteServer
import fr.husi.database.preference.long
import fr.husi.remote.RemoteState
import fr.husi.test.FakeClashApi
import fr.husi.test.FakePreferencesDataStore
import fr.husi.test.FakeRemoteClient
import fr.husi.test.FakeRemoteServerStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.milliseconds

class RemoteServerEditorTest {
    @Test
    fun `input debounces tests and manual selection locks the protocol until automatic is restored`() =
        runTest {
            val tests = mutableListOf<Pair<String, String>>()
            val native = object : FakeRemoteClient() {
                override suspend fun testConnection(url: String, secret: String): String {
                    tests += url to secret
                    return "rpc"
                }
            }
            val store = FakeRemoteServerStore()
            val remote = RemoteState(
                backgroundScope,
                native,
                store,
                flowOf(1000),
                FakePreferencesDataStore().preferences.long(Key.ACTIVE_REMOTE_SERVER_ID),
                openClash = { _, _ ->
                    FakeClashApi().apply {
                        handler = { _, _ ->
                            Json.parseToJsonElement("""{"version":"clash"}""")
                        }
                    }
                },
            )
            val viewModels = ViewModelStore()
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                val editor = RemoteServerEditor(null, remote)
                viewModels.put("editor", editor)
                assertEquals(RemoteBackend.AUTO, editor.state.backend)
                editor.setUrl("https://example.test")
                Snapshot.sendApplyNotifications()
                runCurrent()
                advanceTimeBy(499.milliseconds)
                assertEquals(emptyList(), tests)
                editor.setSecret("new-secret")
                Snapshot.sendApplyNotifications()
                runCurrent()
                advanceTimeBy(500.milliseconds)
                runCurrent()
                assertEquals(listOf("https://example.test" to "new-secret"), tests)
                assertIs<RemoteServerTestState.Success>(editor.state.test)
                editor.setBackend(RemoteBackend.CLASH)
                advanceTimeBy(500.milliseconds)
                runCurrent()
                assertEquals(1, tests.size)
                assertEquals(
                    "Clash clash",
                    assertIs<RemoteServerTestState.Success>(editor.state.test).version,
                )
                val saved = CompletableDeferred<RemoteServer>()
                editor.save { saved.complete(it) }
                runCurrent()
                assertEquals(RemoteBackend.CLASH, saved.await().backend)
                editor.setBackend(RemoteBackend.AUTO)
                advanceTimeBy(500.milliseconds)
                runCurrent()
                assertEquals(2, tests.size)
            } finally {
                viewModels.clear()
                remote.close()
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `late probe result cannot replace a newer editor input`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val native = object : FakeRemoteClient() {
            override suspend fun testConnection(url: String, secret: String): String {
                if (secret == "old") {
                    withContext(NonCancellable) {
                        gate.await()
                    }
                }
                return secret
            }
        }
        val remote = RemoteState(
            backgroundScope,
            native,
            FakeRemoteServerStore(),
            flowOf(1000),
            FakePreferencesDataStore().preferences.long(Key.ACTIVE_REMOTE_SERVER_ID),
        )
        val viewModels = ViewModelStore()
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val editor = RemoteServerEditor(null, remote)
            viewModels.put("editor", editor)
            editor.setBackend(RemoteBackend.SING_BOX)
            editor.setSecret("old")
            Snapshot.sendApplyNotifications()
            runCurrent()
            advanceTimeBy(500.milliseconds)
            runCurrent()
            editor.setSecret("new")
            Snapshot.sendApplyNotifications()
            runCurrent()
            advanceTimeBy(500.milliseconds)
            runCurrent()
            assertEquals(
                "sing-box new",
                assertIs<RemoteServerTestState.Success>(editor.state.test).version,
            )
            gate.complete(Unit)
            runCurrent()
            assertEquals(
                "sing-box new",
                assertIs<RemoteServerTestState.Success>(editor.state.test).version,
            )
        } finally {
            gate.complete(Unit)
            viewModels.clear()
            remote.close()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `legacy records select sing-box without a storage schema migration`() {
        val server = Json.decodeFromString<RemoteServer>("""{"id":1,"url":"https://example.test"}""")
        assertEquals(RemoteBackend.SING_BOX, server.backend)
    }
}
