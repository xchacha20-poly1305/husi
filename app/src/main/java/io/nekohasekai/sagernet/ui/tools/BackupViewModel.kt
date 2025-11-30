package io.nekohasekai.sagernet.ui.tools

import android.os.Parcel
import android.os.Parcelable
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.nekohasekai.sagernet.database.AssetEntity
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.ParcelizeBridge
import io.nekohasekai.sagernet.database.ProxyEntity
import io.nekohasekai.sagernet.database.ProxyGroup
import io.nekohasekai.sagernet.database.RuleEntity
import io.nekohasekai.sagernet.database.SagerDatabase
import io.nekohasekai.sagernet.database.preference.KeyValuePair
import io.nekohasekai.sagernet.ktx.Logs
import io.nekohasekai.sagernet.ktx.b64Decode
import io.nekohasekai.sagernet.ktx.b64EncodeUrlSafe
import io.nekohasekai.sagernet.ktx.forEach
import io.nekohasekai.sagernet.ktx.getIntOrNull
import io.nekohasekai.sagernet.ktx.onDefaultDispatcher
import io.nekohasekai.sagernet.ktx.onIoDispatcher
import io.nekohasekai.sagernet.ktx.readableMessage
import io.nekohasekai.sagernet.ktx.runOnDefaultDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Stable
internal data class BackupUiState(
    val options: Int = OPTIONS_GROUPS_AND_CONFIGURATION or OPTIONS_RULES or OPTIONS_SETTINGS,
    val exported: String? = null,
    val inputResult: JSONObject? = null,
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
        const val BACKUP_VERSION = 1
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

    fun inputFromStream(
        inputStream: InputStream,
        onError: (message: String) -> Unit,
    ) = viewModelScope.launch(Dispatchers.IO) {
        try {
            val contentString = inputStream.bufferedReader().use(BufferedReader::readText)
            val content = JSONObject(contentString)
            val version = content.getIntOrNull("version")
            if (version != BACKUP_VERSION) error("invalid version $version")
            onDefaultDispatcher {
                _uiState.update { state ->
                    state.copy(
                        inputResult = content,
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
        content: JSONObject,
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
        if (profile && content.has("profiles")) {
            val profiles = mutableListOf<ProxyEntity>()
            val jsonProfiles = content.getJSONArray("profiles")
            jsonProfiles.forEach { profile ->
                val data = (profile as String).b64Decode()
                val parcel = Parcel.obtain()
                parcel.unmarshall(data, 0, data.size)
                parcel.setDataPosition(0)
                profiles.add(ProxyEntity.CREATOR.createFromParcel(parcel))
                parcel.recycle()
            }
            onIoDispatcher {
                SagerDatabase.proxyDao.reset()
                SagerDatabase.proxyDao.insert(profiles)
            }

            val groups = mutableListOf<ProxyGroup>()
            val jsonGroups = content.getJSONArray("groups")
            jsonGroups.forEach { group ->
                val data = (group as String).b64Decode()
                val parcel = Parcel.obtain()
                parcel.unmarshall(data, 0, data.size)
                parcel.setDataPosition(0)
                groups.add(ProxyGroup.CREATOR.createFromParcel(parcel))
                parcel.recycle()
            }
            onIoDispatcher {
                SagerDatabase.groupDao.reset()
                SagerDatabase.groupDao.insert(groups)
            }
        }
        if (rule && content.has("rules")) {
            val rules = mutableListOf<RuleEntity>()
            val jsonRules = content.getJSONArray("rules")
            jsonRules.forEach { rule ->
                val data = (rule as String).b64Decode()
                val parcel = Parcel.obtain()
                parcel.unmarshall(data, 0, data.size)
                parcel.setDataPosition(0)
                rules.add(ParcelizeBridge.createRule(parcel))
                parcel.recycle()
            }
            onIoDispatcher {
                SagerDatabase.rulesDao.reset()
                SagerDatabase.rulesDao.insert(rules)
            }

            val assets = mutableListOf<AssetEntity>()
            val jsonAssets = content.getJSONArray("assets")
            jsonAssets.forEach { asset ->
                val data = (asset as String).b64Decode()
                val parcel = Parcel.obtain()
                parcel.unmarshall(data, 0, data.size)
                parcel.setDataPosition(0)
                assets.add(ParcelizeBridge.createAsset(parcel))
                parcel.recycle()
            }
            onIoDispatcher {
                SagerDatabase.assetDao.reset()
                SagerDatabase.assetDao.insert(assets)
            }
        }
        if (setting && content.has("settings")) {
            onIoDispatcher {
                when (val rawSettings = content.get("settings")) {
                    is JSONArray -> {
                        val pairs = mutableListOf<KeyValuePair>()
                        rawSettings.forEach { settingItem ->
                            val data = (settingItem as String).b64Decode()
                            val parcel = Parcel.obtain()
                            parcel.unmarshall(data, 0, data.size)
                            parcel.setDataPosition(0)
                            pairs.add(KeyValuePair.CREATOR.createFromParcel(parcel))
                            parcel.recycle()
                        }
                        DataStore.configurationStore.importLegacyPairs(pairs)
                    }

                    is JSONObject -> {
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
        val out = JSONObject().apply {
            put("version", BACKUP_VERSION)
            if (profile) {
                put(
                    "profiles",
                    JSONArray().apply {
                        SagerDatabase.proxyDao.getAll().forEach {
                            put(it.toBase64Str())
                        }
                    },
                )
                put(
                    "groups",
                    JSONArray().apply {
                        SagerDatabase.groupDao.allGroups().first().forEach {
                            put(it.toBase64Str())
                        }
                    },
                )
            }
            if (rule) {
                put(
                    "rules",
                    JSONArray().apply {
                        SagerDatabase.rulesDao.allRules().first().forEach {
                            put(it.toBase64Str())
                        }
                    },
                )
                put(
                    "assets",
                    JSONArray().apply {
                        SagerDatabase.assetDao.getAll().first().forEach {
                            put(it.toBase64Str())
                        }
                    },
                )
            }
            if (setting) {
                put(
                    "settings",
                    JSONObject().apply {
                        DataStore.configurationStore.exportToJson(this)
                    },
                )
            }
        }
        return out.toString(2)
    }

    private fun Parcelable.toBase64Str(): String {
        val parcel = Parcel.obtain()
        try {
            writeToParcel(parcel, 0)
            return parcel.marshall().b64EncodeUrlSafe()
        } finally {
            parcel.recycle()
        }
    }
}