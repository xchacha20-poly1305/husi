@file:OptIn(ExperimentalAtomicApi::class)

package fr.husi.ui

import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.husi.Key
import fr.husi.bg.DeepLinkDispatcher
import fr.husi.database.DataStore
import fr.husi.database.GroupManager
import fr.husi.database.ProxyGroup
import fr.husi.fmt.AbstractBean
import fr.husi.group.RawUpdater
import fr.husi.ktx.Logs
import fr.husi.ktx.SubscriptionFoundException
import fr.husi.ktx.readableMessage
import fr.husi.ktx.runOnIoDispatcher
import fr.husi.repository.Repository
import fr.husi.repository.resolveRepository
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
        val onDismiss: (() -> Unit)? = null,
    ) : MainViewModelUiEvent
}

@Stable
class MainViewModel(
    private val repository: Repository = resolveRepository(),
    private val importLinkInteractor: ImportLinkInteractor = ImportLinkInteractor(),
) : ViewModel(), GroupManager.Interface {

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
            DeepLinkDispatcher.flow.collect { link ->
                importFromUri(link)
            }
        }

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
                                    repository.reloadService()
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

    fun importFromUri(uri: String) = viewModelScope.launch {
        val preview = try {
            importLinkInteractor.parseUri(uri)
        } catch (e: Exception) {
            _uiEvent.emit(alertDialog(StringOrRes.Direct(e.readableMessage)))
            return@launch
        }
        when (preview) {
            ImportLinkPreview.Ignore -> Unit
            is ImportLinkPreview.Subscription -> showImportSubscriptionDialog(preview.group)
            is ImportLinkPreview.Profiles -> showImportProfileDialog(preview.proxies)
        }
    }

    fun importSubscription(uri: String) = viewModelScope.launch {
        val group = try {
            importLinkInteractor.parseSubscription(uri)
        } catch (e: Exception) {
            _uiEvent.emit(alertDialog(StringOrRes.Direct(e.readableMessage)))
            return@launch
        } ?: return@launch
        showImportSubscriptionDialog(group)
    }

    private suspend fun showImportSubscriptionDialog(group: ProxyGroup) {
        val detail = group.name + "\n" + group.subscription?.link + "\n" + group.subscription?.token
        _uiEvent.emit(
            MainViewModelUiEvent.AlertDialog(
                title = StringOrRes.Res(Res.string.subscription_import),
                message = StringOrRes.ResWithParams(Res.string.subscription_import_message, detail),
                confirmButton = AlertButton(StringOrRes.Res(Res.string.ok)) {
                    runOnIoDispatcher {
                        importLinkInteractor.importSubscription(group)
                    }
                },
                dismissButton = AlertButton(StringOrRes.Res(Res.string.cancel)) {},
            ),
        )
    }

    private suspend fun showImportProfileDialog(profiles: List<AbstractBean>) {
        if (profiles.isEmpty()) {
            _uiEvent.emit(alertDialog(StringOrRes.Res(Res.string.no_proxies_found)))
            return
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
                    title = StringOrRes.Res(Res.string.import_url),
                    message = StringOrRes.Res(Res.string.import_http_url),
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
        val importedCount = importLinkInteractor.importProfiles(proxies)
        _uiEvent.emit(
            MainViewModelUiEvent.Snackbar(
                StringOrRes.PluralsRes(
                    Res.plurals.added,
                    importedCount,
                    importedCount,
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
                onDismiss = {
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
                    StringOrRes.PluralsRes(
                        Res.plurals.group_updated,
                        changed,
                        group.displayName(),
                        changed,
                    ),
                ),
            )
            return
        }

        val parts = buildList {
            add(
                StringOrRes.PluralsRes(
                    Res.plurals.group_updated,
                    changed,
                    group.displayName(),
                    changed,
                ),
            )
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
