package io.nekohasekai.sagernet.ui

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.preference.PreferenceDataStore
import io.nekohasekai.sagernet.Key
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.SagerNet.Companion.app
import io.nekohasekai.sagernet.database.AssetEntity
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.SagerDatabase
import io.nekohasekai.sagernet.database.preference.OnPreferenceDataStoreChangeListener
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal sealed interface AssetEditEvents {
    class UpdateName(val name: String) : AssetEditEvents
}

internal class AssetEditActivityViewModel : ViewModel(), OnPreferenceDataStoreChangeListener {
    private val _dirty = MutableStateFlow(false)
    val dirty = _dirty.asStateFlow()

    private val _uiEvent = MutableSharedFlow<AssetEditEvents>()
    val uiEvent = _uiEvent.asSharedFlow()

    var editingAssetName = ""
    var shouldUpdateFromInternet = false

    fun loadAssetEntity(entity: AssetEntity) {
        DataStore.assetName = entity.name
        DataStore.assetUrl = entity.url
    }

    fun serializeAssetEntity(entity: AssetEntity) {
        entity.name = DataStore.assetName
        entity.url = DataStore.assetUrl
    }

    /** @return Error string res */
    @StringRes
    fun validate(): Int? {
        val assetName = DataStore.assetName
        if (assetName.length > 255 || assetName.contains('/')) {
            return R.string.invalid_filename
        }
        if (
            app.externalAssets.resolve("geo").resolve(assetName)
                .canonicalPath.substringAfterLast('/') != assetName
        ) {
            return R.string.invalid_filename
        }
        if (assetName != editingAssetName && SagerDatabase.assetDao.get(assetName) != null) {
            return R.string.duplicate_name
        }
        if (!assetName.endsWith(".srs")) {
            return R.string.expect_srs
        }
        if (assetName == ".srs") {
            return R.string.invalid_filename
        }
        if (assetName.startsWith("geoip-") || assetName.startsWith("geosite-")) {
            return R.string.warn_starte_with_geo
        }
        // Not check if duplicate with build-in srs so that user can override it.
        return null
    }

    suspend fun save() {
        if (editingAssetName.isEmpty()) {
            val entity = AssetEntity()
            serializeAssetEntity(entity)
            SagerDatabase.assetDao.create(entity)
        } else if (_dirty.value) {
            val entity = SagerDatabase.assetDao.get(editingAssetName)
            if (entity == null) {
                return
            }
            serializeAssetEntity(entity)
            SagerDatabase.assetDao.update(entity)
        }
    }

    override fun onPreferenceDataStoreChanged(
        store: PreferenceDataStore,
        key: String,
    ) {
        viewModelScope.launch {
            when (key) {
                Key.ASSET_URL -> {
                    shouldUpdateFromInternet = true
                    if (DataStore.assetName.isEmpty()) {
                        _uiEvent.emit(
                            AssetEditEvents.UpdateName(DataStore.assetUrl.substringAfterLast("/"))
                        )
                    }
                }
            }

            _dirty.emit(true)
        }
    }
}