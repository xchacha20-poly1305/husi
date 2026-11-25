package fr.husi.ui.tools

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.husi.database.AssetEntity
import fr.husi.database.DataStore
import fr.husi.database.ProxyEntity
import fr.husi.database.ProxyGroup
import fr.husi.database.RuleEntity
import fr.husi.database.SagerDatabase
import fr.husi.fmt.BeanConverters
import fr.husi.ktx.Logs
import fr.husi.ktx.b64Decode
import fr.husi.ktx.b64EncodeUrlSafe
import fr.husi.ktx.fileNameTimestamp
import fr.husi.ktx.kxs
import fr.husi.ktx.readableMessage
import fr.husi.ktx.runOnDefaultDispatcher
import fr.husi.net.WebDAVClient
import fr.husi.net.WebDAVEntry
import fr.husi.net.WebDAVServer
import fr.husi.net.localHttpProxy
import fr.husi.net.resolveWebDAVClient
import fr.husi.repository.resolveRepository
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.write
import io.github.xchacha20_poly1305.yan.Age
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

@Serializable
internal data class BackupPayload(
    val version: Int,
    val profiles: List<String>? = null,
    val groups: List<String>? = null,
    val rules: List<RuleEntity>? = null,
    val assets: List<AssetEntity>? = null,
    val settings: JsonElement? = null,
)

@Immutable
internal data class WebDAVConfig(
    val url: String = "",
    val username: String = "",
    val password: String = "",
) {
    val isConfigured get() = url.isNotBlank()
}

internal enum class WebDAVBackupsPurpose {
    RESTORE,
    DELETE,
}

/** The backups found on the WebDAV server while the user picks from them. */
@Immutable
internal data class WebDAVBackups(
    val purpose: WebDAVBackupsPurpose,
    /** Newest first. */
    val entries: List<WebDAVEntry>,
)

internal class BackupFile(val name: String, val bytes: ByteArray, val encrypted: Boolean)

@Immutable
internal data class BackupUiState(
    val options: Int = OPTIONS_GROUPS_AND_CONFIGURATION or OPTIONS_RULES or OPTIONS_SETTINGS,
    val exported: BackupFile? = null,
    val isExporting: Boolean = false,
    val inputResult: BackupPayload? = null,
    val encryptedInput: EncryptedBackup? = null,
    val isImporting: Boolean = false,
    val webdav: WebDAVConfig = WebDAVConfig(),
    val webdavBackups: WebDAVBackups? = null,
    val isWebDAVBusy: Boolean = false,
) {
    companion object {
        const val OPTIONS_GROUPS_AND_CONFIGURATION = 1 shl 0
        const val OPTIONS_RULES = 1 shl 1
        const val OPTIONS_SETTINGS = 1 shl 2
    }

    val backupGroupsAndConfig get() = options and OPTIONS_GROUPS_AND_CONFIGURATION != 0
    val backupRules get() = options and OPTIONS_RULES != 0
    val backupSettings get() = options and OPTIONS_SETTINGS != 0
}

