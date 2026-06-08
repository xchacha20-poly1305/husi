package fr.husi.ui

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.husi.bg.RouteAssetUpdater
import fr.husi.database.AssetEntity
import fr.husi.database.SagerDatabase
import fr.husi.ktx.blankAsNull
import fr.husi.ktx.runOnIoDispatcher
import fr.husi.repository.resolveRepository
import fr.husi.resources.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
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

    lateinit var editingName: String
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

    fun validate(text: String): StringResource? {
        if (text.length > 255 || text.contains('/')) {
            return Res.string.invalid_filename
        }
        if (
            resolveRepository().externalAssetsDir.resolve("geo").resolve(text)
                .canonicalPath.substringAfterLast('/') != text
        ) {
            return Res.string.invalid_filename
        }
        if (isNew && runBlocking { SagerDatabase.assetDao.get(text) } != null) {
            return Res.string.duplicate_name
        }
        if (!text.endsWith(".srs")) {
            return Res.string.expect_srs
        }
        if (text.startsWith("geosite-") || text.startsWith("geoip-")) {
            return Res.string.warn_starte_with_geo
        }
        return null
    }
}
