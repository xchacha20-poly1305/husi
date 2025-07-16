package io.nekohasekai.sagernet.ui.configuration

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.nekohasekai.sagernet.Key
import io.nekohasekai.sagernet.bg.proto.TestInstance
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.GroupManager
import io.nekohasekai.sagernet.database.ProfileManager
import io.nekohasekai.sagernet.database.ProxyEntity
import io.nekohasekai.sagernet.database.SagerDatabase
import io.nekohasekai.sagernet.ktx.Logs
import io.nekohasekai.sagernet.ktx.isIpAddress
import io.nekohasekai.sagernet.ktx.onIoDispatcher
import io.nekohasekai.sagernet.ktx.onMainDispatcher
import io.nekohasekai.sagernet.ktx.readableMessage
import io.nekohasekai.sagernet.plugin.PluginManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import libcore.Libcore
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.Collections
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.incrementAndFetch

sealed class UiTestState {
    object Idle : UiTestState()

    object Start : UiTestState()

    data class InProgress(
        val latestResult: ProfileTestResult,
        val processedCount: Int,
        val totalCount: Int,
    ) : UiTestState()
}

data class ProfileTestResult(
    val profile: ProxyEntity,
    val result: TestResult,
)

sealed class TestResult {
    data class Success(val ping: Int) : TestResult()
    data class Failure(val reason: FailureReason) : TestResult()
}

/** Let UI map tp the R class resource. */
sealed class FailureReason {
    object InvalidConfig : FailureReason()
    object DomainNotFound : FailureReason()
    object IcmpUnavailable : FailureReason()
    object TcpUnavailable : FailureReason()
    object ConnectionRefused : FailureReason()
    object NetworkUnreachable : FailureReason()
    object Timeout : FailureReason()
    data class Generic(val message: String?) : FailureReason()
    data class PluginNotFound(val message: String) : FailureReason()
}

internal enum class TestType {
    ICMPPing,
    TCPPing,
    URLTest,
}

internal class ConfigurationFragmentViewModel : ViewModel() {
    private val _uiTestState = MutableLiveData<UiTestState>(UiTestState.Idle)
    val uiTestState: LiveData<UiTestState> = _uiTestState

    private var testJob: Job? = null

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
                _uiTestState.value = UiTestState.Idle
                return@launch
            }

            _uiTestState.value = UiTestState.Start
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
                        _uiTestState.value = UiTestState.InProgress(
                            latestResult = profileResult,
                            processedCount = count,
                            totalCount = totalCount
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
                        is TestResult.Success -> it.profile.ping = result.ping
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

            onMainDispatcher {
                _uiTestState.value = UiTestState.Idle
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
}