package io.nekohasekai.sagernet.ui.configuration

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.runtime.Stable
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.nekohasekai.sagernet.Key
import io.nekohasekai.sagernet.bg.proto.TestInstance
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.GroupManager
import io.nekohasekai.sagernet.database.ProfileManager
import io.nekohasekai.sagernet.database.ProxyEntity
import io.nekohasekai.sagernet.database.ProxyGroup
import io.nekohasekai.sagernet.database.SagerDatabase
import io.nekohasekai.sagernet.fmt.AbstractBean
import io.nekohasekai.sagernet.fmt.Deduplication
import io.nekohasekai.sagernet.group.RawUpdater
import io.nekohasekai.sagernet.ktx.Logs
import io.nekohasekai.sagernet.ktx.SubscriptionFoundException
import io.nekohasekai.sagernet.ktx.isIpAddress
import io.nekohasekai.sagernet.ktx.onDefaultDispatcher
import io.nekohasekai.sagernet.ktx.onIoDispatcher
import io.nekohasekai.sagernet.ktx.readableMessage
import io.nekohasekai.sagernet.ktx.removeFirstMatched
import io.nekohasekai.sagernet.ktx.runOnIoDispatcher
import io.nekohasekai.sagernet.plugin.PluginManager
import io.nekohasekai.sagernet.repository.repo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import libcore.Libcore
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.zip.ZipInputStream

@Stable
data class ConfigurationFragmentUiState(
    val groups: List<ProxyGroup> = emptyList(),
    val selectedGroupIndex: Int = 0,
    val testState: ConfigurationTestUiState? = null,
    val alertForDelete: AlertForDelete? = null,
)

@Stable
data class AlertForDelete(
    val size: Int,
    val summary: String,
    val confirm: () -> Unit,
)

@Stable
data class ConfigurationTestUiState(
    val latestResult: ProfileTestResult? = null,
    val processedCount: Int = 0,
    val total: Int = 0,
)

@Stable
data class ProfileTestResult(
    val profile: ProxyEntity,
    val result: TestResult,
)

@Stable
sealed interface TestResult {
    data class Success(val ping: Int) : TestResult
    data class Failure(val reason: FailureReason) : TestResult
}

/** Let UI map tp the R class resource. */
@Stable
sealed interface FailureReason {
    object InvalidConfig : FailureReason
    object DomainNotFound : FailureReason
    object IcmpUnavailable : FailureReason
    object TcpUnavailable : FailureReason
    object ConnectionRefused : FailureReason
    object NetworkUnreachable : FailureReason
    object Timeout : FailureReason
    data class Generic(val message: String?) : FailureReason
    data class PluginNotFound(val message: String) : FailureReason
}

@Stable
enum class TestType {
    ICMPPing,
    TCPPing,
    URLTest,
}

@Stable
class ConfigurationScreenViewModel(val selectCallback: ((id: Long) -> Unit)?) : ViewModel() {


    private val _uiState = MutableStateFlow(ConfigurationFragmentUiState())
    val uiState = _uiState.asStateFlow()

    val selectedGroup = DataStore.configurationStore.longFlow(Key.PROFILE_GROUP)

    private val childViewModels = mutableMapOf<Long, GroupProfilesHolderViewModel>()

    init {
        viewModelScope.launch {
            DataStore.configurationStore.longFlow(Key.PROFILE_GROUP).collect { selectedGroup ->
                _uiState.update { state ->
                    state.copy(selectedGroupIndex = state.groups.indexOfFirst { it.id == selectedGroup })
                }
            }
        }
    }

    fun registerChild(groupId: Long, vm: GroupProfilesHolderViewModel) {
        childViewModels[groupId] = vm
    }

    fun unregisterChild(groupId: Long) {
        childViewModels.remove(groupId)
    }

    fun scrollToProxy(groupId: Long, proxyId: Long, fallbackToTop: Boolean = false) {
        childViewModels[groupId]?.scrollToProxy(proxyId, fallbackToTop)
    }

    fun scrollToProxy(proxyId: Long) = viewModelScope.launch {
        val group = onIoDispatcher {
            ProfileManager.getProfile(proxyId)?.id
        } ?: return@launch
        childViewModels[group]?.scrollToProxy(proxyId, true)
    }

