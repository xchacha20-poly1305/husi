package fr.husi.ui

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.husi.bg.RouteAssetUpdater
import fr.husi.bg.routeGeoDir
import fr.husi.database.AssetEntity
import fr.husi.database.SagerDatabase
import fr.husi.fmt.SingBoxOptions
import fr.husi.ktx.blankAsNull
import fr.husi.ktx.runOnIoDispatcher
import fr.husi.platform.PathLimits
import fr.husi.repository.resolveRepository
import fr.husi.resources.Res
import fr.husi.resources.duplicate_name
import fr.husi.resources.expect_srs
import fr.husi.resources.filename_too_long_bytes
import fr.husi.resources.filename_too_long_characters
import fr.husi.resources.invalid_filename
import fr.husi.resources.path_too_long_bytes
import fr.husi.resources.path_too_long_characters
import fr.husi.resources.warn_starte_with_geo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource

@Immutable
internal data class AssetEditUiState(
    val name: String = "",
    val link: String = "",
    val autoUpdateDelay: Int = 0,
)

@Stable
internal class AssetEditViewModel(
    assetName: String,
) : ViewModel() {

    val uiState: StateFlow<AssetEditUiState>
        field = MutableStateFlow(AssetEditUiState())

    private val initialState = MutableStateFlow<AssetEditUiState?>(null)
    val isDirty = combine(uiState, initialState) { currentState, initialState ->
        initialState?.let {
            it != currentState
        } ?: false
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = false,
    )

    var editingName: String = ""
    var isNew = false

    init {
        viewModelScope.launch {
            initialize(assetName)
        }
    }

    suspend fun initialize(name: String) {
        isNew = false
        shouldUpdateFromInternet = false
        initialState.value = null
        val asset = SagerDatabase.assetDao.get(name) ?: AssetEntity().also {
            isNew = true
        }
        editingName = name
        uiState.update { state ->
            state.copy(
                name = asset.name,
                link = asset.url,
                autoUpdateDelay = asset.autoUpdateDelay,
            ).also {
                initialState.value = it
            }
        }
    }

    var shouldUpdateFromInternet = false

    fun save() = runOnIoDispatcher {
        if (isNew) {
            val entity = AssetEntity()
            entity.loadFromUiState(uiState.value)
            SagerDatabase.assetDao.create(entity)
        } else if (isDirty.value) {
            val entity = SagerDatabase.assetDao.get(editingName) ?: return@runOnIoDispatcher
            entity.loadFromUiState(uiState.value)
            SagerDatabase.assetDao.update(entity)
        }
        RouteAssetUpdater.reconfigureUpdater()
    }

    private fun AssetEntity.loadFromUiState(state: AssetEditUiState) {
        name = state.name
        url = state.link
        autoUpdateDelay = state.autoUpdateDelay
    }

    fun setName(name: String) = viewModelScope.launch {
        uiState.update {
            it.copy(name = name)
        }
    }

    fun setLink(link: String) = viewModelScope.launch {
        uiState.update {
            val name = it.name.blankAsNull() ?: link.substringAfterLast("/")
            it.copy(
                name = name,
                link = link,
            )
        }
        shouldUpdateFromInternet = true
    }

    fun setAutoUpdateDelay(autoUpdateDelay: Int) = viewModelScope.launch {
        uiState.update {
            it.copy(autoUpdateDelay = autoUpdateDelay)
        }
    }

    suspend fun validate(text: String): StringOrRes? {
        val limits = PathLimits.current
        if (!limits.acceptsName(text)) {
            return limits.tooLongMessage(
                inBytes = Res.string.filename_too_long_bytes,
                inCharacters = Res.string.filename_too_long_characters,
                limit = limits.maxNameLength,
                text = text,
            )
        }
        val file = routeGeoDir(resolveRepository().externalAssetsDir).resolve(text)
        if (file.canonicalFile.name != text) {
            return StringOrRes.Res(Res.string.invalid_filename)
        }
        if (!limits.acceptsPath(file.absolutePath)) {
            return limits.tooLongMessage(
                inBytes = Res.string.path_too_long_bytes,
                inCharacters = Res.string.path_too_long_characters,
                limit = limits.maxPathLength,
                text = file.absolutePath,
            )
        }
        if (text != editingName && SagerDatabase.assetDao.get(text) != null) {
            return StringOrRes.Res(Res.string.duplicate_name)
        }
        if (!text.endsWith(SingBoxOptions.RULE_SET_FILE_SUFFIX)) {
            return StringOrRes.Res(Res.string.expect_srs)
        }
        if (text.startsWith("geosite-") || text.startsWith("geoip-")) {
            return StringOrRes.Res(Res.string.warn_starte_with_geo)
        }
        return null
    }

    fun PathLimits.tooLongMessage(
        inBytes: StringResource,
        inCharacters: StringResource,
        limit: Int,
        text: String,
    ): StringOrRes = StringOrRes.ResWithParams(
        if (countsUtf8Bytes) {
            inBytes
        } else {
            inCharacters
        },
        limit,
        lengthOf(text),
    )

}
