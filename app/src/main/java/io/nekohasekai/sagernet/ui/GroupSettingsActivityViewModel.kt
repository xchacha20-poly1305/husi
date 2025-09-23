package io.nekohasekai.sagernet.ui

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.preference.PreferenceDataStore
import io.nekohasekai.sagernet.GroupType
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.GroupManager
import io.nekohasekai.sagernet.database.ProxyGroup
import io.nekohasekai.sagernet.database.SagerDatabase
import io.nekohasekai.sagernet.database.SubscriptionBean
import io.nekohasekai.sagernet.database.preference.OnPreferenceDataStoreChangeListener
import io.nekohasekai.sagernet.ktx.applyDefaultValues
import io.nekohasekai.sagernet.ktx.runOnIoDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal class GroupSettingsActivityViewModel : ViewModel(),
OnPreferenceDataStoreChangeListener{

    companion object {
        const val OUTBOUND_POSITION = 1
    }

    suspend fun ProxyGroup.writeToTmpDataStore() {
        DataStore.groupName = name ?: ""
        DataStore.groupType = type
        DataStore.groupOrder = order

        DataStore.frontProxy = frontProxy
        DataStore.landingProxy = landingProxy
        DataStore.frontProxyTmp = if (frontProxy >= 0) OUTBOUND_POSITION else 0
        DataStore.landingProxyTmp = if (landingProxy >= 0) OUTBOUND_POSITION else 0

        val subscription = subscription ?: SubscriptionBean().applyDefaultValues()
        DataStore.subscriptionType = subscription.type
        DataStore.subscriptionToken = subscription.token
        DataStore.subscriptionLink = subscription.link
        DataStore.subscriptionForceResolve = subscription.forceResolve
        DataStore.subscriptionDeduplication = subscription.deduplication
        DataStore.subscriptionFilterRegex = subscription.filterNotRegex
        DataStore.subscriptionUpdateWhenConnectedOnly = subscription.updateWhenConnectedOnly
        DataStore.subscriptionUserAgent = subscription.customUserAgent
        DataStore.subscriptionAutoUpdate = subscription.autoUpdate
        DataStore.subscriptionAutoUpdateDelay = subscription.autoUpdateDelay
    }

    fun ProxyGroup.loadFromTmpDataStore() {
        name = DataStore.groupName.takeIf { it.isNotBlank() } ?: "My group"
        type = DataStore.groupType
        order = DataStore.groupOrder

        frontProxy = if (DataStore.frontProxyTmp == OUTBOUND_POSITION) DataStore.frontProxy else -1
        landingProxy =
            if (DataStore.landingProxyTmp == OUTBOUND_POSITION) DataStore.landingProxy else -1

        val isSubscription = type == GroupType.SUBSCRIPTION
        if (isSubscription) {
            subscription = (subscription ?: SubscriptionBean().applyDefaultValues()).apply {
                type = DataStore.subscriptionType
                token = DataStore.subscriptionToken
                link = DataStore.subscriptionLink
                forceResolve = DataStore.subscriptionForceResolve
                deduplication = DataStore.subscriptionDeduplication
                filterNotRegex = DataStore.subscriptionFilterRegex
                updateWhenConnectedOnly = DataStore.subscriptionUpdateWhenConnectedOnly
                customUserAgent = DataStore.subscriptionUserAgent
                autoUpdate = DataStore.subscriptionAutoUpdate
                autoUpdateDelay = DataStore.subscriptionAutoUpdateDelay
            }
        }
    }

    private var editingID: Long = 0L
    val isNew get() = editingID == 0L

    private val _dirty = MutableStateFlow(false)
    val dirty = _dirty.asStateFlow()

    fun initialize(id: Long) = viewModelScope.launch {
        editingID = id
        DataStore.editingId = id
        if (isNew) {
            ProxyGroup().writeToTmpDataStore()
        } else {
            SagerDatabase.groupDao.getById(id)!!.writeToTmpDataStore()
        }
    }

    fun delete() = runOnIoDispatcher {
        GroupManager.deleteGroup(editingID)
    }

    fun save() = runOnIoDispatcher {
        if (isNew) {
            GroupManager.createGroup(ProxyGroup().apply { loadFromTmpDataStore() })
            return@runOnIoDispatcher
        }
        if (!_dirty.value) return@runOnIoDispatcher
        val entity = SagerDatabase.groupDao.getById(editingID) ?: return@runOnIoDispatcher
        val keepUserInfo = entity.type == GroupType.SUBSCRIPTION
                && DataStore.groupType == GroupType.SUBSCRIPTION
                && entity.subscription?.link == DataStore.subscriptionLink
        if (!keepUserInfo) entity.subscription?.apply {
            bytesUsed = -1L
            bytesRemaining = -1L
            expiryDate = -1L
        }
        entity.loadFromTmpDataStore()
        GroupManager.updateGroup(entity)
    }

    override fun onPreferenceDataStoreChanged(
        store: PreferenceDataStore,
        key: String,
    ) {
        _dirty.update { true }
    }
}