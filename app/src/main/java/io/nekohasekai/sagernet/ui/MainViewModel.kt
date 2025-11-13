package io.nekohasekai.sagernet.ui

import android.net.Uri
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.nekohasekai.sagernet.GroupType
import io.nekohasekai.sagernet.Key
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.SubscriptionType
import io.nekohasekai.sagernet.aidl.ISagerNetService
import io.nekohasekai.sagernet.aidl.SpeedDisplayData
import io.nekohasekai.sagernet.bg.BaseService
import io.nekohasekai.sagernet.bg.SagerConnection
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.GroupManager
import io.nekohasekai.sagernet.database.ProfileManager
import io.nekohasekai.sagernet.database.ProxyGroup
import io.nekohasekai.sagernet.database.SubscriptionBean
import io.nekohasekai.sagernet.fmt.AbstractBean
import io.nekohasekai.sagernet.fmt.KryoConverters
import io.nekohasekai.sagernet.group.GroupUpdater
import io.nekohasekai.sagernet.group.RawUpdater
import io.nekohasekai.sagernet.ktx.Logs
import io.nekohasekai.sagernet.ktx.SubscriptionFoundException
import io.nekohasekai.sagernet.ktx.b64Decode
import io.nekohasekai.sagernet.ktx.blankAsNull
import io.nekohasekai.sagernet.ktx.defaultOr
import io.nekohasekai.sagernet.ktx.parseProxies
import io.nekohasekai.sagernet.ktx.readableMessage
import io.nekohasekai.sagernet.ktx.runOnIoDispatcher
import io.nekohasekai.sagernet.ktx.zlibDecompress
import io.nekohasekai.sagernet.repository.repo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Immutable
data class MainUiState(
    val txSpeed: Long = 0,
    val rxSpeed: Long = 0,
    val urlTestResult: Int? = null,
)

@Immutable
sealed interface MainViewModelUiEvent {
    class Snackbar(val message: StringOrRes) : MainViewModelUiEvent
    class SnackbarWithAction(
        val message: StringOrRes,
        val actionLabel: StringOrRes,
        val callback: (SnackbarResult) -> Unit,
    ) : MainViewModelUiEvent

    class Alert(val message: StringOrRes) : MainViewModelUiEvent
    class ConfirmImportSubscription(
        val detail: String,
        val confirm: () -> Unit,
    ) : MainViewModelUiEvent

    class ConfirmImportProfile(val name: String, val confirm: () -> Unit) : MainViewModelUiEvent
    class ImportSubscriptionOrHttp(
        val link: String,
        val asSubscription: () -> Unit,
        val asProxy: () -> Unit,
    ) : MainViewModelUiEvent
}

