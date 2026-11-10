package fr.husi.di

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.snapshots.Snapshot
import androidx.lifecycle.ViewModelStore
import fr.husi.database.RemoteServerEntity
import fr.husi.proto.daemon.Version
import fr.husi.remote.RemoteClient
import fr.husi.remote.RemoteState
import fr.husi.repository.Repository
import fr.husi.repository.WebRepository
import fr.husi.tailscale.RemoteSshSessions
import fr.husi.test.FakeRemoteClient
import fr.husi.test.FakeRemoteServerDao
import fr.husi.ui.MainScreenScope
import fr.husi.ui.remote.RemoteServerEditor
import fr.husi.ui.remote.RemoteServerTestState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.koin.core.parameter.parametersOf
import org.koin.dsl.koinApplication
import org.koin.dsl.module

class RemoteApplicationModuleTest {
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `scoped editor saves and tests current text field state with the shared remote client`() = runTest {
        val client = FakeClient()
        val serverDao = FakeRemoteServerDao()
        val application = koinApplication {
            modules(browserPlatformModule, remoteApplicationModule, module {
                single<RemoteClient> { client }
                single<RemoteServerEntity.Dao> { serverDao }
            })
        }
        val scope = application.koin.createScope<MainScreenScope>()
        val store = ViewModelStore()
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val repository = scope.get<Repository>()
            assertIs<WebRepository>(repository)
            assertSame(application.koin.get<Repository>(), repository)
            val remote = scope.get<RemoteState>()
            assertSame(remote, scope.get<RemoteState>())
            val editor = scope.get<RemoteServerEditor> { parametersOf(null as Long?) }
            store.put("editor", editor)
            assertEquals(RemoteServerTestState.Idle, editor.state.test)
            editor.nameState.setTextAndPlaceCursorAtEnd("Remote server")
            editor.urlState.setTextAndPlaceCursorAtEnd("https://example.com")
            editor.secretState.setTextAndPlaceCursorAtEnd("test-secret")
            val saved = CompletableDeferred<Unit>()
            editor.save { saved.complete(Unit) }
            saved.await()
            val server = serverDao.list().first().single()
            assertEquals("Remote server", server.name)
            assertEquals("https://example.com", server.url)
            assertEquals("test-secret", server.secret)

            Snapshot.sendApplyNotifications()
            advanceUntilIdle()
            assertEquals(listOf(server.url to server.secret), client.connectionTests)
            assertIs<RemoteServerTestState.Success>(editor.state.test)

            editor.secretState.setTextAndPlaceCursorAtEnd("changed-secret")
            Snapshot.sendApplyNotifications()
            advanceUntilIdle()
            assertEquals(
                listOf(server.url to server.secret, server.url to "changed-secret"),
                client.connectionTests,
            )
            assertSame(client, remote.client)
            assertTrue(scope.get<RemoteSshSessions>().sessions.isEmpty())
        } finally {
            store.clear()
            scope.close()
            application.close()
            Dispatchers.resetMain()
        }
        assertEquals(1, client.closeCount)
    }

    private class FakeClient : FakeRemoteClient() {
        var closeCount = 0
        val connectionTests = mutableListOf<Pair<String, String>>()
        override suspend fun connect(url: String, secret: String) = Version()
        override suspend fun call(method: String, request: ByteArray) = byteArrayOf()
        override fun stream(method: String, request: ByteArray): Flow<ByteArray> = emptyFlow()
        override fun close() { closeCount++ }
        override suspend fun testConnection(url: String, secret: String): String {
            connectionTests.add(url to secret)
            return "test"
        }
        override fun normalizeUrl(value: String) = value
    }
}
