package io.nekohasekai.sagernet.ui.configuration

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.preference.PreferenceDataStore
import io.nekohasekai.sagernet.Key
import io.nekohasekai.sagernet.SagerNet
import io.nekohasekai.sagernet.aidl.TrafficData
import io.nekohasekai.sagernet.bg.proto.TestInstance
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.GroupManager
import io.nekohasekai.sagernet.database.ProfileManager
import io.nekohasekai.sagernet.database.ProxyEntity
import io.nekohasekai.sagernet.database.ProxyGroup
import io.nekohasekai.sagernet.database.SagerDatabase
import io.nekohasekai.sagernet.database.preference.OnPreferenceDataStoreChangeListener
import io.nekohasekai.sagernet.ktx.Logs
import io.nekohasekai.sagernet.ktx.isIpAddress
import io.nekohasekai.sagernet.ktx.onDefaultDispatcher
import io.nekohasekai.sagernet.ktx.onIoDispatcher
import io.nekohasekai.sagernet.ktx.readableMessage
import io.nekohasekai.sagernet.ktx.removeFirstMatched
import io.nekohasekai.sagernet.ktx.runOnIoDispatcher
import io.nekohasekai.sagernet.plugin.PluginManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
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
import java.util.Collections
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.incrementAndFetch

internal data class ConfigurationFragmentUiState(
    val groups: List<ProxyGroup> = emptyList(),
    val selectedGroupIndex: Int = 0,
    val testState: ConfigurationTestUiState? = null,
)

internal data class ConfigurationTestUiState(
    val latestResult: ProfileTestResult? = null,
    val processedCount: Int = 0,
    val total: Int = 0,
)

internal data class ProfileTestResult(
    val profile: ProxyEntity,
    val result: TestResult,
)

internal sealed interface TestResult {
    data class Success(val ping: Int) : TestResult
    data class Failure(val reason: FailureReason) : TestResult
}

