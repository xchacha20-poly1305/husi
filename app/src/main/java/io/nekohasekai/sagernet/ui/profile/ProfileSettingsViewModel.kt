@file:Suppress("UNCHECKED_CAST")

package io.nekohasekai.sagernet.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.nekohasekai.sagernet.GroupType
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.GroupManager
import io.nekohasekai.sagernet.database.ProfileManager
import io.nekohasekai.sagernet.database.ProxyEntity
import io.nekohasekai.sagernet.database.ProxyGroup
import io.nekohasekai.sagernet.database.SagerDatabase
import io.nekohasekai.sagernet.fmt.AbstractBean
import io.nekohasekai.sagernet.fmt.SingBoxOptions
import io.nekohasekai.sagernet.ktx.applyDefaultValues
import io.nekohasekai.sagernet.ktx.onIoDispatcher
import io.nekohasekai.sagernet.ktx.runOnIoDispatcher
import io.nekohasekai.sagernet.ui.StringOrRes
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

internal sealed interface ProfileSettingsUiEvent {
    data class Alert(val title: StringOrRes, val message: StringOrRes) : ProfileSettingsUiEvent
}

internal sealed interface ProfileSettingsUiState {
    val customConfig: String
    val customOutbound: String
}

internal abstract class ProfileSettingsViewModel<T : AbstractBean> : ViewModel() {

    abstract val uiState: StateFlow<ProfileSettingsUiState>

    private val _uiEvent = MutableSharedFlow<ProfileSettingsUiEvent>()
    val uiEvent = _uiEvent.asSharedFlow()

    protected suspend fun emitAlert(title: StringOrRes, message: StringOrRes) {
        _uiEvent.emit(ProfileSettingsUiEvent.Alert(title, message))
    }

    private lateinit var initialState: ProfileSettingsUiState
    val isDirty by lazy(LazyThreadSafetyMode.NONE) {
        uiState.map { currentState ->
            initialState != currentState
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false,
        )
    }

    protected abstract fun createBean(): T
    protected abstract fun T.writeToUiState()
    protected abstract fun T.loadFromUiState()

    protected var editingId = -1L
    val isNew get() = editingId < 0L
    lateinit var proxyEntity: ProxyEntity
    lateinit var bean: T
    var isSubscription = false

    fun initialize(editingId: Long, isSubscription: Boolean) {
        this.editingId = editingId
        this.isSubscription = isSubscription

        bean = if (isNew) {
            createBean().applyDefaultValues()
        } else {
            proxyEntity = SagerDatabase.proxyDao.getById(editingId)!!
            (proxyEntity.requireBean() as T)
        }
        bean.writeToUiState()
        initialState = uiState.value
    }

    fun delete() = runOnIoDispatcher {
        ProfileManager.deleteProfile(proxyEntity.groupId, editingId)
    }

    fun save() = runOnIoDispatcher {
        if (isNew) {
            val editingGroup = DataStore.selectedGroupForImport()
            DataStore.selectedGroup = editingGroup
            val bean = createBean()
            bean.loadFromUiState()
            ProfileManager.createProfile(editingGroup, bean)
            return@runOnIoDispatcher
        }
        bean.loadFromUiState()
        proxyEntity.putBean(bean)
        ProfileManager.updateProfile(proxyEntity)
    }

    suspend fun groupsForMove(): List<ProxyGroup> = onIoDispatcher {
        SagerDatabase.groupDao.allGroups()
            .first()
            .filter {
                it.type == GroupType.BASIC && it.id != proxyEntity.groupId
            }
    }

    fun move(to: Long) = runOnIoDispatcher {
        val from = proxyEntity.groupId
        proxyEntity.groupId = to
        ProfileManager.updateProfile(proxyEntity)
        GroupManager.postUpdate(from)
        GroupManager.postUpdate(to)
        DataStore.selectedGroup = to
    }

    abstract fun setCustomConfig(config: String)
    abstract fun setCustomOutbound(outbound: String)

}

internal val fingerprints
    get() = listOf(
        "",
        SingBoxOptions.FINGERPRINT_CHROME,
        SingBoxOptions.FINGERPRINT_FIREFOX,
        SingBoxOptions.FINGERPRINT_EDGE,
        SingBoxOptions.FINGERPRINT_SAFARI,
        SingBoxOptions.FINGERPRINT_360,
        SingBoxOptions.FINGERPRINT_QQ,
        SingBoxOptions.FINGERPRINT_IOS,
        SingBoxOptions.FINGERPRINT_ANDROID,
        SingBoxOptions.FINGERPRINT_RANDOM,
        SingBoxOptions.FINGERPRINT_RANDOMIZED,
    )

internal val congestionControls
    get() = listOf(
        "bbr",
        "cubic",
        "new_reno",
    )

internal val muxTypes = listOf("h2mux", "smux", "yamux")

internal val muxStrategies = listOf(
    R.string.mux_max_connections,
    R.string.mux_min_streams,
    R.string.mux_max_streams,
)
