package io.nekohasekai.sagernet.ui

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.nekohasekai.sagernet.GroupOrder
import io.nekohasekai.sagernet.GroupType
import io.nekohasekai.sagernet.SubscriptionType
import io.nekohasekai.sagernet.database.GroupManager
import io.nekohasekai.sagernet.database.ProxyGroup
import io.nekohasekai.sagernet.database.SagerDatabase
import io.nekohasekai.sagernet.database.SubscriptionBean
import io.nekohasekai.sagernet.ktx.applyDefaultValues
import io.nekohasekai.sagernet.ktx.blankAsNull
import io.nekohasekai.sagernet.ktx.runOnIoDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Immutable
internal data class GroupSettingsUiState(
    val name: String = "",
    val type: Int = GroupType.BASIC,
    val order: Int = GroupOrder.ORIGIN,
    val frontProxy: Long = -1,
    val landingProxy: Long = -1,

    val subscriptionType: Int = SubscriptionType.RAW,
    val subscriptionToken: String = "",
    val subscriptionLink: String = "",
    val subscriptionForceResolve: Boolean = false,
    val subscriptionDeduplication: Boolean = false,
    val subscriptionFilterNotRegex: String = "",
    val subscriptionUpdateWhenConnectedOnly: Boolean = false,
    val subscriptionUserAgent: String = "",
    val subscriptionAutoUpdate: Boolean = false,
    val subscriptionUpdateDelay: Int = 1440,
)

