@file:OptIn(ExperimentalAtomicApi::class)

package fr.husi.ui

import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.husi.GroupType
import fr.husi.Key
import fr.husi.SubscriptionType
import fr.husi.database.DataStore
import fr.husi.database.GroupManager
import fr.husi.database.ProfileManager
import fr.husi.database.ProxyGroup
import fr.husi.database.SubscriptionBean
import fr.husi.fmt.AbstractBean
import fr.husi.fmt.KryoConverters
import fr.husi.group.GroupUpdater
import fr.husi.group.RawUpdater
import fr.husi.ktx.Logs
import fr.husi.ktx.SubscriptionFoundException
import fr.husi.ktx.b64Decode
import fr.husi.ktx.blankAsNull
import fr.husi.ktx.defaultOr
import fr.husi.ktx.parseProxies
import fr.husi.ktx.readableMessage
import fr.husi.ktx.runOnIoDispatcher
import fr.husi.ktx.zlibDecompress
import fr.husi.libcore.Libcore
import fr.husi.repository.repo
import fr.husi.utils.LibcoreClientManager
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import fr.husi.resources.*

@Immutable
sealed interface URLTestStatus {
    object Initial : URLTestStatus
    object Testing : URLTestStatus
    class Success(val legacy: Int) : URLTestStatus
    class Exception(val exception: String) : URLTestStatus
}

@Immutable
data class AlertButton(
    val label: StringOrRes,
    val onClick: () -> Unit,
)

@Immutable
sealed interface MainViewModelUiEvent {
    class Snackbar(val message: StringOrRes) : MainViewModelUiEvent
    class SnackbarWithAction(
        val message: StringOrRes,
        val actionLabel: StringOrRes,
        val callback: (SnackbarResult) -> Unit,
    ) : MainViewModelUiEvent

    class AlertDialog(
        val title: StringOrRes,
        val message: StringOrRes,
        val confirmButton: AlertButton,
        val dismissButton: AlertButton? = null,
    ) : MainViewModelUiEvent
}

@Stable
class MainViewModel : ViewModel(), GroupManager.Interface {

    private val _urlTestStatus = MutableStateFlow<URLTestStatus>(URLTestStatus.Initial)
    val urlTestStatus = _urlTestStatus.asStateFlow()

    private val _uiEvent = MutableSharedFlow<MainViewModelUiEvent>()
    val uiEvent = _uiEvent.asSharedFlow()

    private fun alertDialog(
        message: StringOrRes,
        title: StringOrRes = StringOrRes.Res(Res.string.error_title),
    ) = MainViewModelUiEvent.AlertDialog(
        title = title,
        message = message,
        confirmButton = AlertButton(StringOrRes.Res(Res.string.ok)) {},
    )

