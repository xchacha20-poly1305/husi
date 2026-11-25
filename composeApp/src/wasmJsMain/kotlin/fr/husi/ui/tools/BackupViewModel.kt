package fr.husi.ui.tools

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.husi.Key
import fr.husi.database.DataStore
import fr.husi.database.RemoteServer
import fr.husi.database.preference.DataStorePreferenceDataStore
import fr.husi.database.preference.PreferenceProxy
import fr.husi.ktx.Logs
import fr.husi.ktx.fileNameTimestamp
import fr.husi.ktx.kxs
import fr.husi.remote.ClashApi
import fr.husi.remote.ClashApiException
import fr.husi.remote.ClashRequests
import fr.husi.remote.ClashSession
import fr.husi.remote.RemoteState
import fr.husi.resources.Res
import fr.husi.resources.clash_unsupported
import fr.husi.ui.StringOrRes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement

@Serializable
internal data class WebBackupPayload(
    val version: Int,
    val servers: List<RemoteServer>? = null,
    val settings: JsonObject? = null,
)

@Immutable
internal data class BackupUiState(
    val options: Int = OPTIONS_SERVERS or OPTIONS_SETTINGS,
    val inputResult: WebBackupPayload? = null,
    val isExporting: Boolean = false,
    val isImporting: Boolean = false,
    val isClashStorageBusy: Boolean = false,
) {
    companion object {
        const val OPTIONS_SERVERS = 1 shl 0
        const val OPTIONS_SETTINGS = 1 shl 1
    }

    val backupServers get() = options and OPTIONS_SERVERS != 0
    val backupSettings get() = options and OPTIONS_SETTINGS != 0
    val hasSelection get() = backupServers || backupSettings
}