    fun requestFocusIfNotHave(groupId: Long) {
        childViewModels[groupId]?.requestFocusIfNotHave()
    }

    private var testJob: Job? = null

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    fun setSearchQuery(query: String) = viewModelScope.launch {
        _searchQuery.value = query
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun doTest(group: Long, type: TestType) {
        val performTest: suspend (ProxyEntity) -> TestResult = when (type) {
            TestType.ICMPPing -> ::icmpPing
            TestType.TCPPing -> ::tcpPing
            TestType.URLTest -> ::urlTest
        }

        testJob = viewModelScope.launch {
            val proxies = SagerDatabase.proxyDao.getByGroup(group).first()
            val totalCount = proxies.size
            var processedCount = 0
            val concurrent = DataStore.connectionTestConcurrent

            if (proxies.isEmpty()) {
                _uiState.update { state -> state.copy(testState = null) }
                return@launch
            }

            _uiState.update { state ->
                state.copy(
                    testState = ConfigurationTestUiState(
                        total = proxies.size,
                    ),
                )
            }
            val results = mutableListOf<ProfileTestResult>()

            try {
                proxies.asFlow()
                    .flatMapMerge(concurrent) { profile ->
                        flow {
                            val result = onIoDispatcher { performTest(profile) }
                            emit(ProfileTestResult(profile, result))
                        }
                    }
                    .flowOn(Dispatchers.Default)
                    .collect { profileResult ->
                        results.add(profileResult)
                        processedCount++
                        _uiState.update { state ->
                            state.copy(
                                testState = ConfigurationTestUiState(
                                    latestResult = profileResult,
                                    processedCount = processedCount,
                                    total = totalCount,
                                ),
                            )
                        }
                    }
            } finally {
                saveResultsAndFinish(group, results)
            }
        }
    }

    private fun saveResultsAndFinish(group: Long, results: List<ProfileTestResult>) {
        viewModelScope.launch(Dispatchers.IO) {
            results.forEach {
                try {
                    when (val result = it.result) {
                        is TestResult.Success -> {
                            it.profile.ping = result.ping
                            it.profile.status = ProxyEntity.STATUS_AVAILABLE
                            it.profile.error = null
                        }

                        is TestResult.Failure -> {
                            it.profile.ping = 0

                            it.profile.status = when (result.reason) {
                                FailureReason.ConnectionRefused, FailureReason.IcmpUnavailable,
                                FailureReason.NetworkUnreachable, FailureReason.Timeout,
                                    -> ProxyEntity.STATUS_UNREACHABLE

                                is FailureReason.PluginNotFound, FailureReason.DomainNotFound,
                                FailureReason.InvalidConfig,
                                    -> ProxyEntity.STATUS_INVALID

                                is FailureReason.Generic, FailureReason.TcpUnavailable -> ProxyEntity.STATUS_UNAVAILABLE
                            }
                        }
                    }
                    ProfileManager.updateProfile(it.profile)
                } catch (e: Exception) {
                    Logs.e(e)
                }
            }

            onDefaultDispatcher {
                _uiState.update { state -> state.copy(testState = null) }
            }
        }
    }

    fun cancelTest() {
        testJob?.cancel()
        _uiState.update { state ->
            state.copy(testState = null)
        }
    }

    private suspend fun icmpPing(profile: ProxyEntity): TestResult {
        val bean = profile.requireBean()
        if (!bean.canICMPing()) return TestResult.Failure(FailureReason.IcmpUnavailable)

        var address = bean.serverAddress
        if (!address.isIpAddress()) try {
            InetAddress.getAllByName(address)[0]?.let {
                address = it.hostAddress
            }
        } catch (_: UnknownHostException) {
        }
        if (!address.isIpAddress()) {
            return TestResult.Failure(FailureReason.DomainNotFound)
        }

        return try {
            val result = Libcore.icmpPing(address, 5000)
            TestResult.Success(result)
        } catch (e: Exception) {
            Logs.e(e)
            TestResult.Failure(FailureReason.Generic(e.readableMessage))
        }
    }

    private suspend fun tcpPing(profile: ProxyEntity): TestResult {
        val bean = profile.requireBean()
        if (!bean.canTCPing()) return TestResult.Failure(FailureReason.TcpUnavailable)

        var address = bean.serverAddress
        if (!address.isIpAddress()) try {
            InetAddress.getAllByName(address)[0]?.let {
                address = it.hostAddress
            }
        } catch (_: UnknownHostException) {
        }
        if (!address.isIpAddress()) {
            return TestResult.Failure(FailureReason.DomainNotFound)
        }

        return try {
            val result = Libcore.tcpPing(address, bean.serverPort.toString(), 3000)
            TestResult.Success(result)
        } catch (e: Exception) {
            Logs.e(e)
            val message = e.readableMessage
            when {
                message.contains("ECONNREFUSED") -> {
                    TestResult.Failure(FailureReason.ConnectionRefused)
                }

                message.contains("ENETUNREACH") -> {
                    TestResult.Failure(FailureReason.NetworkUnreachable)
                }

                !message.contains("failed:") -> {
                    TestResult.Failure(FailureReason.Timeout)
                }

                else -> TestResult.Failure(FailureReason.Generic(e.readableMessage))
            }
        }
    }

    private suspend fun urlTest(profile: ProxyEntity): TestResult {
        val testURL = DataStore.connectionTestURL
        val testTimeout = DataStore.connectionTestTimeout
        val underVPN = DataStore.serviceMode == Key.MODE_VPN && DataStore.serviceState.started

        return try {
            val result = TestInstance(profile, testURL, testTimeout).doTest(underVPN)
            TestResult.Success(result)
        } catch (e: PluginManager.PluginNotFoundException) {
            TestResult.Failure(FailureReason.PluginNotFound(e.readableMessage))
        } catch (e: Exception) {
            TestResult.Failure(FailureReason.Generic(e.readableMessage))
        }
    }

    private val profileAccess = Mutex()
    private val reloadAccess = Mutex()

    fun onProfileSelect(new: Long) = viewModelScope.launch {
        if (selectCallback != null) {
            selectCallback(new)
            return@launch
        }
        var lastSelected: Long
        var updated: Boolean
        profileAccess.withLock {
            lastSelected = DataStore.selectedProxy
            updated = new != lastSelected
            DataStore.selectedProxy = new
        }
        if (updated) {
            if (DataStore.serviceState.canStop && reloadAccess.tryLock()) {
                repo.reloadService()
                reloadAccess.unlock()
            }
        } else if (repo.isTv) {
            if (DataStore.serviceState.started) {
                repo.stopService()
            } else {
                repo.startService()
            }
        }
        val groupId = DataStore.selectedGroup
        childViewModels[groupId]?.onProfileSelected(new)
    }

    init {
        viewModelScope.launch {
            ProfileManager.getGroups().collect { groups ->
                reloadGroups(groups)
            }
        }
    }

    private suspend fun reloadGroups(all: List<ProxyGroup>?) {
        val groups = (all ?: onIoDispatcher {
            ProfileManager.getGroups().first()
        }).toMutableList()
        if (groups.size > 1) groups.removeFirstMatched {
            it.ungrouped && SagerDatabase.proxyDao.countByGroup(it.id).first() == 0L
        }

        val selectedId = DataStore.currentGroupId()
        var selectIndex = groups.indexOfFirst { it.id == selectedId }

        if (selectIndex < 0) {
            selectIndex = 0
            DataStore.selectedGroup = groups[0].id
        }
        _uiState.emit(
            _uiState.value.copy(
                groups = groups,
                selectedGroupIndex = selectIndex,
            ),
        )
    }

    fun updateOrder(groupId: Long, order: Int) = viewModelScope.launch {
        val group = _uiState.value.groups.find { it.id == groupId } ?: return@launch
        if (group.order == order) return@launch
        runOnIoDispatcher {
            GroupManager.updateGroup(
                group.copy(
                    order = order,
                ),
            )
        }
    }

    fun clearTrafficStatistics(groupId: Long) = viewModelScope.launch {
        val profiles = onIoDispatcher { SagerDatabase.proxyDao.getByGroup(groupId).first() }
        val toClear = profiles.mapNotNull {
            if (it.tx != 0L || it.rx != 0L) {
                it.tx = 0L
                it.rx = 0L
                it
            } else {
                null
            }
        }
        if (toClear.isNotEmpty()) onIoDispatcher {
            SagerDatabase.proxyDao.updateProxy(toClear)
        }
    }

    fun clearResults(groupId: Long) = viewModelScope.launch {
        val profiles = onIoDispatcher { SagerDatabase.proxyDao.getByGroup(groupId).first() }
        val toClear = profiles.mapNotNull {
            if (it.status != ProxyEntity.STATUS_INITIAL) {
                it.status = ProxyEntity.STATUS_INITIAL
                it.ping = 0
                it.error = null
                it
            } else {
                null
            }
        }
        if (toClear.isNotEmpty()) onIoDispatcher {
            SagerDatabase.proxyDao.updateProxy(toClear)
        }
    }

    fun deleteUnavailable(groupId: Long) = viewModelScope.launch {
        val toDelete = onIoDispatcher {
            SagerDatabase.proxyDao.getByGroup(groupId).first().mapNotNull {
                when (it.status) {
                    ProxyEntity.STATUS_INITIAL, ProxyEntity.STATUS_AVAILABLE -> null
                    else -> it
                }
            }
        }
        if (toDelete.isEmpty()) return@launch

        val ids = toDelete.map { it.id }
        _uiState.update { state ->
            state.copy(
                alertForDelete = AlertForDelete(
                    size = toDelete.size,
                    summary = nameSummary(toDelete),
                    confirm = {
                        dismissAlert()
                        runOnIoDispatcher {
                            ProfileManager.deleteProfiles(groupId, ids)
                        }
                    },
                ),
            )
        }
    }

    fun removeDuplicate(groupId: Long) = viewModelScope.launch {
        val profiles = onIoDispatcher {
            SagerDatabase.proxyDao.getByGroup(groupId).first()
        }
        val uniqueProxies = LinkedHashSet<Deduplication>()
        val toDelete = profiles.mapNotNull {
            val bean = it.requireBean()
            val deduplication = Deduplication(bean, bean.javaClass.name)
            if (uniqueProxies.add(deduplication)) {
                null
            } else {
                it
            }
        }
        if (toDelete.isEmpty()) return@launch

        val ids = toDelete.map { it.id }
        _uiState.update { state ->
            state.copy(
                alertForDelete = AlertForDelete(
                    size = toDelete.size,
                    summary = nameSummary(toDelete),
                    confirm = {
                        dismissAlert()
                        runOnIoDispatcher {
                            ProfileManager.deleteProfiles(groupId, ids)
                        }
                    },
                ),
            )
        }
    }

    fun dismissAlert() {
        _uiState.update { it.copy(alertForDelete = null) }
    }

    private fun nameSummary(profiles: List<ProxyEntity>): String {
        return profiles.joinToString(separator = "\n") { it.displayName() }
    }

    fun importFile(
        contentResolver: ContentResolver,
        uri: Uri,
        onProxiesFound: (List<AbstractBean>) -> Unit,
        onSubscriptionFound: (Uri) -> Unit,
        onNoProxies: () -> Unit,
        onError: (String) -> Unit,
    ) = runOnIoDispatcher {
        try {
            val fileName = contentResolver.query(uri, null, null, null, null)
                ?.use { cursor ->
                    cursor.moveToFirst()
                    cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME)
                        .let(cursor::getString)
                }
            val proxies = mutableListOf<AbstractBean>()
            if (fileName != null && fileName.endsWith(".zip")) {
                ZipInputStream(contentResolver.openInputStream(uri)!!).use { zip ->
                    while (true) {
                        val entry = zip.nextEntry ?: break
                        if (entry.isDirectory) continue
                        val fileText = zip.bufferedReader().readText()
                        RawUpdater.parseRaw(fileText, entry.name)?.let { beans ->
                            proxies.addAll(beans)
                        }
                        zip.closeEntry()
                    }
                }
            } else {
                val fileText = contentResolver.openInputStream(uri)!!.use {
                    it.bufferedReader().readText()
                }
                RawUpdater.parseRaw(fileText, fileName ?: "")?.let { beans ->
                    proxies.addAll(beans)
                }
            }
            if (proxies.isEmpty()) {
                onNoProxies()
            } else {
                onProxiesFound(proxies)
            }

        } catch (e: SubscriptionFoundException) {
            onSubscriptionFound(e.link.toUri())
        } catch (e: Exception) {
            Logs.w(e)
            onError(e.readableMessage)
        }
    }

}