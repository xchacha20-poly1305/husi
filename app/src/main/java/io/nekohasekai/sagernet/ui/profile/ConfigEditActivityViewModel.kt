package io.nekohasekai.sagernet.ui.profile

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.nekohasekai.sagernet.Key
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.ktx.Logs
import io.nekohasekai.sagernet.ktx.readableMessage
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import libcore.Libcore

internal sealed interface ConfigEditActivityUiEvent {
    class UpdateText(val text: String) : ConfigEditActivityUiEvent
    class Alert(val message: String) : ConfigEditActivityUiEvent
    class SnackBar(@param:StringRes val id: Int) : ConfigEditActivityUiEvent
    object Finish : ConfigEditActivityUiEvent
}

internal class ConfigEditActivityViewModel : ViewModel() {
    private val _uiEvent = MutableSharedFlow<ConfigEditActivityUiEvent>()
    val uiEvent = _uiEvent.asSharedFlow()

    var key = Key.SERVER_CONFIG
    var content = ""
    var needSave = false

    suspend fun saveAndExit(text: String) {
        val formated = formatJson(text) ?: return
        DataStore.profileCacheStore.putString(key, formated)
        _uiEvent.emit(ConfigEditActivityUiEvent.Finish)
    }

    /**
     * @return Non-null if success.
     */
    suspend fun formatJson(origin: CharSequence?): String? {
        if (origin.isNullOrBlank()) {
            val formatted = "" // Not null
            _uiEvent.emit(ConfigEditActivityUiEvent.UpdateText(formatted))
            return formatted
        }

        return try {
            val formated = Libcore.formatConfig(origin.toString()).value
            _uiEvent.emit(ConfigEditActivityUiEvent.UpdateText(formated))
            formated
        } catch (e: Exception) {
            Logs.w(e)
            _uiEvent.emit(ConfigEditActivityUiEvent.Alert(e.readableMessage))
            null
        }
    }

    suspend fun checkConfig(text: String) {
        try {
            val jsonContent = if (text.contains("outbound")) {
                // complete config
                text
            } else {
                // turn single outbound to complete config
                val singleOutbound = JsonParser.parseString(text)
                val jsonArray = JsonArray().also { it.add(singleOutbound) }
                JsonObject().also { it.add("outbounds", jsonArray) }.toString()
            }
            Libcore.checkConfig(jsonContent)
        } catch (e: Exception) {
            Logs.i("failed to check config", e)
            _uiEvent.emit(ConfigEditActivityUiEvent.Alert(e.readableMessage))
            return
        }
        _uiEvent.emit(ConfigEditActivityUiEvent.SnackBar(android.R.string.ok))
    }
}