@Stable
class MainViewModel() : ViewModel(),
    SagerConnection.Callback, GroupManager.Interface {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState = _uiState.asStateFlow()

    private val _uiEvent = MutableSharedFlow<MainViewModelUiEvent>()
    val uiEvent = _uiEvent.asSharedFlow()

    init {
        GroupManager.userInterface = this

        viewModelScope.launch {
            DataStore.configurationStore.keysFlow(Key.SERVICE_MODE)
                .drop(1) // Skip initial value
                .collect {
                    onBinderDied()
                }
        }
        viewModelScope.launch {
            DataStore.configurationStore.keysFlow(
                Key.PROXY_APPS,
                Key.BYPASS_MODE,
                Key.PACKAGES,
            ).collect {
                if (DataStore.serviceState.canStop) {
                    _uiEvent.emit(
                        MainViewModelUiEvent.SnackbarWithAction(
                            message = StringOrRes.Res(R.string.need_reload),
                            actionLabel = StringOrRes.Res(R.string.apply),
                            callback = { result ->
                                if (result == SnackbarResult.ActionPerformed) {
                                    repo.reloadService()
                                }
                            },
                        ),
                    )
                }
            }
        }
    }

    fun showSnackbar(message: StringOrRes) = viewModelScope.launch {
        _uiEvent.emit(MainViewModelUiEvent.Snackbar(message))
    }

    fun urlTest(service: ISagerNetService?) = viewModelScope.launch(Dispatchers.IO) {
        try {
            if (!DataStore.serviceState.connected || service == null) {
                error("not started")
            }
            val result = service.urlTest(null)
            _uiState.update {
                it.copy(urlTestResult = result)
            }
        } catch (e: Exception) {
            _uiEvent.emit(MainViewModelUiEvent.Snackbar(StringOrRes.Direct(e.readableMessage)))
            _uiState.update {
                it.copy(urlTestResult = null)
            }
        }
    }

    fun importFromUri(uri: Uri) {
        if (uri.scheme == "husi" && uri.host == "subscription" || uri.scheme == "sing-box") {
            importSubscription(uri)
        } else {
            importProfileFromUri(uri)
        }
    }

    fun importSubscription(uri: Uri) = viewModelScope.launch {
        val group: ProxyGroup
        val url = defaultOr(
            "",
            { uri.getQueryParameter("url") },
            {
                when (uri.scheme) {
                    "http", "https" -> uri.toString()
                    else -> null
                }
            },
        )
        if (url.isNotBlank()) {
            group = ProxyGroup(type = GroupType.SUBSCRIPTION)
            val subscription = SubscriptionBean()

            // cleartext format
            subscription.link = url
            subscription.type = when (uri.getQueryParameter("type")?.lowercase()) {
                "oocv1" -> SubscriptionType.OOCv1
                "sip008" -> SubscriptionType.SIP008
                else -> SubscriptionType.RAW
            }
            group.name = defaultOr(
                "",
                { uri.getQueryParameter("name") },
                { uri.fragment },
            )
        } else {
            val data =
                uri.encodedQuery.takeIf { !it.isNullOrBlank() } ?: return@launch
            try {
                group = KryoConverters.deserialize(
                    ProxyGroup().apply { export = true },
                    data.b64Decode().zlibDecompress(),
                ).apply {
                    export = false
                }
            } catch (e: Exception) {
                _uiEvent.emit(MainViewModelUiEvent.Alert(StringOrRes.Direct(e.readableMessage)))
                return@launch
            }
        }

        if (group.name.isNullOrBlank() || group.subscription?.link.isNullOrBlank() || group.subscription?.token.isNullOrBlank()) {
            return@launch
        }
        group.name = group.name.blankAsNull() ?: ("Subscription #" + System.currentTimeMillis())

        val detail = group.name + "\n" + group.subscription?.link + "\n" + group.subscription?.token
        _uiEvent.emit(
            MainViewModelUiEvent.ConfirmImportSubscription(
                detail = detail,
                confirm = {
                    runOnIoDispatcher {
                        GroupManager.createGroup(group)
                        GroupUpdater.startUpdate(group, true)
                    }
                },
            ),
        )
    }

    private fun importProfileFromUri(uri: Uri) = viewModelScope.launch {
        val profiles = try {
            parseProxies(uri.toString())
        } catch (e: Exception) {
            _uiEvent.emit(MainViewModelUiEvent.Alert(StringOrRes.Direct(e.readableMessage)))
            return@launch
        }
        if (profiles.isEmpty()) {
            _uiEvent.emit(MainViewModelUiEvent.Alert(StringOrRes.Res(R.string.no_proxies_found)))
            return@launch
        }
        _uiEvent.emit(
            MainViewModelUiEvent.ConfirmImportProfile(
                name = profiles.joinToString("\n") { it.displayName() },
                confirm = {
                    runOnIoDispatcher {
                        importProfile(profiles)
                    }
                },
            ),
        )
    }

    fun parseProxy(text: String?) = viewModelScope.launch {
        if (text.isNullOrBlank()) {
            _uiEvent.emit(MainViewModelUiEvent.Snackbar(StringOrRes.Res(R.string.clipboard_empty)))
            return@launch
        }
        // single line uri
        val uri = try {
            text.toUri()
        } catch (_: Exception) {
            null
        }
        // Import as proxy or subscription
        when (uri?.scheme) {
            "http", "https" -> _uiEvent.emit(
                MainViewModelUiEvent.ImportSubscriptionOrHttp(
                    link = text,
                    asSubscription = {
                        importSubscription(uri)
                    },
                    asProxy = {
                        viewModelScope.launch {
                            parseSubscription(text)
                        }
                    },
                ),
            )

            else -> parseSubscription(text)
        }
    }

    private suspend fun parseSubscription(text: String) {
        try {
            val proxies = RawUpdater.parseRaw(text)
            if (proxies.isNullOrEmpty()) {
                _uiEvent.emit(MainViewModelUiEvent.Snackbar(StringOrRes.Res(R.string.no_proxies_found_in_clipboard)))
            } else {
                importProfile(proxies)
            }
        } catch (e: SubscriptionFoundException) {
            importSubscription(e.link.toUri())
        } catch (e: Exception) {
            Logs.w(e)
            _uiEvent.emit(MainViewModelUiEvent.Snackbar(StringOrRes.Direct(e.readableMessage)))
        }
    }

    suspend fun importProfile(proxies: List<AbstractBean>) {
        val targetId = DataStore.selectedGroupForImport()
        for (proxy in proxies) {
            ProfileManager.createProfile(targetId, proxy)
        }
        DataStore.selectedGroup = targetId
        _uiEvent.emit(
            MainViewModelUiEvent.Snackbar(
                StringOrRes.PluralsRes(
                    R.plurals.added,
                    proxies.size,
                    proxies.size,
                ),
            ),
        )
    }

    override fun cbSpeedUpdate(stats: SpeedDisplayData) {
        _uiState.update { state ->
            state.copy(
                txSpeed = stats.txRateProxy,
                rxSpeed = stats.rxRateProxy,
            )
        }
    }

    override fun stateChanged(
        state: BaseService.State,
        profileName: String?,
        msg: String?,
    ) {
        TODO("Not yet implemented")
    }

    override fun missingPlugin(profileName: String, pluginName: String) {
        super.missingPlugin(profileName, pluginName)
    }

    override fun onServiceConnected(service: ISagerNetService) {
        TODO("Not yet implemented")
    }

    override fun onServiceDisconnected() {
        super.onServiceDisconnected()
    }

    override fun onBinderDied() {
        super.onBinderDied()
    }

    override suspend fun confirm(message: String): Boolean {
        TODO("Not yet implemented")
    }

    override suspend fun alert(message: String) {
        TODO("Not yet implemented")
    }

    override suspend fun onUpdateSuccess(
        group: ProxyGroup,
        changed: Int,
        added: List<String>,
        updated: Map<String, String>,
        deleted: List<String>,
        duplicate: List<String>,
        byUser: Boolean,
    ) {
        TODO("Not yet implemented")
    }

    override suspend fun onUpdateFailure(
        group: ProxyGroup,
        message: String,
    ) {
        TODO("Not yet implemented")
    }
}