@Stable
internal class GroupSettingsActivityViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(GroupSettingsUiState())
    val uiState = _uiState.asStateFlow()

    private var editingID: Long = 0L
    val isNew get() = editingID == 0L

    private lateinit var initialState: GroupSettingsUiState

    val isDirty = uiState.map { currentState ->
        initialState != currentState
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = false,
    )

    fun initialize(id: Long) = viewModelScope.launch {
        editingID = id
        val group = if (isNew) {
            ProxyGroup()
        } else {
            SagerDatabase.groupDao.getById(id).first()!!
        }
        _uiState.update {
            val subscription = group.subscription ?: SubscriptionBean().applyDefaultValues()
            it.copy(
                name = group.name ?: "",
                type = group.type,
                order = group.order,
                frontProxy = group.frontProxy,
                landingProxy = group.landingProxy,

                subscriptionType = subscription.type,
                subscriptionToken = subscription.token,
                subscriptionLink = subscription.link,
                subscriptionForceResolve = subscription.forceResolve,
                subscriptionDeduplication = subscription.deduplication,
                subscriptionFilterNotRegex = subscription.filterNotRegex,
                subscriptionUpdateWhenConnectedOnly = subscription.updateWhenConnectedOnly,
                subscriptionUserAgent = subscription.customUserAgent,
                subscriptionAutoUpdate = subscription.autoUpdate,
                subscriptionUpdateDelay = subscription.autoUpdateDelay,
            ).also {
                initialState = it
            }
        }
    }

    fun delete() = runOnIoDispatcher {
        GroupManager.deleteGroup(editingID)
    }

    fun save() = runOnIoDispatcher {
        if (isNew) {
            GroupManager.createGroup(ProxyGroup().apply { loadFromUiState(uiState.value) })
            return@runOnIoDispatcher
        }
        if (!isDirty.value) return@runOnIoDispatcher
        val entity = SagerDatabase.groupDao.getById(editingID).firstOrNull() ?: return@runOnIoDispatcher
        val state = _uiState.value
        val keepUserInfo = entity.type == GroupType.SUBSCRIPTION
                && initialState.type == GroupType.SUBSCRIPTION
                && entity.subscription?.link == state.subscriptionLink
        if (!keepUserInfo) entity.subscription?.apply {
            bytesUsed = -1L
            bytesRemaining = -1L
            expiryDate = -1L
        }
        entity.loadFromUiState(state)
        GroupManager.updateGroup(entity)
    }

    private fun ProxyGroup.loadFromUiState(state: GroupSettingsUiState) {
        name = state.name.blankAsNull() ?: "My Group"
        type = state.type
        order = state.order
        frontProxy = state.frontProxy
        landingProxy = state.landingProxy

        if (type == GroupType.SUBSCRIPTION) {
            subscription = (subscription ?: SubscriptionBean().applyDefaultValues()).apply {
                type = state.subscriptionType
                token = state.subscriptionToken
                link = state.subscriptionLink
                forceResolve = state.subscriptionForceResolve
                deduplication = state.subscriptionDeduplication
                filterNotRegex = state.subscriptionFilterNotRegex
                updateWhenConnectedOnly = state.subscriptionUpdateWhenConnectedOnly
                customUserAgent = state.subscriptionUserAgent
                autoUpdate = state.subscriptionAutoUpdate
                autoUpdateDelay = state.subscriptionUpdateDelay
            }
        }
    }

    fun setName(name: String) = viewModelScope.launch {
        _uiState.update {
            it.copy(name = name)
        }
    }

    fun setType(type: Int) = viewModelScope.launch {
        _uiState.update {
            it.copy(type = type)
        }
    }

    fun setOrder(order: Int) = viewModelScope.launch {
        _uiState.update {
            it.copy(order = order)
        }
    }

    fun setFrontProxy(frontProxy: Long) = viewModelScope.launch {
        _uiState.update {
            it.copy(frontProxy = frontProxy)
        }
    }

    fun setLandingProxy(landingProxy: Long) = viewModelScope.launch {
        _uiState.update {
            it.copy(landingProxy = landingProxy)
        }
    }

    fun setSubscriptionType(subscriptionType: Int) = viewModelScope.launch {
        _uiState.update {
            it.copy(subscriptionType = subscriptionType)
        }
    }

    fun setSubscriptionToken(subscriptionToken: String) = viewModelScope.launch {
        _uiState.update {
            it.copy(subscriptionToken = subscriptionToken)
        }
    }

    fun setSubscriptionLink(subscriptionLink: String) = viewModelScope.launch {
        _uiState.update {
            it.copy(subscriptionLink = subscriptionLink)
        }
    }

    fun setSubscriptionForceResolve(subscriptionForceResolve: Boolean) = viewModelScope.launch {
        _uiState.update {
            it.copy(subscriptionForceResolve = subscriptionForceResolve)
        }
    }

    fun setSubscriptionDeduplication(subscriptionDeduplication: Boolean) = viewModelScope.launch {
        _uiState.update {
            it.copy(subscriptionDeduplication = subscriptionDeduplication)
        }
    }

    fun setSubscriptionFilterNotRegex(subscriptionFilterNotRegex: String) = viewModelScope.launch {
        _uiState.update {
            it.copy(subscriptionFilterNotRegex = subscriptionFilterNotRegex)
        }
    }

    fun setSubscriptionUpdateWhenConnectedOnly(subscriptionUpdateWhenConnectedOnly: Boolean) =
        viewModelScope.launch {
            _uiState.update {
                it.copy(subscriptionUpdateWhenConnectedOnly = subscriptionUpdateWhenConnectedOnly)
            }
        }

    fun setSubscriptionUserAgent(subscriptionUserAgent: String) = viewModelScope.launch {
        _uiState.update {
            it.copy(subscriptionUserAgent = subscriptionUserAgent)
        }
    }

    fun setSubscriptionAutoUpdate(subscriptionAutoUpdate: Boolean) = viewModelScope.launch {
        _uiState.update {
            it.copy(subscriptionAutoUpdate = subscriptionAutoUpdate)
        }
    }

    fun setSubscriptionUpdateDelay(subscriptionUpdateDelay: Int) = viewModelScope.launch {
        _uiState.update {
            it.copy(subscriptionUpdateDelay = subscriptionUpdateDelay)
        }
    }

}
