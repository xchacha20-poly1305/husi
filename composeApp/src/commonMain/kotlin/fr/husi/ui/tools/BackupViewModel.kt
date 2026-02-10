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
import fr.husi.fmt.KryoConverters
import fr.husi.ktx.Logs
import fr.husi.ktx.b64Decode
import fr.husi.ktx.b64EncodeUrlSafe
import fr.husi.ktx.kxs
import fr.husi.ktx.onDefaultDispatcher
import fr.husi.ktx.onIoDispatcher
import fr.husi.ktx.readableMessage
import fr.husi.ktx.runOnDefaultDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

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
internal data class BackupUiState(
    val options: Int = OPTIONS_GROUPS_AND_CONFIGURATION or OPTIONS_RULES or OPTIONS_SETTINGS,
    val exported: String? = null,
    val inputResult: BackupPayload? = null,
    val isImporting: Boolean = false,
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
internal class BackupViewModel : ViewModel() {

    companion object {
        const val BACKUP_VERSION = 3
        private const val TIME_FORMAT = "yyyy-MM-dd_HH-mm-ss"
    }

    private val _uiState = MutableStateFlow(BackupUiState())
    val uiState = _uiState.asStateFlow()

    fun setBackupGroupsAndConfig(enable: Boolean) = viewModelScope.launch {
        _uiState.update { state ->
            val options = if (enable) {
                state.options or BackupUiState.OPTIONS_GROUPS_AND_CONFIGURATION
            } else {
                state.options and BackupUiState.OPTIONS_GROUPS_AND_CONFIGURATION.inv()
            }
            state.copy(options = options)
        }
    }

    fun setBackupRules(enable: Boolean) = viewModelScope.launch {
        _uiState.update { state ->
            val options = if (enable) {
                state.options or BackupUiState.OPTIONS_RULES
            } else {
                state.options and BackupUiState.OPTIONS_RULES.inv()
            }
            state.copy(options = options)
        }
    }

    fun setBackupSettings(enable: Boolean) = viewModelScope.launch {
        _uiState.update { state ->
            val options = if (enable) {
                state.options or BackupUiState.OPTIONS_SETTINGS
            } else {
                state.options and BackupUiState.OPTIONS_SETTINGS.inv()
            }
            state.copy(options = options)
        }
    }

    fun export() = viewModelScope.launch {
        val state = uiState.value
        val content = onIoDispatcher {
            createBackup(state.backupGroupsAndConfig, state.backupRules, state.backupSettings)
        }
        _uiState.update { state ->
            state.copy(exported = content)
        }
    }

    fun share(
        createFile: (name: String) -> File,
        launch: (File) -> Unit,
        onFailed: (message: String) -> Unit,
    ) = viewModelScope.launch(Dispatchers.IO) {
        val state = uiState.value
        val content =
            createBackup(state.backupGroupsAndConfig, state.backupRules, state.backupSettings)

        val time = LocalDateTime.now().format(DateTimeFormatter.ofPattern(TIME_FORMAT))
        val fileName = "husi_backup_${time}.json"
        try {
            val file = createFile(fileName)
            file.writeText(content)
            launch(file)
        } catch (e: Exception) {
            Logs.e(e)
            onFailed(e.readableMessage)
        }
    }

    fun inputFromBytes(
        bytes: ByteArray,
        onError: (message: String) -> Unit,
    ) = inputFromString(
        content = bytes.decodeToString(),
        onError = onError,
    )

    private fun inputFromString(
        content: String,
        onError: (message: String) -> Unit,
    ) = viewModelScope.launch(Dispatchers.IO) {
        try {
            val backup = kxs.decodeFromString<BackupPayload>(content)
            val version = backup.version
            if (version != BACKUP_VERSION) error("Unsupported backup version $version (expected $BACKUP_VERSION)")
            onDefaultDispatcher {
                _uiState.update { state ->
                    state.copy(
                        inputResult = backup,
                    )
                }
            }
        } catch (e: Exception) {
            Logs.e(e)
            onError(e.readableMessage)
        }
    }

    fun clearInputResult() = viewModelScope.launch {
        _uiState.update { state ->
            state.copy(inputResult = null)
        }
    }

    fun finishInput(
        content: BackupPayload,
        profile: Boolean,
        rule: Boolean,
        setting: Boolean,
    ) = runOnDefaultDispatcher {
        _uiState.update { state ->
            state.copy(
                inputResult = null,
                isImporting = true,
            )
        }
        if (profile && content.profiles != null) {
            val profiles = mutableListOf<ProxyEntity>()
            for (entry in content.profiles) {
                val data = entry.b64Decode()
                profiles.add(KryoConverters.deserialize(ProxyEntity(), data))
            }
            onIoDispatcher {
                SagerDatabase.proxyDao.reset()
                SagerDatabase.proxyDao.insert(profiles)
            }

            val groups = mutableListOf<ProxyGroup>()
            for (entry in content.groups.orEmpty()) {
                val data = entry.b64Decode()
                groups.add(KryoConverters.deserialize(ProxyGroup(), data))
            }
            onIoDispatcher {
                SagerDatabase.groupDao.reset()
                SagerDatabase.groupDao.insert(groups)
            }
        }
        if (rule && content.rules != null) {
            val rules = content.rules
            onIoDispatcher {
                SagerDatabase.rulesDao.reset()
                SagerDatabase.rulesDao.insert(rules)
            }

            val assets = content.assets.orEmpty()
            onIoDispatcher {
                SagerDatabase.assetDao.reset()
                SagerDatabase.assetDao.insert(assets)
            }
        }
        if (setting && content.settings != null) {
            onIoDispatcher {
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
        _uiState.update { state ->
            state.copy(isImporting = false)
        }
    }

    fun postExport() {
        _uiState.update { state ->
            state.copy(exported = null)
        }
    }

    private suspend fun createBackup(profile: Boolean, rule: Boolean, setting: Boolean): String {
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
        return KryoConverters.serialize(this).b64EncodeUrlSafe()
    }
}