    init {
        GroupManager.userInterface = this

        viewModelScope.launch {
            DataStore.configurationStore.keysFlow(
                Key.PROXY_APPS,
                Key.BYPASS_MODE,
                Key.PACKAGES,
            ).collectLatest {
                if (DataStore.serviceState.canStop) {
                    _uiEvent.emit(
                        MainViewModelUiEvent.SnackbarWithAction(
                            message = StringOrRes.Res(Res.string.need_reload),
                            actionLabel = StringOrRes.Res(Res.string.apply),
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

    override fun onCleared() {
        GroupManager.userInterface = null
        runBlocking {
            urlTestClient.close()
        }
        super.onCleared()
    }

    fun showSnackbar(message: StringOrRes) = viewModelScope.launch {
        _uiEvent.emit(MainViewModelUiEvent.Snackbar(message))
    }

    fun resetUrlTestStatus() {
        _urlTestStatus.value = URLTestStatus.Initial
    }

    private val urlTestClient = LibcoreClientManager()

    fun urlTest() = viewModelScope.launch(Dispatchers.IO) {
        _urlTestStatus.update { status ->
            if (status == URLTestStatus.Testing) {
                return@launch
            }
            URLTestStatus.Testing
        }
        if (!DataStore.serviceState.connected) {
            _urlTestStatus.update { URLTestStatus.Exception("not started") }
            return@launch
        }
        try {
            var result = -1
            urlTestClient.withClient { client ->
                result = client.urlTest(
                    "",
                    DataStore.connectionTestURL,
                    DataStore.connectionTestTimeout,
                )
            }
            _urlTestStatus.update { URLTestStatus.Success(result) }
        } catch (e: Exception) {
            _urlTestStatus.update { URLTestStatus.Exception(e.readableMessage) }
        }
    }

    fun importFromUri(uri: String) {
        if (uri.startsWith("sing-box://") || uri.startsWith("husi://subscription")) {
            importSubscription(uri)
        } else {
            importProfileFromUri(uri)
        }
    }

    fun importSubscription(uri: String) = viewModelScope.launch {
        val urlForQuery = try {
            Libcore.parseURL(uri)
        } catch (e: Exception) {
            _uiEvent.emit(alertDialog(StringOrRes.Direct(e.readableMessage)))
            return@launch
        }
        val group: ProxyGroup
        val url = defaultOr(
            "",
            { urlForQuery.queryParameter("url") },
            {
                when (urlForQuery.scheme) {
                    "http", "https" -> uri
                    else -> null
                }
            },
        )
        if (url.isNotBlank()) {
            group = ProxyGroup(type = GroupType.SUBSCRIPTION)
            group.subscription = SubscriptionBean().apply {
                // cleartext format
                link = url
                type = when (urlForQuery.queryParameter("type")?.lowercase()) {
                    "oocv1" -> SubscriptionType.OOCv1
                    "sip008" -> SubscriptionType.SIP008
                    else -> SubscriptionType.RAW
                }
            }

            group.name = defaultOr(
                "",
                { urlForQuery.queryParameter("name") },
                { urlForQuery.fragment },
            )
        } else {
            val data =
                uri.substringAfter('?', "").substringBefore('#').blankAsNull() ?: return@launch
            try {
                group = KryoConverters.deserialize(
                    ProxyGroup().apply { export = true },
                    data.b64Decode().zlibDecompress(),
                ).apply {
                    export = false
                }
            } catch (e: Exception) {
                _uiEvent.emit(alertDialog(StringOrRes.Direct(e.readableMessage)))
                return@launch
            }
        }

        if (group.name.isNullOrBlank() && group.subscription?.link.isNullOrBlank() && group.subscription?.token.isNullOrBlank()) {
            return@launch
        }
        group.name = group.name.blankAsNull() ?: ("Subscription #" + System.currentTimeMillis())

        val detail = group.name + "\n" + group.subscription?.link + "\n" + group.subscription?.token
        _uiEvent.emit(
            MainViewModelUiEvent.AlertDialog(
                title = StringOrRes.Res(Res.string.subscription_import),
                message = StringOrRes.ResWithParams(Res.string.subscription_import_message, detail),
                confirmButton = AlertButton(StringOrRes.Res(Res.string.ok)) {
                    runOnIoDispatcher {
                        GroupManager.createGroup(group)
                        GroupUpdater.startUpdate(group, true)
                    }
                },
                dismissButton = AlertButton(StringOrRes.Res(Res.string.cancel)) {},
            ),
        )
    }

    private fun importProfileFromUri(uri: String) = viewModelScope.launch {
        val profiles = try {
            parseProxies(uri)
        } catch (e: Exception) {
            _uiEvent.emit(alertDialog(StringOrRes.Direct(e.readableMessage)))
            return@launch
        }
        if (profiles.isEmpty()) {
            _uiEvent.emit(alertDialog(StringOrRes.Res(Res.string.no_proxies_found)))
            return@launch
        }
        _uiEvent.emit(
            MainViewModelUiEvent.AlertDialog(
                title = StringOrRes.Res(Res.string.profile_import),
                message = StringOrRes.ResWithParams(
                    Res.string.profile_import_message,
                    profiles.joinToString("\n") { it.displayName() },
                ),
                confirmButton = AlertButton(StringOrRes.Res(Res.string.ok)) {
                    runOnIoDispatcher {
                        importProfile(profiles)
                    }
                },
                dismissButton = AlertButton(StringOrRes.Res(Res.string.cancel)) {},
            ),
        )
    }

    fun parseProxy(text: String?) = viewModelScope.launch {
        if (text.isNullOrBlank()) {
            _uiEvent.emit(MainViewModelUiEvent.Snackbar(StringOrRes.Res(Res.string.clipboard_empty)))
            return@launch
        }
        // Import as proxy or subscription
        when (text.substringBefore("://", "").lowercase()) {
            "http", "https" -> _uiEvent.emit(
                MainViewModelUiEvent.AlertDialog(
                    title = StringOrRes.Res(Res.string.profile_import),
                    message = StringOrRes.Direct(text),
                    confirmButton = AlertButton(StringOrRes.Res(Res.string.subscription_import)) {
                        importSubscription(text)
                    },
                    dismissButton = AlertButton(StringOrRes.Res(Res.string.profile_import)) {
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
                _uiEvent.emit(MainViewModelUiEvent.Snackbar(StringOrRes.Res(Res.string.no_proxies_found_in_clipboard)))
            } else {
                importProfile(proxies)
            }
        } catch (e: SubscriptionFoundException) {
            importSubscription(e.link)
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
                    Res.plurals.added,
                    proxies.size,
                    proxies.size,
                ),
            ),
        )
    }

    override suspend fun confirm(message: String): Boolean {
        val deferred = CompletableDeferred<Boolean>()
        _uiEvent.emit(
            MainViewModelUiEvent.AlertDialog(
                title = StringOrRes.Res(Res.string.confirm),
                message = StringOrRes.Direct(message),
                confirmButton = AlertButton(StringOrRes.Res(Res.string.ok)) {
                    deferred.complete(true)
                },
                dismissButton = AlertButton(StringOrRes.Res(Res.string.cancel)) {
                    deferred.complete(false)
                },
            ),
        )
        return deferred.await()
    }

    override suspend fun alert(message: String) {
        _uiEvent.emit(alertDialog(StringOrRes.Direct(message)))
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
        if (changed == 0) {
            _uiEvent.emit(
                MainViewModelUiEvent.Snackbar(
                    StringOrRes.ResWithParams(Res.string.group_no_difference, group.displayName()),
                ),
            )
            return
        }
        if (!byUser) {
            _uiEvent.emit(
                MainViewModelUiEvent.Snackbar(
                    StringOrRes.ResWithParams(Res.string.group_updated, group.displayName(), changed),
                ),
            )
            return
        }

        val parts = buildList {
            add(StringOrRes.ResWithParams(Res.string.group_updated, group.displayName(), changed))
            if (added.isNotEmpty()) {
                add(StringOrRes.ResWithParams(Res.string.group_added, added.joinToString("\n")))
            }
            if (updated.isNotEmpty()) {
                add(
                    StringOrRes.ResWithParams(
                        Res.string.group_changed,
                        updated.entries.joinToString("\n") { "${it.key} -> ${it.value}" },
                    ),
                )
            }
            if (deleted.isNotEmpty()) {
                add(
                    StringOrRes.ResWithParams(
                        Res.string.group_deleted,
                        deleted.joinToString("\n"),
                    ),
                )
            }
            if (duplicate.isNotEmpty()) {
                add(
                    StringOrRes.ResWithParams(
                        Res.string.group_duplicate,
                        duplicate.joinToString("\n"),
                    ),
                )
            }
        }
        _uiEvent.emit(
            alertDialog(
                message = StringOrRes.Compound(parts),
                title = StringOrRes.ResWithParams(Res.string.group_diff, group.displayName()),
            ),
        )
    }

    override suspend fun onUpdateWarning(group: String, error: String) {
        _uiEvent.emit(
            MainViewModelUiEvent.Snackbar(
                StringOrRes.Compound(
                    parts = listOf(
                        StringOrRes.Direct(group),
                        StringOrRes.ResWithParams(Res.string.force_resolve_error, error),
                    ),
                    separator = ": ",
                ),
            ),
        )
    }

    override suspend fun onUpdateFailure(group: ProxyGroup, message: String) {
        _uiEvent.emit(
            alertDialog(StringOrRes.Direct("${group.name}: $message")),
        )
    }
}
