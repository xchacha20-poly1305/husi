package fr.husi.ui.profile

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.husi.ktx.blankAsNull
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

internal const val SIP003_OBFS_LOCAL = "obfs-local"
internal const val SIP003_V2RAY_PLUGIN = "v2ray-plugin"

internal object SIP003Keys {
    const val OBFS = "obfs"
    const val OBFS_HOST = "obfs-host"
    const val TLS = "tls"
    const val MODE = "mode"
    const val HOST = "host"
    const val PATH = "path"
    const val MUX = "mux"
    const val CERT_RAW = "certRaw"
}

internal enum class ObfsMode(val value: String) {
    Http("http"),
    Tls("tls"),
    ;

    companion object {
        val Default get() = Http
        fun parse(name: String?): ObfsMode = entries.firstOrNull {
            it.value == name
        } ?: Default
    }
}

internal enum class V2RayMode(val value: String) {
    Websocket("websocket"),
    Quic("quic"),
    ;

    companion object {
        val Default get() = Websocket
        fun parse(name: String?): V2RayMode = entries.firstOrNull {
            it.value == name
        } ?: Default
    }
}

internal const val DEFAULT_V2RAY_MUX = 1

@Immutable
internal data class SIP003EditorUiState(
    val obfs: ObfsMode = ObfsMode.Default,
    val obfsHost: String = "",
    val tls: Boolean = false,
    val mode: V2RayMode = V2RayMode.Default,
    val host: String = "",
    val path: String = "",
    val mux: Int = DEFAULT_V2RAY_MUX,
    val certRaw: String = "",
)

@Stable
internal class SIP003EditorViewModel(
    val pluginName: String,
    private val initialOpts: String,
) : ViewModel() {

    private val initialState = parseInitial(initialOpts)

    val uiState: StateFlow<SIP003EditorUiState>
        field = MutableStateFlow(initialState)

    val isDirty: StateFlow<Boolean> = uiState
        .map { it != initialState }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false,
        )

    fun serialize(): String {
        val state = uiState.value
        return when (pluginName) {
            SIP003_OBFS_LOCAL -> serializeSIP003(
                listOf(
                    SIP003Keys.OBFS to state.obfs.value,
                    SIP003Keys.OBFS_HOST to state.obfsHost,
                ),
            )

            SIP003_V2RAY_PLUGIN -> serializeSIP003(
                listOf(
                    SIP003Keys.TLS to if (state.tls) "1" else null,
                    SIP003Keys.MODE to state.mode.value,
                    SIP003Keys.HOST to state.host,
                    SIP003Keys.PATH to state.path,
                    SIP003Keys.MUX to state.mux.toString(),
                    SIP003Keys.CERT_RAW to state.certRaw,
                ),
            )

            else -> initialOpts
        }
    }

    private fun parseInitial(opts: String): SIP003EditorUiState {
        val map = parseSIP003(opts)
        val default = SIP003EditorUiState()
        return SIP003EditorUiState(
            obfs = ObfsMode.parse(map[SIP003Keys.OBFS]),
            obfsHost = map[SIP003Keys.OBFS_HOST].orEmpty(),
            tls = !map[SIP003Keys.TLS].isNullOrEmpty(),
            mode = V2RayMode.parse(map[SIP003Keys.MODE]),
            host = map[SIP003Keys.HOST].orEmpty(),
            path = map[SIP003Keys.PATH].orEmpty(),
            mux = map[SIP003Keys.MUX]?.toIntOrNull() ?: default.mux,
            certRaw = map[SIP003Keys.CERT_RAW].orEmpty(),
        )
    }

    fun setObfs(value: ObfsMode) = uiState.update { it.copy(obfs = value) }
    fun setObfsHost(value: String) = uiState.update { it.copy(obfsHost = value) }
    fun setTls(value: Boolean) = uiState.update { it.copy(tls = value) }
    fun setMode(value: V2RayMode) = uiState.update { it.copy(mode = value) }
    fun setHost(value: String) = uiState.update { it.copy(host = value) }
    fun setPath(value: String) = uiState.update { it.copy(path = value) }
    fun setMux(value: Int) = uiState.update { it.copy(mux = value) }
    fun setCertRaw(value: String) = uiState.update { it.copy(certRaw = value) }
}

/**
 * Mirrors sing-box `transport/sip003.ParsePluginOptions`:
 * `;` separates entries, `=` separates key/value, `\` escapes the next character,
 * a key without `=` is treated as a flag with value `"1"`.
 */
internal fun parseSIP003(opts: String): Map<String, String> {
    if (opts.isEmpty()) return emptyMap()
    val result = mutableMapOf<String, String>()
    val key = StringBuilder()
    val value = StringBuilder()
    var isKey = true
    var i = 0
    while (i < opts.length) {
        val char = opts[i]
        when (char) {
            '\\' -> {
                if (i + 1 < opts.length) {
                    (if (isKey) key else value).append(opts[i + 1])
                    i += 2
                    continue
                }
            }

            '=' -> if (isKey) {
                isKey = false
                i++
                continue
            }

            ';' -> {
                if (key.isNotEmpty()) {
                    result[key.toString()] = if (isKey) "1" else value.toString()
                }
                key.clear()
                value.clear()
                isKey = true
                i++
                continue
            }
        }
        if (isKey) {
            key.append(char)
        } else {
            value.append(char)
        }
        i++
    }
    if (key.isNotEmpty()) {
        result[key.toString()] = if (isKey) {
            "1"
        } else {
            value.toString()
        }
    }
    return result
}

private fun escapeSIP003(text: String): String {
    val builder = StringBuilder(text.length)
    text.forEach { char ->
        when (char) {
            '\\', '=', ';' -> builder.append('\\')
        }
        builder.append(char)
    }
    return builder.toString()
}

internal fun serializeSIP003(entries: List<Pair<String, String?>>): String {
    return entries
        .mapNotNull { (key, value) ->
            value.blankAsNull()?.let {
                "${escapeSIP003(key)}=${escapeSIP003(value!!)}"
            }
        }
        .joinToString(";")
}
