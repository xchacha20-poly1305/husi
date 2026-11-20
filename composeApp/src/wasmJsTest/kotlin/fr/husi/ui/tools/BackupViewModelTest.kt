@file:OptIn(ExperimentalCoroutinesApi::class)

package fr.husi.ui.tools

import androidx.lifecycle.ViewModelStore
import fr.husi.Key
import fr.husi.database.RemoteBackend
import fr.husi.database.RemoteServer
import fr.husi.database.preference.DataStorePreferenceDataStore
import fr.husi.database.preference.PreferenceProxy
import fr.husi.database.preference.boolean
import fr.husi.database.preference.int
import fr.husi.database.preference.long
import fr.husi.database.preference.string
import fr.husi.ktx.kxs
import fr.husi.net.HttpStatus
import fr.husi.remote.ClashApiException
import fr.husi.remote.RemoteState
import fr.husi.resources.Res
import fr.husi.resources.clash_unsupported
import fr.husi.test.FakeClashApi
import fr.husi.test.FakePreferencesDataStore
import fr.husi.test.FakeRemoteClient
import fr.husi.test.FakeRemoteServerStore
import fr.husi.ui.StringOrRes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BackupViewModelTest {

    private suspend fun TestScope.withBackupHarness(
        api: FakeClashApi = defaultClashApi(),
        block: suspend (Harness) -> Unit,
    ) {
        val prefsStore = FakePreferencesDataStore().preferences
        val activeServerPref = prefsStore.long(Key.ACTIVE_REMOTE_SERVER_ID)
        val storageKeyPref = prefsStore.string("clashStorageBackupKey") {
            BackupViewModel.DEFAULT_CLASH_STORAGE_KEY
        }
        val serverStore = FakeRemoteServerStore()
        val remoteScope = CoroutineScope(backgroundScope.coroutineContext + SupervisorJob())
        val remote = RemoteState(
            scope = remoteScope,
            client = FakeRemoteClient(),
            serverStore = serverStore,
            speedInterval = flowOf(1000),
            activeServerId = activeServerPref,
            openClash = { _, _ -> api },
        )
        val viewModels = ViewModelStore()
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val viewModel = BackupViewModel(
                remote = remote,
                preferencesStore = prefsStore,
                storageKeyPreference = storageKeyPref,
            )
            viewModels.put("backup", viewModel)
            runCurrent()
            block(Harness(viewModel, remote, prefsStore, activeServerPref, storageKeyPref, api))
        } finally {
            viewModels.clear()
            remote.close()
            Dispatchers.resetMain()
        }
    }

    private class Harness(
        val viewModel: BackupViewModel,
        val remote: RemoteState,
        val prefsStore: DataStorePreferenceDataStore,
        val activeServerPref: PreferenceProxy<Long>,
        val storageKeyPref: PreferenceProxy<String>,
        val api: FakeClashApi,
    )

    private fun defaultClashApi(): FakeClashApi = FakeClashApi().apply {
        handler = { operation, _ ->
            when (operation) {
                "version" -> Json.parseToJsonElement("""{"version":"fixture"}""")
                "proxies" -> Json.parseToJsonElement("{}")
                "config" -> Json.parseToJsonElement("""{"mode":"rule"}""")
                else -> JsonNull
            }
        }
    }

    @Test
    fun `export covers all option combinations and excludes active remote server ID`() = runTest {
        withBackupHarness { harness ->
            val saved = harness.remote.save(
                RemoteServer(name = "Main", url = "http://127.0.0.1:9090", secret = "sec", backend = RemoteBackend.CLASH),
            )
            harness.activeServerPref.set(saved.id)
            harness.prefsStore.int(Key.APP_THEME).set(3)
            harness.prefsStore.boolean(Key.LOG_TERMINAL_MODE).set(true)
            runCurrent()

            // Servers + Settings
            var exportedFile: String? = null
            var exportedBytes: ByteArray? = null
            var successCount = 0
            harness.viewModel.export(
                download = { bytes, name ->
                    exportedBytes = bytes
                    exportedFile = name
                },
                onSuccess = { successCount++ },
                onFailed = { error("Unexpected export failure: $it") },
            )
            runCurrent()

            assertEquals(1, successCount)
            val fileName = assertNotNull(exportedFile)
            assertTrue(fileName.startsWith("husi_web_backup_"), fileName)
            assertTrue(fileName.endsWith(".json"), fileName)
            val fullPayload = kxs.decodeFromString<WebBackupPayload>(assertNotNull(exportedBytes).decodeToString())
            assertEquals(BackupViewModel.BACKUP_VERSION, fullPayload.version)
            assertEquals(listOf(saved), fullPayload.servers)
            val fullSettings = assertNotNull(fullPayload.settings)
            assertFalse(Key.ACTIVE_REMOTE_SERVER_ID in fullSettings)
            assertTrue(Key.APP_THEME in fullSettings)
            assertTrue(Key.LOG_TERMINAL_MODE in fullSettings)

            // Servers only
            harness.viewModel.setBackupSettings(false)
            exportedBytes = null
            harness.viewModel.export(
                download = { bytes, _ -> exportedBytes = bytes },
                onSuccess = { successCount++ },
                onFailed = { error("Unexpected export failure: $it") },
            )
            runCurrent()
            val serversOnly = kxs.decodeFromString<WebBackupPayload>(assertNotNull(exportedBytes).decodeToString())
            assertEquals(listOf(saved), serversOnly.servers)
            assertNull(serversOnly.settings)

            // Settings only
            harness.viewModel.setBackupServers(false)
            harness.viewModel.setBackupSettings(true)
            exportedBytes = null
            harness.viewModel.export(
                download = { bytes, _ -> exportedBytes = bytes },
                onSuccess = { successCount++ },
                onFailed = { error("Unexpected export failure: $it") },
            )
            runCurrent()
            val settingsOnly = kxs.decodeFromString<WebBackupPayload>(assertNotNull(exportedBytes).decodeToString())
            assertNull(settingsOnly.servers)
            assertNotNull(settingsOnly.settings)

            // Neither selected -> no-op
            harness.viewModel.setBackupSettings(false)
            assertFalse(harness.viewModel.uiState.value.hasSelection)
            var called = false
            harness.viewModel.export(
                download = { _, _ -> called = true },
                onSuccess = { called = true },
                onFailed = { called = true },
            )
            runCurrent()
            assertFalse(called)
        }
    }

    @Test
    fun `inputFromBytes and finishInput round-trip servers and settings while preserving active server ID`() = runTest {
        withBackupHarness { harness ->
            val original = harness.remote.save(
                RemoteServer(name = "Current", url = "http://127.0.0.1:9090", secret = "s", backend = RemoteBackend.CLASH),
            )
            harness.remote.connect(original)
            runCurrent()
            assertEquals(original.id, harness.activeServerPref.get())

            val sourcePrefs = FakePreferencesDataStore().preferences
            sourcePrefs.int(Key.APP_THEME).set(7)
            sourcePrefs.string("clashStorageBackupKey").set("restored-key")
            val importedSettings = sourcePrefs.exportToJson()
            val importedServers = listOf(
                RemoteServer(id = 10L, userOrder = 1L, name = "Restored", url = "http://127.0.0.1:9090", secret = "s", backend = RemoteBackend.CLASH),
            )
            val payload = WebBackupPayload(
                version = BackupViewModel.BACKUP_VERSION,
                servers = importedServers,
                settings = importedSettings,
            )

            // Parse bytes -> opens confirmation dialog
            harness.viewModel.inputFromBytes(
                bytes = kxs.encodeToString(payload).encodeToByteArray(),
                onError = { error("Unexpected parse error: $it") },
            )
            assertEquals(payload, harness.viewModel.uiState.value.inputResult)

            // Settings-only import preserves activeServerId & updates storageKeyState
            var successCount = 0
            harness.viewModel.finishInput(
                content = payload,
                importServers = false,
                importSettings = true,
                onSuccess = { successCount++ },
                onFailed = { error("Unexpected import error: $it") },
            )
            runCurrent()
            assertEquals(1, successCount)
            assertNull(harness.viewModel.uiState.value.inputResult)
            assertEquals(7, harness.prefsStore.int(Key.APP_THEME).get())
            assertEquals(original.id, harness.activeServerPref.get())
            assertEquals(listOf(original), harness.remote.servers.toList())
            assertEquals("restored-key", harness.viewModel.storageKeyState.text.toString())

            // Servers-only import replaces servers without overwriting settings
            harness.prefsStore.int(Key.APP_THEME).set(9)
            harness.viewModel.finishInput(
                content = payload,
                importServers = true,
                importSettings = false,
                onSuccess = { successCount++ },
                onFailed = { error("Unexpected import error: $it") },
            )
            runCurrent()
            assertEquals(2, successCount)
            assertEquals(importedServers, harness.remote.servers.toList())
            assertEquals(9, harness.prefsStore.int(Key.APP_THEME).get())

            // Full import (servers + settings)
            harness.viewModel.finishInput(
                content = payload,
                importServers = true,
                importSettings = true,
                onSuccess = { successCount++ },
                onFailed = { error("Unexpected import error: $it") },
            )
            runCurrent()
            assertEquals(3, successCount)
            assertEquals(7, harness.prefsStore.int(Key.APP_THEME).get())
            assertEquals(importedServers, harness.remote.servers.toList())
            assertFalse(harness.viewModel.uiState.value.isImporting)
        }
    }

    @Test
    fun `inputFromBytes rejects malformed or incompatible payloads`() = runTest {
        withBackupHarness { harness ->
            val invalidPayloads = listOf(
                "{not-json",
                """{"servers":[]}""",
                """{"version":3,"servers":[]}""",
                """{"version":1}""",
                """{"version":1,"servers":[{"id":-1,"userOrder":1,"name":"Bad","url":"http://a","secret":""}]}""",
                """{"version":1,"settings":{"__version":99}}""",
            )
            for (raw in invalidPayloads) {
                var error: StringOrRes? = null
                harness.viewModel.inputFromBytes(raw.encodeToByteArray()) { error = it }
                assertNotNull(error, "Expected rejection for $raw")
                assertNull(harness.viewModel.uiState.value.inputResult)
            }
        }
    }

    @Test
    fun `clash storage push pull and delete round-trip through ClashApi`() = runTest {
        val stored = mutableMapOf<String, JsonElement>()
        val api = defaultClashApi().apply {
            handler = { operation, input ->
                when (operation) {
                    "version" -> Json.parseToJsonElement("""{"version":"fixture"}""")
                    "proxies" -> Json.parseToJsonElement("{}")
                    "config" -> Json.parseToJsonElement("""{"mode":"rule"}""")
                    "storagePut" -> {
                        val key = input.getValue("key").jsonPrimitive.content
                        val value = input.getValue("value").jsonPrimitive.content
                        assertFalse('\n' in value, "Compact JSON expected for storagePut")
                        stored[key] = Json.parseToJsonElement(value)
                        JsonNull
                    }

                    "storageGet" -> {
                        val key = input.getValue("key").jsonPrimitive.content
                        stored[key] ?: JsonNull
                    }

                    "storageDelete" -> {
                        val key = input.getValue("key").jsonPrimitive.content
                        stored.remove(key)
                        JsonNull
                    }

                    else -> error("Unexpected operation $operation")
                }
            }
        }

        withBackupHarness(api) { harness ->
            val server = harness.remote.save(
                RemoteServer(name = "Clash", url = "http://127.0.0.1:9090", secret = "s", backend = RemoteBackend.CLASH),
            )
            harness.remote.connect(server)
            runCurrent()

            harness.prefsStore.int(Key.APP_THEME).set(5)
            harness.viewModel.setClashStorageKey("  my-backup-key  ")

            var pushed = false
            harness.viewModel.pushToClashStorage(
                onSuccess = { pushed = true },
                onFailed = { error("Unexpected push failure: $it") },
            )
            runCurrent()

            assertTrue(pushed)
            assertEquals("my-backup-key", harness.storageKeyPref.get())
            assertEquals("my-backup-key", harness.viewModel.storageKeyState.text.toString())
            assertTrue("my-backup-key" in stored)
            assertFalse(harness.viewModel.uiState.value.isClashStorageBusy)

            // Pull populates inputResult
            var notFound = false
            harness.viewModel.pullFromClashStorage(
                onNotFound = { notFound = true },
                onFailed = { error("Unexpected pull failure: $it") },
            )
            runCurrent()

            assertFalse(notFound)
            val pulled = assertNotNull(harness.viewModel.uiState.value.inputResult)
            assertEquals(listOf(server), pulled.servers)
            assertNotNull(pulled.settings)
            assertFalse(Key.ACTIVE_REMOTE_SERVER_ID in pulled.settings)
            harness.viewModel.clearInputResult()

            // Delete removes entry
            var deleted = false
            harness.viewModel.deleteFromClashStorage(
                onSuccess = { deleted = true },
                onFailed = { error("Unexpected delete failure: $it") },
            )
            runCurrent()
            assertTrue(deleted)
            assertFalse("my-backup-key" in stored)

            // Pull when empty invokes onNotFound
            harness.viewModel.pullFromClashStorage(
                onNotFound = { notFound = true },
                onFailed = { error("Unexpected pull failure: $it") },
            )
            runCurrent()
            assertTrue(notFound)
            assertNull(harness.viewModel.uiState.value.inputResult)
        }
    }

    @Test
    fun `clash storage maps unsupported HTTP statuses and generic errors`() = runTest {
        for (status in listOf(HttpStatus.NOT_FOUND, HttpStatus.METHOD_NOT_ALLOWED, HttpStatus.NOT_IMPLEMENTED)) {
            val api = defaultClashApi().apply {
                handler = { operation, _ ->
                    when (operation) {
                        "version" -> Json.parseToJsonElement("""{"version":"fixture"}""")
                        "proxies" -> Json.parseToJsonElement("{}")
                        "config" -> Json.parseToJsonElement("""{"mode":"rule"}""")
                        "storageGet", "storageDelete" -> throw ClashApiException("http", status, "Unsupported")
                        else -> JsonNull
                    }
                }
            }

            withBackupHarness(api) { harness ->
                val server = harness.remote.save(
                    RemoteServer(name = "Clash", url = "http://127.0.0.1:9090", backend = RemoteBackend.CLASH),
                )
                harness.remote.connect(server)
                runCurrent()

                var pullError: StringOrRes? = null
                harness.viewModel.pullFromClashStorage(
                    onNotFound = { error("Should not report not-found on HTTP error") },
                    onFailed = { pullError = it },
                )
                runCurrent()
                assertEquals(StringOrRes.Res(Res.string.clash_unsupported), pullError)

                var deleteError: StringOrRes? = null
                harness.viewModel.deleteFromClashStorage(
                    onSuccess = { error("Should not succeed") },
                    onFailed = { deleteError = it },
                )
                runCurrent()
                assertEquals(StringOrRes.Res(Res.string.clash_unsupported), deleteError)
                assertFalse(harness.viewModel.uiState.value.isClashStorageBusy)
            }
        }

        val failingApi = defaultClashApi().apply {
            handler = { operation, _ ->
                when (operation) {
                    "version" -> Json.parseToJsonElement("""{"version":"fixture"}""")
                    "proxies" -> Json.parseToJsonElement("{}")
                    "config" -> Json.parseToJsonElement("""{"mode":"rule"}""")
                    "storagePut" -> throw ClashApiException("http", HttpStatus.INTERNAL_SERVER_ERROR, "Server failure")
                    else -> JsonNull
                }
            }
        }
        withBackupHarness(failingApi) { harness ->
            val server = harness.remote.save(
                RemoteServer(name = "Clash", url = "http://127.0.0.1:9090", backend = RemoteBackend.CLASH),
            )
            harness.remote.connect(server)
            runCurrent()

            var pushError: StringOrRes? = null
            harness.viewModel.pushToClashStorage(
                onSuccess = { error("Should not succeed") },
                onFailed = { pushError = it },
            )
            runCurrent()
            assertEquals(StringOrRes.Direct("Server failure"), pushError)
            assertFalse(harness.viewModel.uiState.value.isClashStorageBusy)
        }
    }

    @Test
    fun `isValidStorageKey enforces mihomo key constraints and UTF-8 byte length`() {
        assertFalse(BackupViewModel.isValidStorageKey(""))
        assertFalse(BackupViewModel.isValidStorageKey("   "))
        assertFalse(BackupViewModel.isValidStorageKey("."))
        assertFalse(BackupViewModel.isValidStorageKey(".."))
        assertFalse(BackupViewModel.isValidStorageKey("dir/key"))
        assertFalse(BackupViewModel.isValidStorageKey("dir\\key"))
        assertTrue(BackupViewModel.isValidStorageKey("  husi-backup  "))
        assertTrue(BackupViewModel.isValidStorageKey("a".repeat(64)))
        assertFalse(BackupViewModel.isValidStorageKey("a".repeat(65)))
        assertTrue(BackupViewModel.isValidStorageKey("中".repeat(21)))
        assertFalse(BackupViewModel.isValidStorageKey("中".repeat(22)))
    }
}
