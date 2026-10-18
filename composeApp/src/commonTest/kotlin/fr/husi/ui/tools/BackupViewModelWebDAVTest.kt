package fr.husi.ui.tools

import fr.husi.database.DataStore
import fr.husi.ktx.kxs
import fr.husi.net.WebDAVEntry
import fr.husi.test.FakeWebDAVClient
import fr.husi.test.HusiKoinMainDispatcherTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class BackupViewModelWebDAVTest : HusiKoinMainDispatcherTest() {

    private val fakeWebDAV = FakeWebDAVClient()

    override suspend fun postStartKoin() {
        DataStore.configurationStore.reset()
        DataStore.webdavURL.set(SERVER_URL)
        DataStore.webdavUsername.set(USERNAME)
        DataStore.webdavPassword.set(PASSWORD)
    }

    /** Backs up settings only, so the tests need no Room database. */
    private fun newViewModel() = BackupViewModel(
        webdavClient = fakeWebDAV,
        ioDispatcher = dispatcher,
    ).also {
        it.setBackupGroupsAndConfig(false)
        it.setBackupRules(false)
        it.setBackupSettings(true)
    }

    @Test
    fun `uploadToWebDAV stores a parsable backup with the configured server`() = runTest(dispatcher.scheduler) {
        val viewModel = newViewModel()
        var succeeded = false

        viewModel.uploadToWebDAV(onSuccess = { succeeded = true }, onFailed = { error(it) })
        advanceUntilIdle()

        assertTrue(succeeded)
        val (name, content) = fakeWebDAV.files.entries.single()
        assertTrue(BackupViewModel.isBackupFileName(name), name)
        val payload = kxs.decodeFromString<BackupPayload>(content.decodeToString())
        assertEquals(BackupViewModel.BACKUP_VERSION, payload.version)
        assertNotNull(payload.settings)
        assertNull(payload.profiles)

        val server = fakeWebDAV.servers.single()
        assertEquals(SERVER_URL, server.url)
        assertEquals(USERNAME, server.username)
        assertEquals(PASSWORD, server.password)
        assertEquals(false, viewModel.uiState.value.isWebDAVBusy)
    }

    @Test
    fun `loadWebDAVBackups keeps backups only, newest first`() = runTest(dispatcher.scheduler) {
        fakeWebDAV.listing = listOf(
            WebDAVEntry(name = "husi_backup_old.json", size = 1, modified = 1_000),
            WebDAVEntry(name = "notes.txt", size = 1, modified = 3_000),
            WebDAVEntry(name = "husi_backup_new.json", size = 1, modified = 2_000),
        )
        val viewModel = newViewModel()

        viewModel.loadWebDAVBackups(WebDAVBackupsPurpose.RESTORE, onFailed = { error(it) })
        advanceUntilIdle()

        val webdavBackups = assertNotNull(viewModel.uiState.value.webdavBackups)
        assertEquals(WebDAVBackupsPurpose.RESTORE, webdavBackups.purpose)
        assertEquals(listOf("husi_backup_new.json", "husi_backup_old.json"), webdavBackups.entries.map { it.name })
    }

    @Test
    fun `restoreFromWebDAV offers the downloaded backup for import`() = runTest(dispatcher.scheduler) {
        val viewModel = newViewModel()
        viewModel.uploadToWebDAV(onSuccess = {}, onFailed = { error(it) })
        advanceUntilIdle()
        viewModel.loadWebDAVBackups(WebDAVBackupsPurpose.RESTORE, onFailed = { error(it) })
        advanceUntilIdle()
        val entry = viewModel.uiState.value.webdavBackups!!.entries.single()

        viewModel.restoreFromWebDAV(entry, onFailed = { error(it) })
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNull(state.webdavBackups)
        assertEquals(BackupViewModel.BACKUP_VERSION, state.inputResult?.version)
    }

    @Test
    fun `deleteFromWebDAV removes every given backup and closes the list`() = runTest(dispatcher.scheduler) {
        fakeWebDAV.files["husi_backup_1.json"] = byteArrayOf()
        fakeWebDAV.files["husi_backup_2.json"] = byteArrayOf()
        fakeWebDAV.files["husi_backup_3.json"] = byteArrayOf()
        val viewModel = newViewModel()
        viewModel.loadWebDAVBackups(WebDAVBackupsPurpose.DELETE, onFailed = { error(it) })
        advanceUntilIdle()
        val entries = viewModel.uiState.value.webdavBackups!!.entries
            .filter { it.name != "husi_backup_2.json" }
        var succeeded = false

        viewModel.deleteFromWebDAV(entries, onSuccess = { succeeded = true }, onFailed = { error(it) })
        advanceUntilIdle()

        assertTrue(succeeded)
        assertEquals(setOf("husi_backup_2.json"), fakeWebDAV.files.keys)
        assertNull(viewModel.uiState.value.webdavBackups)
        assertEquals(false, viewModel.uiState.value.isWebDAVBusy)
    }

    @Test
    fun `a failed WebDAV call reports its message and clears busy`() = runTest(dispatcher.scheduler) {
        fakeWebDAV.nextThrowable = IOException("HTTP 401 Unauthorized")
        val viewModel = newViewModel()
        var failure: String? = null

        viewModel.loadWebDAVBackups(WebDAVBackupsPurpose.RESTORE, onFailed = { failure = it })
        advanceUntilIdle()

        assertEquals("HTTP 401 Unauthorized", failure)
        assertNull(viewModel.uiState.value.webdavBackups)
        assertEquals(false, viewModel.uiState.value.isWebDAVBusy)
    }

    private companion object {
        const val SERVER_URL = "https://dav.example.invalid/husi/"
        const val USERNAME = "user"
        const val PASSWORD = "secret"
    }
}
