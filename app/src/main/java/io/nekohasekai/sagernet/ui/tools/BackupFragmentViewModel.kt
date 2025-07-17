package io.nekohasekai.sagernet.ui.tools

import android.os.Parcel
import android.os.Parcelable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.nekohasekai.sagernet.SagerNet
import io.nekohasekai.sagernet.database.ParcelizeBridge
import io.nekohasekai.sagernet.database.ProxyEntity
import io.nekohasekai.sagernet.database.ProxyGroup
import io.nekohasekai.sagernet.database.RuleEntity
import io.nekohasekai.sagernet.database.SagerDatabase
import io.nekohasekai.sagernet.database.preference.KeyValuePair
import io.nekohasekai.sagernet.database.preference.PublicDatabase
import io.nekohasekai.sagernet.ktx.Logs
import io.nekohasekai.sagernet.ktx.b64Decode
import io.nekohasekai.sagernet.ktx.b64EncodeUrlSafe
import io.nekohasekai.sagernet.ktx.readableMessage
import io.nekohasekai.sagernet.ktx.runOnIoDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

internal sealed class BackupFragmentEvent {
    data class RequestExport(val fileName: String, val content: String) : BackupFragmentEvent()
    data class RequestShare(val fileName: String, val content: String) : BackupFragmentEvent()
    data class ShowImportDialog(
        val contentJson: String,
        val hasProfiles: Boolean,
        val hasRules: Boolean,
        val hasSettings: Boolean
    ) : BackupFragmentEvent()

    data class ShowSnackbar(val message: String) : BackupFragmentEvent()
    data class ShowError(val message: String) : BackupFragmentEvent()
    object RestartApp : BackupFragmentEvent()
}

internal class BackupViewModel : ViewModel() {

    companion object {
        const val TIME_FORMAT = "yyyy-MM-dd_HH-mm-ss"
        const val MIME_TYPE = "application/json"
    }

    private val _uiEvent = MutableSharedFlow<BackupFragmentEvent>()
    val uiEvent = _uiEvent.asSharedFlow()

    private val _isImporting = MutableStateFlow(false)
    val isImporting = _isImporting.asStateFlow()