@Stable
internal class BackupViewModel(
    private val webdavClient: WebDAVClient = resolveWebDAVClient(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {

    companion object {
        const val BACKUP_VERSION = 3
        const val BACKUP_FILE_EXTENSION = "json"
        private const val ENCRYPTED_BACKUP_EXTENSION = "$BACKUP_FILE_EXTENSION.age"
        private const val BACKUP_FILE_PREFIX = "husi_backup_"

        fun isBackupFileName(name: String): Boolean =
            name.startsWith(BACKUP_FILE_PREFIX) &&
                    (name.endsWith(".$BACKUP_FILE_EXTENSION") || name.endsWith(".$ENCRYPTED_BACKUP_EXTENSION"))
    }

    val uiState: StateFlow<BackupUiState>
        field = MutableStateFlow(BackupUiState())

    init {
        viewModelScope.launch {
            combine(
                DataStore.webdavURL.flow(),
                DataStore.webdavUsername.flow(),
                DataStore.webdavPassword.flow(),
                ::WebDAVConfig,
            ).collect { config ->
                uiState.update { state -> state.copy(webdav = config) }
            }
        }
    }

    fun setBackupGroupsAndConfig(enable: Boolean) = viewModelScope.launch {
        uiState.update { state ->
            val options = if (enable) {
                state.options or BackupUiState.OPTIONS_GROUPS_AND_CONFIGURATION
            } else {
                state.options and BackupUiState.OPTIONS_GROUPS_AND_CONFIGURATION.inv()
            }
            state.copy(options = options)
        }
    }

    fun setBackupRules(enable: Boolean) = viewModelScope.launch {
        uiState.update { state ->
            val options = if (enable) {
                state.options or BackupUiState.OPTIONS_RULES
            } else {
                state.options and BackupUiState.OPTIONS_RULES.inv()
            }
            state.copy(options = options)
        }
    }

    fun setBackupSettings(enable: Boolean) = viewModelScope.launch {
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
        encryption: BackupEncryption?,
        onFailed: (message: String) -> Unit,
    ) = viewModelScope.launch {
        if (uiState.value.isExporting || uiState.value.exported != null) return@launch
        uiState.update { state -> state.copy(isExporting = true) }
        try {
            val backup = withContext(ioDispatcher) { createBackupFile(encryption) }
            uiState.update { state -> state.copy(exported = backup) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logs.e(e)
            onFailed(e.readableMessage)
        } finally {
            uiState.update { state -> state.copy(isExporting = false) }
        }
    }

    fun share(
        encryption: BackupEncryption?,
        launch: (PlatformFile) -> Unit,
        onFailed: (message: String) -> Unit,
    ) = viewModelScope.launch(ioDispatcher) {
        try {
            val backup = createBackupFile(encryption)
            val file = PlatformFile(resolveRepository().cacheDir, backup.name)
            file.write(backup.bytes)
            launch(file)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logs.e(e)
            onFailed(e.readableMessage)
        }
    }

    fun saveWebDAVConfig(config: WebDAVConfig) = viewModelScope.launch {
        DataStore.webdavURL.set(config.url.trim())
        DataStore.webdavUsername.set(config.username)
        DataStore.webdavPassword.set(config.password)
    }

    fun uploadToWebDAV(
        encryption: BackupEncryption? = null,
        onSuccess: () -> Unit,
        onFailed: (message: String) -> Unit,
    ) = runWebDAV(onFailed) {
        val backup = createBackupFile(encryption)
        webdavClient.upload(webdavServer(), backup.name, backup.bytes)
        onSuccess()
    }

    fun loadWebDAVBackups(
        purpose: WebDAVBackupsPurpose,
        onFailed: (message: String) -> Unit,
    ) = runWebDAV(onFailed) {
        val entries = webdavClient.list(webdavServer())
            .filter { isBackupFileName(it.name) }
            .sortedWith(compareByDescending<WebDAVEntry> { it.modified }.thenByDescending { it.name })
        uiState.update { state -> state.copy(webdavBackups = WebDAVBackups(purpose, entries)) }
    }

    fun dismissWebDAVBackups() {
        uiState.update { state -> state.copy(webdavBackups = null) }
    }

    fun restoreFromWebDAV(
        entry: WebDAVEntry,
        onFailed: (message: String) -> Unit,
    ) = runWebDAV(onFailed) {
        dismissWebDAVBackups()
        val content = webdavClient.download(webdavServer(), entry.name)
        input(content)
    }

    /** Stops at the first failure; the backups before it stay deleted. */
    fun deleteFromWebDAV(
        entries: List<WebDAVEntry>,
        onSuccess: () -> Unit,
        onFailed: (message: String) -> Unit,
    ) = runWebDAV(onFailed) {
        dismissWebDAVBackups()
        val server = webdavServer()
        for (entry in entries) {
            webdavClient.delete(server, entry.name)
        }
        onSuccess()
    }

    fun inputFromBytes(
        bytes: ByteArray,
        onError: (message: String) -> Unit,
    ) = viewModelScope.launch(ioDispatcher) {
        try {
            input(bytes)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logs.e(e)
            onError(e.readableMessage)
        }
    }

    private fun input(bytes: ByteArray) {
        val encrypted = EncryptedBackup.detect(bytes)
        val backup = if (encrypted == null) decodeBackup(bytes) else null
        uiState.update { state -> state.copy(inputResult = backup, encryptedInput = encrypted) }
    }

    suspend fun decryptInput(backup: EncryptedBackup, identity: String) {
        val decoded = withContext(ioDispatcher) {
            val plaintext = backup.decrypt(identity)
            try {
                decodeBackup(plaintext)
            } finally {
                plaintext.fill(0)
            }
        }
        uiState.update { state ->
            if (state.encryptedInput !== backup) state
            else state.copy(inputResult = decoded, encryptedInput = null)
        }
    }

    fun dismissEncryptedInput() {
        uiState.update { state -> state.copy(encryptedInput = null) }
    }

    private fun decodeBackup(bytes: ByteArray): BackupPayload {
        val backup = try {
            kxs.decodeFromString<BackupPayload>(bytes.decodeToString())
        } catch (_: IllegalArgumentException) {
            // Serialization errors can contain the contents of the selected file.
            throw IllegalArgumentException("Invalid backup JSON")
        }
        val version = backup.version
        if (version != BACKUP_VERSION) error("Unsupported backup version $version (expected $BACKUP_VERSION)")
        return backup
    }

    private fun runWebDAV(
        onFailed: (message: String) -> Unit,
        block: suspend () -> Unit,
    ) = viewModelScope.launch(ioDispatcher) {
        uiState.update { state -> state.copy(isWebDAVBusy = true) }
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logs.e(e)
            onFailed(e.readableMessage)
        } finally {
            uiState.update { state -> state.copy(isWebDAVBusy = false) }
        }
    }

    private suspend fun webdavServer() = WebDAVServer(
        url = DataStore.webdavURL.get(),
        username = DataStore.webdavUsername.get(),
        password = DataStore.webdavPassword.get(),
        proxy = localHttpProxy(),
    )

    fun clearInputResult() = viewModelScope.launch {
        uiState.update { state ->
            state.copy(inputResult = null)
        }
    }

    fun finishInput(
        content: BackupPayload,
        profile: Boolean,
        rule: Boolean,
        setting: Boolean,
    ) = runOnDefaultDispatcher {
        uiState.update { state ->
            state.copy(
                inputResult = null,
                isImporting = true,
            )
        }
        if (profile && content.profiles != null) {
            val profiles = mutableListOf<ProxyEntity>()
            for (entry in content.profiles) {
                val data = entry.b64Decode()
                profiles.add(BeanConverters.deserialize(ProxyEntity(), data))
            }
            withContext(ioDispatcher) {
                SagerDatabase.proxyDao.reset()
                SagerDatabase.proxyDao.insert(profiles)
            }

            val groups = mutableListOf<ProxyGroup>()
            for (entry in content.groups.orEmpty()) {
                val data = entry.b64Decode()
                groups.add(BeanConverters.deserialize(ProxyGroup(), data))
            }
            withContext(ioDispatcher) {
                SagerDatabase.groupDao.reset()
                SagerDatabase.groupDao.insert(groups)
            }
        }
        if (rule && content.rules != null) {
            val rules = content.rules
            withContext(ioDispatcher) {
                SagerDatabase.rulesDao.reset()
                SagerDatabase.rulesDao.insert(rules)
            }

            val assets = content.assets.orEmpty()
            withContext(ioDispatcher) {
                SagerDatabase.assetDao.reset()
                SagerDatabase.assetDao.insert(assets)
            }
        }
        if (setting && content.settings != null) {
            withContext(ioDispatcher) {
                when (val rawSettings = content.settings) {
                    is JsonArray -> {
                        importLegacySettingPairs(rawSettings)
                    }

                    is JsonObject -> {
                        DataStore.configurationStore.importFromJson(rawSettings)
                    }

                    else -> {
                        Logs.w("Ignore settings import: unsupported format ${rawSettings::class.simpleName}")
                    }
                }
            }
        }
        uiState.update { state ->
            state.copy(isImporting = false)
        }
    }

    fun postExport() {
        uiState.update { state ->
            state.copy(exported = null)
        }
    }

    private suspend fun createBackupFile(encryption: BackupEncryption?): BackupFile {
        val extension = if (encryption == null) BACKUP_FILE_EXTENSION else ENCRYPTED_BACKUP_EXTENSION
        val name = "$BACKUP_FILE_PREFIX${fileNameTimestamp()}.$extension"
        val plaintext = createBackup().encodeToByteArray()
        if (encryption == null) return BackupFile(name, plaintext, encrypted = false)
        val ciphertext = try {
            Age.encryptBytes(plaintext, listOf(encryption.recipient), armor = encryption.armor)
        } finally {
            plaintext.fill(0)
        }
        return BackupFile(name, ciphertext, encrypted = true)
    }

    private suspend fun createBackup(): String {
        val state = uiState.value
        val profile = state.backupGroupsAndConfig
        val rule = state.backupRules
        val setting = state.backupSettings
        val profiles = if (profile) SagerDatabase.proxyDao.getAll().map { it.toBase64Str() } else null
        val groups = if (profile) SagerDatabase.groupDao.allGroups().first().map { it.toBase64Str() } else null
        val rules = if (rule) SagerDatabase.rulesDao.allRules().first() else null
        val assets = if (rule) SagerDatabase.assetDao.getAll().first() else null
        val settings = if (setting) DataStore.configurationStore.exportToJson() else null
        val backup = BackupPayload(
            version = BACKUP_VERSION,
            profiles = profiles,
            groups = groups,
            rules = rules,
            assets = assets,
            settings = settings,
        )
        return kxs.encodeToString(backup)
    }

    private fun fr.husi.fmt.Serializable.toBase64Str(): String {
        return BeanConverters.serialize(this).b64EncodeUrlSafe()
    }
}
