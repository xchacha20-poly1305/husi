package fr.husi.ui.dashboard

import android.net.NetworkCapabilities
import fr.husi.repository.androidRepo
import kotlinx.coroutines.delay
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.NetworkInterface

private const val PRIORITY_WIFI = 0
private const val PRIORITY_CELLULAR = 1
private const val PRIORITY_OTHER = 2
private const val PRIORITY_VPN = 3

actual suspend fun buildPlatformNetworkInfo(): Triple<List<NetworkInterfaceInfo>, String?, String?> {
    @Suppress("DEPRECATION") val networks = androidRepo.connectivity.allNetworks.toList().ifEmpty {
        androidRepo.connectivity.activeNetwork?.let(::listOf).orEmpty()
    }
    if (networks.isEmpty()) return Triple(emptyList(), null, null)

    val interfaces = mutableListOf<Pair<Int, Pair<NetworkInterfaceInfo, List<InetAddress>>>>()
    val knownNames = mutableSetOf<String>()

    for (item in networks) {
        val capabilities = androidRepo.connectivity.getNetworkCapabilities(item) ?: continue
        val linkProperties = androidRepo.connectivity.getLinkProperties(item) ?: continue
        val interfaceName = linkProperties.interfaceName ?: continue
        if (!knownNames.add(interfaceName)) continue

        val addressPairs = loadInterfaceAddresses(interfaceName).ifEmpty {
            linkProperties.linkAddresses.map { linkAddress ->
                linkAddress.address to linkAddress.prefixLength.toShort()
            }
        }
        val hosts = addressPairs.map { it.first }
        val addresses = addressPairs.map { (address, prefix) -> address.toPrefix(prefix) }
        val priority = when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> PRIORITY_VPN
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> PRIORITY_WIFI
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> PRIORITY_CELLULAR
            else -> PRIORITY_OTHER
        }
        interfaces += priority to (NetworkInterfaceInfo(
            name = interfaceName,
            addresses = addresses,
        ) to hosts)
    }

    if (interfaces.none { it.first == PRIORITY_WIFI }) {
        findWifiInterfaceFallback(knownNames)?.let { interfaces += PRIORITY_WIFI to it }
    }

    if (interfaces.isEmpty()) return Triple(emptyList(), null, null)

    val sortedInterfaces = interfaces.sortedWith(
        compareBy<Pair<Int, Pair<NetworkInterfaceInfo, List<InetAddress>>>> { it.first }
            .thenBy { it.second.first.name },
    )

    var ipv4: String? = null
    var ipv6: String? = null
    val interfaceInfos = sortedInterfaces.map { it.second.first }
    val interfaceHosts = sortedInterfaces.map { it.second.second }
    interfaceHosts.forEach { hosts ->
        if (ipv4 != null && ipv6 != null) return@forEach
        if (ipv4 == null) {
            ipv4 = hosts.filterIsInstance<Inet4Address>()
                .firstOrNull()
                ?.hostAddress
        }
        if (ipv6 == null) {
            val ipv6Addresses = hosts.filterIsInstance<Inet6Address>()
            ipv6 = ipv6Addresses.firstOrNull { it.isGlobalIPv6() }?.hostAddress
                ?: ipv6Addresses.firstOrNull()?.hostAddress
        }
    }

    return Triple(interfaceInfos, ipv4, ipv6)
}

private suspend fun loadInterfaceAddresses(interfaceName: String): List<Pair<InetAddress, Short>> {
    repeat(10) {
        val addresses = runCatching {
            NetworkInterface.getByName(interfaceName)?.interfaceAddresses?.map { it.address to it.networkPrefixLength }
        }.getOrNull()?.filter { it.first != null }
        if (!addresses.isNullOrEmpty()) return addresses
        delay(100)
    }
    return emptyList()
}

private suspend fun findWifiInterfaceFallback(
    knownNames: MutableSet<String>,
): Pair<NetworkInterfaceInfo, List<InetAddress>>? {
    val networkInterfaces = runCatching { NetworkInterface.getNetworkInterfaces() }.getOrNull()
        ?: return null
    while (networkInterfaces.hasMoreElements()) {
        val netInterface = networkInterfaces.nextElement()
        val name = netInterface.name ?: continue
        val normalizedName = name.lowercase()
        if (!normalizedName.startsWith("wlan") && !normalizedName.startsWith("wifi")) continue
        val active =
            runCatching { netInterface.isUp && !netInterface.isLoopback }.getOrDefault(false)
        if (!active) continue
        if (!knownNames.add(name)) continue
        val addressPairs = loadInterfaceAddresses(name).ifEmpty {
            netInterface.interfaceAddresses.map { it.address to it.networkPrefixLength }
                .filter { it.first != null }
        }
        if (addressPairs.isEmpty()) continue
        val hosts = addressPairs.map { it.first }
        val addresses = addressPairs.map { (address, prefix) -> address.toPrefix(prefix) }
        return NetworkInterfaceInfo(
            name = name,
            addresses = addresses,
        ) to hosts
    }
    return null
}

private fun Inet6Address.isGlobalIPv6(): Boolean {
    val firstByte = address[0].toInt() and 0xFF
    if (firstByte == 0xfc || firstByte == 0xfd) return false // ULA
    return !(isLinkLocalAddress || isSiteLocalAddress || isLoopbackAddress || isAnyLocalAddress)
}

private fun InetAddress.toPrefix(prefix: Short): String {
    return if (this is Inet6Address) {
        "${InetAddress.getByAddress(address).hostAddress}/$prefix"
    } else {
        "$hostAddress/$prefix"
    }
}