    fun onExportClicked(profile: Boolean, rule: Boolean, setting: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val content = createBackup(profile, rule, setting)
            val time = LocalDateTime.now().format(DateTimeFormatter.ofPattern(TIME_FORMAT))
            val fileName = "husi_backup_${time}"
            _uiEvent.emit(BackupFragmentEvent.RequestExport(fileName, content))
        }
    }

    fun onShareClicked(profile: Boolean, rule: Boolean, setting: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val content = createBackup(profile, rule, setting)
            val time = LocalDateTime.now().format(DateTimeFormatter.ofPattern(TIME_FORMAT))
            val fileName = "husi_backup_${time}.json"
            _uiEvent.emit(BackupFragmentEvent.RequestShare(fileName, content))
        }
    }

    fun onFileSelectedForImport(fileName: String?, inputStream: InputStream?) {
        if (inputStream == null) {
            viewModelScope.launch { _uiEvent.emit(BackupFragmentEvent.ShowError("Failed to open file.")) }
            return
        }

        if (fileName == null || !fileName.endsWith(".json", ignoreCase = true)) {
            viewModelScope.launch { _uiEvent.emit(BackupFragmentEvent.ShowSnackbar("Selected file is not a .json backup file.")) }
            return
        }

        // Do not interrupt if exit suddenly
        runOnIoDispatcher {
            try {
                val contentString = inputStream.bufferedReader().use(BufferedReader::readText)
                val content = JSONObject(contentString)
                val version = content.optInt("version", 0)
                if (version < 1 || version > 1) {
                    _uiEvent.emit(BackupFragmentEvent.ShowSnackbar("Invalid or unsupported backup file version."))
                    return@runOnIoDispatcher
                }

                _uiEvent.emit(
                    BackupFragmentEvent.ShowImportDialog(
                        contentJson = contentString,
                        hasProfiles = content.has("profiles"),
                        hasRules = content.has("rules"),
                        hasSettings = content.has("settings"),
                    )
                )
            } catch (e: Exception) {
                Logs.w(e)
                _uiEvent.emit(BackupFragmentEvent.ShowError("Invalid backup file: ${e.readableMessage}"))
            }
        }
    }

    fun onImportConfirmed(contentJson: String, profile: Boolean, rule: Boolean, setting: Boolean) {
        viewModelScope.launch(Dispatchers.Default) {
            _isImporting.value = true
            try {
                SagerNet.stopService()
                val content = JSONObject(contentJson)
                finishImport(content, profile, rule, setting)
                _uiEvent.emit(BackupFragmentEvent.RestartApp)
            } catch (e: Exception) {
                Logs.w(e)
                _uiEvent.emit(BackupFragmentEvent.ShowError("Import: ${e.readableMessage}"))
            } finally {
                _isImporting.value = false
            }
        }
    }

    private suspend fun createBackup(profile: Boolean, rule: Boolean, setting: Boolean): String {
        val out = JSONObject().apply {
            put("version", 1)
            if (profile) {
                put("profiles", JSONArray().apply {
                    SagerDatabase.proxyDao.getAll().forEach {
                        put(it.toBase64Str())
                    }
                })
                put("groups", JSONArray().apply {
                    SagerDatabase.groupDao.allGroups().forEach {
                        put(it.toBase64Str())
                    }
                })
            }
            if (rule) {
                put("rules", JSONArray().apply {
                    SagerDatabase.rulesDao.allRules().forEach {
                        put(it.toBase64Str())
                    }
                })
            }
            if (setting) {
                put("settings", JSONArray().apply {
                    PublicDatabase.kvPairDao.all().forEach {
                        put(it.toBase64Str())
                    }
                })
            }
        }
        return out.toString(2)
    }

    private fun finishImport(
        content: JSONObject,
        profile: Boolean,
        rule: Boolean,
        setting: Boolean
    ) {
        if (profile && content.has("profiles")) {
            val profiles = mutableListOf<ProxyEntity>()
            val jsonProfiles = content.getJSONArray("profiles")
            for (i in 0 until jsonProfiles.length()) {
                val data = (jsonProfiles[i] as String).b64Decode()
                val parcel = Parcel.obtain()
                parcel.unmarshall(data, 0, data.size)
                parcel.setDataPosition(0)
                profiles.add(ProxyEntity.CREATOR.createFromParcel(parcel))
                parcel.recycle()
            }
            SagerDatabase.proxyDao.reset()
            SagerDatabase.proxyDao.insert(profiles)

            val groups = mutableListOf<ProxyGroup>()
            val jsonGroups = content.getJSONArray("groups")
            for (i in 0 until jsonGroups.length()) {
                val data = (jsonGroups[i] as String).b64Decode()
                val parcel = Parcel.obtain()
                parcel.unmarshall(data, 0, data.size)
                parcel.setDataPosition(0)
                groups.add(ProxyGroup.CREATOR.createFromParcel(parcel))
                parcel.recycle()
            }
            SagerDatabase.groupDao.reset()
            SagerDatabase.groupDao.insert(groups)
        }
        if (rule && content.has("rules")) {
            val rules = mutableListOf<RuleEntity>()
            val jsonRules = content.getJSONArray("rules")
            for (i in 0 until jsonRules.length()) {
                val data = (jsonRules[i] as String).b64Decode()
                val parcel = Parcel.obtain()
                parcel.unmarshall(data, 0, data.size)
                parcel.setDataPosition(0)
                rules.add(ParcelizeBridge.createRule(parcel))
                parcel.recycle()
            }
            SagerDatabase.rulesDao.reset()
            SagerDatabase.rulesDao.insert(rules)
        }
        if (setting && content.has("settings")) {
            val settings = mutableListOf<KeyValuePair>()
            val jsonSettings = content.getJSONArray("settings")
            for (i in 0 until jsonSettings.length()) {
                val data = (jsonSettings[i] as String).b64Decode()
                val parcel = Parcel.obtain()
                parcel.unmarshall(data, 0, data.size)
                parcel.setDataPosition(0)
                settings.add(KeyValuePair.CREATOR.createFromParcel(parcel))
                parcel.recycle()
            }
            PublicDatabase.kvPairDao.reset()
            PublicDatabase.kvPairDao.insert(settings)
        }
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