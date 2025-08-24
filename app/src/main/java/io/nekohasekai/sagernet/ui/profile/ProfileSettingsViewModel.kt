@file:Suppress("UNCHECKED_CAST")

package io.nekohasekai.sagernet.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.preference.PreferenceDataStore
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.ProfileManager
import io.nekohasekai.sagernet.database.ProxyEntity
import io.nekohasekai.sagernet.database.SagerDatabase
import io.nekohasekai.sagernet.database.preference.OnPreferenceDataStoreChangeListener
import io.nekohasekai.sagernet.fmt.AbstractBean
import io.nekohasekai.sagernet.ktx.applyDefaultValues
import io.nekohasekai.sagernet.ktx.onIoDispatcher
import io.nekohasekai.sagernet.ui.StringOrRes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

internal sealed interface ProfileSettingsUiEvent {
    object EnableBackPressCallback : ProfileSettingsUiEvent
    data class Alert(val title: StringOrRes, val message: StringOrRes) : ProfileSettingsUiEvent
}

internal abstract class ProfileSettingsViewModel<T : AbstractBean> : ViewModel(),
    OnPreferenceDataStoreChangeListener {
    protected val _uiEvent = MutableSharedFlow<ProfileSettingsUiEvent>()
    val uiEvent = _uiEvent.asSharedFlow()

    abstract fun createBean(): T
    abstract fun T.writeToTempDatabase()
    abstract fun T.loadFromTempDatabase()

    lateinit var proxyEntity: ProxyEntity
    lateinit var bean: T
    var isSubscription = false

    suspend fun initialize(editingId: Long, isSubscription: Boolean) {
        this.isSubscription = isSubscription
        DataStore.editingId = editingId

        bean = if (editingId == 0L) {
            DataStore.editingGroup = DataStore.selectedGroupForImport()
            createBean().applyDefaultValues()
        } else {
            proxyEntity = SagerDatabase.proxyDao.getById(editingId)!!
            DataStore.editingGroup = proxyEntity.groupId
            (proxyEntity.requireBean() as T)
        }
        bean.writeToTempDatabase()

        DataStore.profileCacheStore.registerChangeListener(this)
    }

    override fun onCleared() {
        DataStore.profileCacheStore.unregisterChangeListener(this)
        super.onCleared()
    }

    suspend fun delete() {
        ProfileManager.deleteProfile(DataStore.editingId, DataStore.editingGroup)
    }

    suspend fun saveEntity() = onIoDispatcher {
        val entity = if (DataStore.editingId == 0L) {
            val editingGroup = DataStore.editingGroup
            val bean = createBean()
            ProfileManager.createProfile(editingGroup, bean)
        } else {
            proxyEntity
        }
        bean.loadFromTempDatabase()
        entity.setBean(bean)
        ProfileManager.updateProfile(entity)
    }

    enum class CustomConfigType {
        Outbound,
        Fall,
    }

    private var customConfigType: CustomConfigType? = null

    fun prepareForEditCustomConfig(type: CustomConfigType) {
        customConfigType = type
        when (type) {
            CustomConfigType.Outbound -> DataStore.serverCustomOutbound = bean.customOutboundJson
            CustomConfigType.Fall -> DataStore.serverCustom = bean.customConfigJson
        }
    }

    fun onEditedCustomConfig(ok: Boolean) = viewModelScope.launch(Dispatchers.IO) {
        val type = requireNotNull(customConfigType)
        customConfigType = null
        if (!ok) return@launch

        when (type) {
            CustomConfigType.Outbound -> bean.customOutboundJson = DataStore.serverCustomOutbound
            CustomConfigType.Fall -> bean.customConfigJson = DataStore.serverCustom
        }

    }

    var dirty = false
        set(value) {
            if (!field) viewModelScope.launch {
                _uiEvent.emit(ProfileSettingsUiEvent.EnableBackPressCallback)
            }
            field = value
        }

    override fun onPreferenceDataStoreChanged(store: PreferenceDataStore, key: String) {
        dirty = true
    }
}