@Stable
internal class BackupViewModel(
    private val remote: RemoteState,
    private val preferencesStore: DataStorePreferenceDataStore = DataStore.configurationStore,
    private val storageKeyPreference: PreferenceProxy<String> = DataStore.clashStorageBackupKey,
) : ViewModel() {

    companion object {
        const val BACKUP_VERSION = 1
        const val BACKUP_FILE_EXTENSION = "json"
        private const val BACKUP_FILE_PREFIX = "husi_web_backup_"
        const val DEFAULT_CLASH_STORAGE_KEY = DataStore.DEFAULT_CLASH_STORAGE_BACKUP_KEY
        const val MAX_CLASH_STORAGE_KEY_BYTES = 64

        val EXCLUDED_SETTING_KEYS = setOf(
            Key.ACTIVE_REMOTE_SERVER_ID,
            DataStore.KEY_BROWSER_NOTIFICATIONS,
        )

        private val compactJson = Json {
            explicitNulls = false
        }

        fun backupFileName(): String = "$BACKUP_FILE_PREFIX${fileNameTimestamp()}.$BACKUP_FILE_EXTENSION"

        fun normalizeStorageKey(rawKey: String): String = rawKey.trim()

        fun isValidStorageKey(rawKey: String): Boolean {
            val key = normalizeStorageKey(rawKey)
            if (key.isEmpty() || key == "." || key == "..") return false
            if ('/' in key || '\\' in key) return false
            return key.encodeToByteArray().size <= MAX_CLASH_STORAGE_KEY_BYTES
        }
    }

    val uiState: StateFlow<BackupUiState>
        field = MutableStateFlow(BackupUiState())

    val storageKeyState = TextFieldState(DEFAULT_CLASH_STORAGE_KEY)
    private var storageKeyTouched = false

    init {
        viewModelScope.launch {
            val saved = storageKeyPreference.get()
            if (!storageKeyTouched && storageKeyState.text.toString() == DEFAULT_CLASH_STORAGE_KEY) {
                storageKeyState.setTextAndPlaceCursorAtEnd(saved)
            }
        }
    }

    fun setClashStorageKey(value: String) {
        storageKeyTouched = true
        storageKeyState.setTextAndPlaceCursorAtEnd(value)
    }

    fun setBackupServers(enable: Boolean) {
        uiState.update { state ->
            val options = if (enable) {
                state.options or BackupUiState.OPTIONS_SERVERS
            } else {
                state.options and BackupUiState.OPTIONS_SERVERS.inv()
            }
            state.copy(options = options)
        }
    }

    fun setBackupSettings(enable: Boolean) {
        uiState.update { state ->
            val options = if (enable) {
                state.options or BackupUiState.OPTIONS_SETTINGS
            } else {
                state.options and BackupUiState.OPTIONS_SETTINGS.inv()
            }
            state.copy(options = options)
        }
    }

    fun export(
        download: suspend (bytes: ByteArray, fileName: String) -> Unit,
        onSuccess: () -> Unit,
        onFailed: (StringOrRes) -> Unit,
    ) {
        if (uiState.value.isExporting || !uiState.value.hasSelection) return
        viewModelScope.launch {
            uiState.update { it.copy(isExporting = true) }
            try {
                val payload = createBackupPayload()
                val bytes = kxs.encodeToString(payload).encodeToByteArray()
                download(bytes, backupFileName())
                onSuccess()
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                Logs.e("Backup export failed", failure)
                onFailed(StringOrRes.Direct(failure.readableMessage()))
            } finally {
                uiState.update { it.copy(isExporting = false) }
            }
        }
    }

    fun inputFromBytes(
        bytes: ByteArray,
        onError: (StringOrRes) -> Unit,
    ) {
        try {
            val text = bytes.decodeToString(throwOnInvalidSequence = true)
            val backup = parseBackupElement(kxs.parseToJsonElement(text))
            uiState.update { it.copy(inputResult = backup) }
        } catch (failure: Exception) {
            if (failure is CancellationException) throw failure
            Logs.e("Backup parse failed", failure)
            onError(StringOrRes.Direct(failure.readableMessage()))
        }
    }

    fun clearInputResult() {
        uiState.update { it.copy(inputResult = null) }
    }

    fun finishInput(
        content: WebBackupPayload,
        importServers: Boolean,
        importSettings: Boolean,
        onSuccess: () -> Unit,
        onFailed: (StringOrRes) -> Unit,
    ) {
        if (uiState.value.isImporting) return
        val willImportServers = importServers && content.servers != null
        val willImportSettings = importSettings && content.settings != null
        if (!willImportServers && !willImportSettings) {
            clearInputResult()
            return
        }
        viewModelScope.launch {
            uiState.update {
                it.copy(
                    inputResult = null,
                    isImporting = true,
                )
            }
            try {
                if (willImportSettings) {
                    preferencesStore.importFromJson(
                        content.settings,
                        preserveKeys = EXCLUDED_SETTING_KEYS,
                    )
                    storageKeyTouched = true
                    storageKeyState.setTextAndPlaceCursorAtEnd(storageKeyPreference.get())
                }
                if (willImportServers) {
                    remote.replaceServers(content.servers)
                }
                onSuccess()
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                Logs.e("Backup import failed", failure)
                onFailed(StringOrRes.Direct(failure.readableMessage()))
            } finally {
                uiState.update { it.copy(isImporting = false) }
            }
        }
    }

    fun pushToClashStorage(
        onSuccess: () -> Unit,
        onFailed: (StringOrRes) -> Unit,
    ) {
        if (!uiState.value.hasSelection) return
        runClashStorage(onFailed) { api, key ->
            val payload = createBackupPayload()
            val json = compactJson.encodeToString(payload)
            api.request(ClashRequests.storagePut(key, json))
            onSuccess()
        }
    }

    fun pullFromClashStorage(
        onNotFound: () -> Unit,
        onFailed: (StringOrRes) -> Unit,
    ) {
        runClashStorage(onFailed) { api, key ->
            val element = api.request(ClashRequests.storageGet(key))
            if (element is JsonNull) {
                onNotFound()
                return@runClashStorage
            }
            val backup = parseBackupElement(element)
            uiState.update { it.copy(inputResult = backup) }
        }
    }

    fun deleteFromClashStorage(
        onSuccess: () -> Unit,
        onFailed: (StringOrRes) -> Unit,
    ) {
        runClashStorage(onFailed) { api, key ->
            api.request(ClashRequests.storageDelete(key))
            onSuccess()
        }
    }

    private fun runClashStorage(
        onFailed: (StringOrRes) -> Unit,
        block: suspend (api: ClashApi, key: String) -> Unit,
    ) {
        if (uiState.value.isClashStorageBusy) return
        val session = remote.currentSession as? ClashSession ?: return
        val key = normalizeStorageKey(storageKeyState.text.toString())
        if (!isValidStorageKey(key)) return

        storageKeyTouched = true
        if (storageKeyState.text.toString() != key) {
            storageKeyState.setTextAndPlaceCursorAtEnd(key)
        }
        viewModelScope.launch {
            uiState.update { it.copy(isClashStorageBusy = true) }
            try {
                storageKeyPreference.set(key)
                block(session.api, key)
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                Logs.e("Clash storage operation failed", failure)
                val message = if (failure is ClashApiException && failure.unsupported) {
                    StringOrRes.Res(Res.string.clash_unsupported)
                } else {
                    StringOrRes.Direct(failure.readableMessage())
                }
                onFailed(message)
            } finally {
                uiState.update { it.copy(isClashStorageBusy = false) }
            }
        }
    }

    private suspend fun createBackupPayload(): WebBackupPayload {
        val state = uiState.value
        val servers = if (state.backupServers) remote.listServers() else null
        val settings = if (state.backupSettings) {
            preferencesStore.exportToJson(excludeKeys = EXCLUDED_SETTING_KEYS)
        } else {
            null
        }
        return WebBackupPayload(
            version = BACKUP_VERSION,
            servers = servers,
            settings = settings,
        )
    }

    private fun parseBackupElement(element: JsonElement): WebBackupPayload {
        val backup = kxs.decodeFromJsonElement<WebBackupPayload>(element)
        if (backup.version != BACKUP_VERSION) {
            error("Unsupported backup version ${backup.version} (expected $BACKUP_VERSION)")
        }
        if (backup.servers == null && backup.settings == null) {
            error("Backup contains no data")
        }
        backup.servers?.forEach { server ->
            require(server.id >= 0L && server.userOrder >= 0L) {
                "Invalid server ID or order"
            }
        }
        backup.settings?.let { DataStorePreferenceDataStore.validateBackupJson(it) }
        return backup
    }

    private fun Throwable.readableMessage(): String =
        message?.takeIf { it.isNotBlank() } ?: toString()
}
