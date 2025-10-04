package io.nekohasekai.sagernet.ui.dashboard

import android.net.NetworkCapabilities
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.nekohasekai.sagernet.aidl.ISagerNetService
import io.nekohasekai.sagernet.ktx.toPrefix
import io.nekohasekai.sagernet.repository.repo
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.NetworkInterface

internal data class StatusFragmentUiState(
    val memory: Long = 0L,
    val goroutines: Int = 0,
    val selectedClashMode: String? = null,
    val clashModes: List<String> = emptyList(),
    val ipv4: String? = null,
    val ipv6: String? = null,
    val networkInterfaces: List<NetworkInterfaceInfo> = emptyList(),
)

internal data class NetworkInterfaceInfo(
    val name: String,
    val addresses: List<String>,
)

internal class StatusFragmentViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(StatusFragmentUiState())
    val uiState = _uiState.asStateFlow()

    companion object {
        private const val UPDATE_INTERVAL: Long = 1500L
    }

    private var service: ISagerNetService? = null
    private var job: Job? = null

    fun initialize(service: ISagerNetService) {
        this.service = service

        job = viewModelScope.launch {
            while (isActive) {
                updateState()
                delay(UPDATE_INTERVAL)
            }
        }
    }

    private suspend fun updateState() {
        val service = service ?: return
        val (ipv4, ipv6) = getLocalAddresses()
        _uiState.emit(
            StatusFragmentUiState(
                memory = service.queryMemory(),
                goroutines = service.queryGoroutines(),
                selectedClashMode = service.clashMode,
                clashModes = service.clashModes,
                ipv4 = ipv4,
                ipv6 = ipv6,
                networkInterfaces = getInterfaces(),
            )
        )
    }

    fun stop() {
        job?.cancel()
        job = null
        service = null
    }

    /**
     * IPv4 + IPv6
     * @see <a href="https://github.com/chen08209/FlClash/blob/adb890d7637c2d6d10e7034b3599be7eacbfee99/lib/common/utils.dart#L304-L328">FlClash</a>
     * */
    private fun getLocalAddresses(): Pair<String?, String?> {
        val connectivityManager = repo.connectivity

        var wifiIPv4: String? = null
        var wifiIPv6: String? = null
        var cellularIPv4: String? = null
        var cellularIPv6: String? = null

        @Suppress("DEPRECATION")
        connectivityManager.allNetworks.forEach { network ->
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return@forEach
            val linkProperties = connectivityManager.getLinkProperties(network) ?: return@forEach

            linkProperties.linkAddresses.forEach { linkAddress ->
                val address = linkAddress.address
                if (!address.isLoopbackAddress) {
                    if (address is Inet4Address) {
                        if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                            if (wifiIPv4 == null) wifiIPv4 = address.hostAddress
                        } else if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
                            if (cellularIPv4 == null) cellularIPv4 = address.hostAddress
                        }
                    } else if (address is Inet6Address && !address.isLinkLocalAddress) {
                        val hostAddress = address.hostAddress?.substringBefore('%')
                        if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                            if (wifiIPv6 == null) wifiIPv6 = hostAddress
                        } else if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
                            if (cellularIPv6 == null) cellularIPv6 = hostAddress
                        }
                    }
                }
            }
        }

        val finalIPv4 = wifiIPv4 ?: cellularIPv4
        val finalIPv6 = wifiIPv6 ?: cellularIPv6

        return Pair(finalIPv4, finalIPv6)
    }

    private fun getInterfaces(): List<NetworkInterfaceInfo> {
        @Suppress("DEPRECATION") val networks = repo.connectivity.allNetworks
        val networkInterfaces = NetworkInterface.getNetworkInterfaces().toList()
        val interfaces = mutableListOf<NetworkInterfaceInfo>()
        for (network in networks) {
            val name = repo.connectivity.getLinkProperties(network)?.interfaceName ?: continue
            val networkInterface = networkInterfaces.find {
                it.name == name
            } ?: continue
            interfaces.add(
                NetworkInterfaceInfo(
                    name = name,
                    addresses = networkInterface.interfaceAddresses.map {
                        it.toPrefix()
                    },
                )
            )
        }
        return interfaces
    }
}