package io.nekohasekai.sagernet.ui

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.database.AssetEntity
import io.nekohasekai.sagernet.database.SagerDatabase
import io.nekohasekai.sagernet.ktx.blankAsNull
import io.nekohasekai.sagernet.ktx.runOnIoDispatcher
import io.nekohasekai.sagernet.repository.repo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Immutable
internal data class AssetEditActivityUiState(
    val name: String = "",
    val link: String = "",
)

@Stable
internal class AssetEditActivityViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(AssetEditActivityUiState())
    val uiState = _uiState.asStateFlow()

    private val initialState = MutableStateFlow<AssetEditActivityUiState?>(null)
    val isDirty = uiState.map { currentState ->
        initialState.value?.let {
            it == currentState
        } ?: false
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = false,
    )

    lateinit var editingName: String
    var isNew = false

    fun initialize(name: String) {
        val asset = SagerDatabase.assetDao.get(name) ?: AssetEntity().also {
            isNew = true
        }
        editingName = name
        _uiState.update { state->
            state.copy(
                name = asset.name,
                link = asset.url,
            ).also {
                initialState.value = it
            }
        }
    }

    var shouldUpdateFromInternet = false

    fun save() = runOnIoDispatcher {
        if (isNew) {
            val entity = AssetEntity()
            entity.loadFromUiState(_uiState.value)
            SagerDatabase.assetDao.create(entity)
        } else if (isDirty.value) {
            val entity = SagerDatabase.assetDao.get(editingName) ?: return@runOnIoDispatcher
            entity.loadFromUiState(_uiState.value)
            SagerDatabase.assetDao.update(entity)
        }
    }

    private fun AssetEntity.loadFromUiState(state: AssetEditActivityUiState) {
        name = state.name
        url = state.link
    }

    fun setName(name: String) = viewModelScope.launch {
        _uiState.update {
            it.copy(name = name)
        }
    }

    fun setLink(link: String) = viewModelScope.launch {
        _uiState.update {
            val name = it.name.blankAsNull() ?: link.substringAfterLast("/")
            it.copy(
                name = name,
                link = link,
            )
        }
        shouldUpdateFromInternet = true
    }

    @StringRes
    fun validate(text: String): Int? {
        if (text.length > 255 || text.contains('/')) {
            return R.string.invalid_filename
        }
        if (
            repo.externalAssetsDir.resolve("geo").resolve(text)
                .canonicalPath.substringAfterLast('/') != text
        ) {
            return R.string.invalid_filename
        }
        if (isNew && SagerDatabase.assetDao.get(text) != null) {
            return R.string.duplicate_name
        }
        if (!text.endsWith(".srs")) {
            return R.string.expect_srs
        }
        if (text.startsWith("geosite-") || text.startsWith("geoip-")) {
            return R.string.warn_starte_with_geo
        }
        return null
    }
}