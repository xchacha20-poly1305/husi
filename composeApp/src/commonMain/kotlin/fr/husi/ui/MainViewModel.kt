@file:OptIn(ExperimentalAtomicApi::class)

package fr.husi.ui

import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.husi.GroupType
import fr.husi.Key
import fr.husi.bg.DeepLinkDispatcher
import fr.husi.database.DataStore
import fr.husi.database.ProxyGroup
import fr.husi.database.SagerDatabase
import fr.husi.fmt.AbstractBean
import fr.husi.group.GroupUpdateResult
import fr.husi.group.GroupUpdateWarning
import fr.husi.group.GroupUpdater
import fr.husi.group.RawUpdater
import fr.husi.core.CoreClient
import fr.husi.core.urlTestOptions
import fr.husi.ktx.Logs
import fr.husi.ktx.SubscriptionFoundException
import fr.husi.ktx.onIoDispatcher
import fr.husi.ktx.readableMessage
import fr.husi.repository.Repository
import fr.husi.repository.resolveRepository
import fr.husi.resources.*
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.core.context.GlobalContext

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
    private val coreClient: CoreClient = GlobalContext.get().get(),
) : ViewModel() {

    val urlTestStatus: StateFlow<URLTestStatus>
        field = MutableStateFlow<URLTestStatus>(URLTestStatus.Initial)

    val uiEvent: SharedFlow<MainViewModelUiEvent>
        field = MutableSharedFlow<MainViewModelUiEvent>()

    private fun alertDialog(
        message: StringOrRes,
        title: StringOrRes = StringOrRes.Res(Res.string.error_title),
    ) = MainViewModelUiEvent.AlertDialog(
        title = title,
        message = message,
        confirmButton = AlertButton(StringOrRes.Res(Res.string.ok)) {},
    )

    init {
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
                    uiEvent.emit(
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

    fun showSnackbar(message: StringOrRes) = viewModelScope.launch {
        uiEvent.emit(MainViewModelUiEvent.Snackbar(message))
    }

    fun resetUrlTestStatus() {
        urlTestStatus.value = URLTestStatus.Initial
    }

    fun urlTest() = viewModelScope.launch(Dispatchers.IO) {
        urlTestStatus.update { status ->
            if (status == URLTestStatus.Testing) {
                return@launch
            }
            URLTestStatus.Testing
        }
        if (!DataStore.serviceState.connected) {
            urlTestStatus.update { URLTestStatus.Exception("not started") }
            return@launch
        }
        try {
            val result = coreClient.urlTest(
                "",
                DataStore.connectionTestURL,
                DataStore.connectionTestTimeout,
                urlTestOptions(
                    DataStore.connectionTestUnifiedDelay,
                    DataStore.connectionTestIgnoreHandshakeTime,
                ),
            )
            urlTestStatus.update { URLTestStatus.Success(result) }
        } catch (e: Exception) {
            urlTestStatus.update { URLTestStatus.Exception(e.readableMessage) }
        }
    }

    fun importFromUri(uri: String) = viewModelScope.launch {
        val preview = try {
            importLinkInteractor.parseUri(uri)
        } catch (e: Exception) {
            uiEvent.emit(alertDialog(StringOrRes.Direct(e.readableMessage)))
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
            uiEvent.emit(alertDialog(StringOrRes.Direct(e.readableMessage)))
            return@launch
        } ?: return@launch
        showImportSubscriptionDialog(group)
    }

    private suspend fun showImportSubscriptionDialog(group: ProxyGroup) {
        val detail = group.name + "\n" + group.subscription?.link + "\n" + group.subscription?.token
        uiEvent.emit(
            MainViewModelUiEvent.AlertDialog(
                title = StringOrRes.Res(Res.string.subscription_import),
                message = StringOrRes.ResWithParams(Res.string.subscription_import_message, detail),
                confirmButton = AlertButton(StringOrRes.Res(Res.string.ok)) {
                    viewModelScope.launch(Dispatchers.Default) {
                        val createdGroup = onIoDispatcher {
                            importLinkInteractor.createSubscriptionGroup(group)
                        }
                        performGroupUpdate(createdGroup, true)
                    }
                },
                dismissButton = AlertButton(StringOrRes.Res(Res.string.cancel)) {},
            ),
        )
    }

    private suspend fun showImportProfileDialog(profiles: List<AbstractBean>) {
        if (profiles.isEmpty()) {
            uiEvent.emit(alertDialog(StringOrRes.Res(Res.string.no_proxies_found)))
            return
        }
        uiEvent.emit(
            MainViewModelUiEvent.AlertDialog(
                title = StringOrRes.Res(Res.string.profile_import),
                message = StringOrRes.ResWithParams(
                    Res.string.profile_import_message,
                    profiles.joinToString("\n") { it.displayName() },
                ),
                confirmButton = AlertButton(StringOrRes.Res(Res.string.ok)) {
                    viewModelScope.launch(Dispatchers.IO) {
                        importProfile(profiles)
                    }
                },
                dismissButton = AlertButton(StringOrRes.Res(Res.string.cancel)) {},
            ),
        )
    }

    fun parseProxy(text: String?) = viewModelScope.launch {
        if (text.isNullOrBlank()) {
            uiEvent.emit(MainViewModelUiEvent.Snackbar(StringOrRes.Res(Res.string.clipboard_empty)))
            return@launch
        }
        when (text.substringBefore("://", "").lowercase()) {
            "http", "https" -> uiEvent.emit(
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
                uiEvent.emit(MainViewModelUiEvent.Snackbar(StringOrRes.Res(Res.string.no_proxies_found_in_clipboard)))
            } else {
                importProfile(proxies)
            }
        } catch (e: SubscriptionFoundException) {
            importSubscription(e.link)
        } catch (e: Exception) {
            Logs.w(e)
            uiEvent.emit(MainViewModelUiEvent.Snackbar(StringOrRes.Direct(e.readableMessage)))
        }
    }

    suspend fun importProfile(proxies: List<AbstractBean>) {
        val importedCount = importLinkInteractor.importProfiles(proxies)
        uiEvent.emit(
            MainViewModelUiEvent.Snackbar(
                StringOrRes.PluralsRes(
                    Res.plurals.added,
                    importedCount,
                    importedCount,
                ),
            ),
        )
    }

    fun updateSubscriptionGroup(group: ProxyGroup) = viewModelScope.launch(Dispatchers.Default) {
        performGroupUpdate(group, true)
    }

    fun updateAllSubscriptionGroups() = viewModelScope.launch(Dispatchers.Default) {
        val groups = onIoDispatcher {
            SagerDatabase.groupDao.allGroups().first()
                .filter { it.type == GroupType.SUBSCRIPTION }
        }
        for (group in groups) {
            performGroupUpdate(group, true)
        }
    }

    suspend fun confirm(message: String): Boolean {
        val deferred = CompletableDeferred<Boolean>()
        uiEvent.emit(
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

    private suspend fun performGroupUpdate(group: ProxyGroup, byUser: Boolean) {
        var allowDisconnectedUpdate = false
        while (true) {
            when (val result = GroupUpdater.executeUpdate(group, byUser, allowDisconnectedUpdate)) {
                is GroupUpdateResult.AlreadyUpdating -> return

                is GroupUpdateResult.ConfirmRequired -> {
                    if (!confirm(result.message)) return
                    allowDisconnectedUpdate = true
                }

                else -> {
                    presentGroupUpdateResult(result)
                    return
                }
            }
        }
    }

    private suspend fun presentGroupUpdateResult(result: GroupUpdateResult) {
        presentGroupUpdateWarnings(result.warningsOrEmpty())
        when (result) {
            is GroupUpdateResult.Success -> presentGroupUpdateSuccess(result)
            is GroupUpdateResult.Failure -> {
                uiEvent.emit(alertDialog(StringOrRes.Direct("${result.group.name}: ${result.message}")))
            }

            else -> Unit
        }
    }

    private suspend fun presentGroupUpdateSuccess(result: GroupUpdateResult.Success) {
        val changed = result.diff.changed
        if (changed == 0) {
            uiEvent.emit(
                MainViewModelUiEvent.Snackbar(
                    StringOrRes.ResWithParams(Res.string.group_no_difference, result.group.displayName()),
                ),
            )
            return
        }
        if (!result.byUser) {
            uiEvent.emit(
                MainViewModelUiEvent.Snackbar(
                    StringOrRes.PluralsRes(
                        Res.plurals.group_updated,
                        changed,
                        result.group.displayName(),
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
                    result.group.displayName(),
                    changed,
                ),
            )
            if (result.diff.added.isNotEmpty()) {
                add(StringOrRes.ResWithParams(Res.string.group_added, result.diff.added.joinToString("\n")))
            }
            if (result.diff.updated.isNotEmpty()) {
                add(
                    StringOrRes.ResWithParams(
                        Res.string.group_changed,
                        result.diff.updated.entries.joinToString("\n") { "${it.key} -> ${it.value}" },
                    ),
                )
            }
            if (result.diff.deleted.isNotEmpty()) {
                add(
                    StringOrRes.ResWithParams(
                        Res.string.group_deleted,
                        result.diff.deleted.joinToString("\n"),
                    ),
                )
            }
            if (result.diff.duplicate.isNotEmpty()) {
                add(
                    StringOrRes.ResWithParams(
                        Res.string.group_duplicate,
                        result.diff.duplicate.joinToString("\n"),
                    ),
                )
            }
        }
        uiEvent.emit(
            alertDialog(
                message = StringOrRes.Compound(parts),
                title = StringOrRes.ResWithParams(Res.string.group_diff, result.group.displayName()),
            ),
        )
    }

    private suspend fun presentGroupUpdateWarnings(warnings: List<GroupUpdateWarning>) {
        for (warning in warnings) {
            uiEvent.emit(
                MainViewModelUiEvent.Snackbar(
                    StringOrRes.Compound(
                        parts = listOf(
                            StringOrRes.Direct(warning.group),
                            StringOrRes.Direct(warning.message),
                        ),
                        separator = ": ",
                    ),
                ),
            )
        }
    }
}

private fun GroupUpdateResult.warningsOrEmpty(): List<GroupUpdateWarning> = when (this) {
    is GroupUpdateResult.Success -> warnings
    is GroupUpdateResult.Failure -> warnings
    else -> emptyList()
}