/** Let UI map tp the R class resource. */
internal sealed interface FailureReason {
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

internal enum class TestType {
    ICMPPing,
    TCPPing,
    URLTest,
}

internal sealed class ConfigurationChildEvent(open val group: Long) {
    class ScrollToProxy(override val group: Long, val id: Long) : ConfigurationChildEvent(group)
    class RequestFocusIfNotHave(override val group: Long) : ConfigurationChildEvent(group)
    class ClearTrafficStatistic(override val group: Long) : ConfigurationChildEvent(group)
    class ClearResult(override val group: Long) : ConfigurationChildEvent(group)
    class DeleteUnavailable(override val group: Long) : ConfigurationChildEvent(group)
    class RemoveDuplicate(override val group: Long) : ConfigurationChildEvent(group)
    class OnProfileSelect(override val group: Long, val new: Long) : ConfigurationChildEvent(group)
    class UpdateOrder(override val group: Long, val order: Int) : ConfigurationChildEvent(group)
}


internal class ConfigurationFragmentViewModel : ViewModel(),
    ProfileManager.Listener, GroupManager.Listener,
    OnPreferenceDataStoreChangeListener {

    init {
        ProfileManager.addListener(this)
        GroupManager.addListener(this)
        DataStore.profileCacheStore.registerChangeListener(this)
    }

    override fun onCleared() {
        ProfileManager.removeListener(this)
        GroupManager.removeListener(this)
        DataStore.profileCacheStore.unregisterChangeListener(this)
        super.onCleared()
    }

    private val _uiState = MutableStateFlow(ConfigurationFragmentUiState())
    val uiState = _uiState.asStateFlow()

    private val _childEvent = MutableSharedFlow<ConfigurationChildEvent>()
    val childEvent = _childEvent.asSharedFlow()

    suspend fun emitChildEvent(event: ConfigurationChildEvent) {
        _childEvent.emit(event)
    }

    var forSelect: Boolean = false
    var selectedItem: ProxyEntity? = null

    @get:StringRes
    var titleRes: Int = 0

    private var testJob: Job? = null

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    fun setSearchQuery(query: String) = viewModelScope.launch {
        _searchQuery.value = query
    }

    @OptIn(ExperimentalAtomicApi::class, ExperimentalCoroutinesApi::class)
    fun doTest(group: Long, type: TestType) {
        val performTest: suspend (ProxyEntity) -> TestResult = when (type) {
            TestType.ICMPPing -> ::icmpPing
            TestType.TCPPing -> ::tcpPing
            TestType.URLTest -> ::urlTest
        }

        testJob = viewModelScope.launch {
            val proxies = SagerDatabase.proxyDao.getByGroup(group)
            val totalCount = proxies.size
            val processedCount = AtomicInt(0)
            val concurrent = DataStore.connectionTestConcurrent

            if (proxies.isEmpty()) {
                _uiState.emit(_uiState.value.copy(testState = null))
                return@launch
            }

            _uiState.emit(
                _uiState.value.copy(
                    testState = ConfigurationTestUiState(
                        total = proxies.size,
                    )
                )
            )
            val results = Collections.synchronizedList(mutableListOf<ProfileTestResult>())

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
                        val count = processedCount.incrementAndFetch()
                        _uiState.emit(
                            _uiState.value.copy(
                                testState = ConfigurationTestUiState(
                                    latestResult = profileResult,
                                    processedCount = count,
                                    total = totalCount
                                )
                            )
                        )
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
                                FailureReason.NetworkUnreachable, FailureReason.Timeout -> ProxyEntity.STATUS_UNREACHABLE

                                is FailureReason.PluginNotFound, FailureReason.DomainNotFound,
                                FailureReason.InvalidConfig -> ProxyEntity.STATUS_INVALID

                                is FailureReason.Generic, FailureReason.TcpUnavailable -> ProxyEntity.STATUS_UNAVAILABLE
                            }
                        }
                    }
                    ProfileManager.updateProfile(it.profile)
                } catch (e: Exception) {
                    Logs.e(e)
                }
            }
            GroupManager.postReload(group)

            onDefaultDispatcher {
                _uiState.emit(_uiState.value.copy(testState = null))
            }
        }
    }

    fun cancelTest() {
        testJob?.cancel()
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

    fun onSelect(id: Long) = runOnIoDispatcher {
        DataStore.selectedGroup = id

        viewModelScope.launch {
            _uiState.update { state ->
                state.copy(selectedGroupIndex = state.groups.indexOfFirst { it.id == id })
            }
        }
    }

    private val profileAccess = Mutex()
    private val reloadAccess = Mutex()

    fun onProfileSelect(new: Long) = viewModelScope.launch {
        var lastSelected: Long
        var updated: Boolean
        profileAccess.withLock {
            lastSelected = DataStore.selectedProxy
            updated = new != lastSelected
            DataStore.selectedProxy = new
        }
        if (updated) {
            ProfileManager.postUpdate(lastSelected)
            if (DataStore.serviceState.canStop && reloadAccess.tryLock()) {
                SagerNet.reloadService()
                reloadAccess.unlock()
            }
        } else if (SagerNet.isTv) {
            if (DataStore.serviceState.started) {
                SagerNet.stopService()
            } else {
                SagerNet.startService()
            }
        }
        emitChildEvent(ConfigurationChildEvent.OnProfileSelect(DataStore.selectedGroup,new))
    }

    init {
        viewModelScope.launch {
            reloadGroups()
        }
    }

    private suspend fun reloadGroups() {
        var all = SagerDatabase.groupDao.allGroups().toMutableList()
        if (all.isEmpty()) {
            SagerDatabase.groupDao.createGroup(ProxyGroup(ungrouped = true))
            all = SagerDatabase.groupDao.allGroups().toMutableList()
        }
        if (all.size > 1) all.removeFirstMatched {
            it.ungrouped && SagerDatabase.proxyDao.countByGroup(it.id) == 0L
        }

        val selectedId = DataStore.currentGroupId()
        var selectIndex = all.indexOfFirst { it.id == selectedId }

        if (selectIndex < 0) {
            selectIndex = 0
            DataStore.selectedGroup = all[0].id
        }
        _uiState.emit(
            _uiState.value.copy(
                groups = all,
                selectedGroupIndex = selectIndex
            )
        )
    }

    override suspend fun onAdd(profile: ProxyEntity) {
        if (!_uiState.value.groups.any { it.id == profile.groupId }) {
            DataStore.selectedGroup = profile.groupId
            reloadGroups()
        }
    }

    override suspend fun onUpdated(data: TrafficData) {}

    override suspend fun onUpdated(profile: ProxyEntity) {}

    override suspend fun onRemoved(groupId: Long, profileId: Long) {
        val group = _uiState.value.groups.find { it.id == groupId } ?: return
        if (group.ungrouped && SagerDatabase.proxyDao.countByGroup(groupId) == 0L) {
            reloadGroups()
        }
    }

    override suspend fun groupAdd(group: ProxyGroup) {
        DataStore.selectedGroup = group.id
        _uiState.update { state ->
            val groups = state.groups + group
            state.copy(groups = groups, selectedGroupIndex = groups.lastIndex)
        }
    }

    override suspend fun groupUpdated(group: ProxyGroup) {
        _uiState.update { state ->
            val groups = state.groups.toMutableList()
            val index = groups.indexOfFirst { it.id == group.id }
            groups[index] = group
            state.copy(groups = groups)
        }
    }

    override suspend fun groupRemoved(groupId: Long) {
        _uiState.update { state ->
            val groups = state.groups.toMutableList()
            groups.removeFirstMatched { it.id == groupId }
            state.copy(groups = groups)
        }
    }

    override suspend fun groupUpdated(groupId: Long) {}

    override fun onPreferenceDataStoreChanged(store: PreferenceDataStore, key: String) {
        if (key != Key.PROFILE_GROUP) return
        // editing group
        viewModelScope.launch {
            val targetID = DataStore.editingGroup
            if (targetID > 0 && targetID != DataStore.selectedGroup) {
                DataStore.selectedGroup = targetID
                reloadGroups()
            }
        }
    }
}