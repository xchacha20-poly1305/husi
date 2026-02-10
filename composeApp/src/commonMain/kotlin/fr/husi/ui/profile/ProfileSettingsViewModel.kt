@file:Suppress("UNCHECKED_CAST")

package fr.husi.ui.profile

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.husi.GroupType
import fr.husi.database.DataStore
import fr.husi.database.ProfileManager
import fr.husi.database.ProxyEntity
import fr.husi.database.ProxyGroup
import fr.husi.database.SagerDatabase
import fr.husi.fmt.AbstractBean
import fr.husi.fmt.SingBoxOptions
import fr.husi.ktx.applyDefaultValues
import fr.husi.ktx.onIoDispatcher
import fr.husi.ktx.runOnIoDispatcher
import fr.husi.ui.StringOrRes
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import fr.husi.resources.*

@Immutable
internal sealed interface ProfileSettingsUiEvent {
    data class Alert(val title: StringOrRes, val message: StringOrRes) : ProfileSettingsUiEvent
}

@Immutable
internal sealed interface ProfileSettingsUiState {
    val customConfig: String
    val customOutbound: String
}

@Stable
internal abstract class ProfileSettingsViewModel<T : AbstractBean> : ViewModel() {

    abstract val uiState: StateFlow<ProfileSettingsUiState>

    private val _uiEvent = MutableSharedFlow<ProfileSettingsUiEvent>()
    val uiEvent = _uiEvent.asSharedFlow()

    protected suspend fun emitAlert(title: StringOrRes, message: StringOrRes) {
        _uiEvent.emit(ProfileSettingsUiEvent.Alert(title, message))
    }

    private val _initialState = MutableStateFlow<ProfileSettingsUiState?>(null)
    val isDirty by lazy(LazyThreadSafetyMode.NONE) {
        uiState.map { currentState ->
            _initialState.value?.let { initialState ->
                initialState != currentState
            } ?: false
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false,
        )
    }

    protected abstract fun createBean(): T
    protected abstract suspend fun T.writeToUiState()
    protected abstract fun T.loadFromUiState()

    protected var editingId = -1L
    val isNew get() = editingId < 0L
    lateinit var proxyEntity: ProxyEntity
    lateinit var bean: T
    var isSubscription = false

    fun initialize(editingId: Long, isSubscription: Boolean) = viewModelScope.launch {
        this@ProfileSettingsViewModel.editingId = editingId
        this@ProfileSettingsViewModel.isSubscription = isSubscription

        bean = if (isNew) {
            createBean().applyDefaultValues()
        } else {
            proxyEntity = onIoDispatcher { SagerDatabase.proxyDao.getById(editingId)!! }
            (proxyEntity.requireBean() as T)
        }
        bean.writeToUiState()
        _initialState.value = uiState.value
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
    Res.string.mux_max_connections,
    Res.string.mux_min_streams,
    Res.string.mux_max_streams,
)
