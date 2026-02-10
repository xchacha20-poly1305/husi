package fr.husi.ui.dashboard

import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.NetworkInterface

actual suspend fun buildPlatformNetworkInfo(): Triple<List<NetworkInterfaceInfo>, String?, String?> {
    val interfaces = mutableListOf<Pair<NetworkInterfaceInfo, List<InetAddress>>>()
    val networkInterfaces = runCatching { NetworkInterface.getNetworkInterfaces() }.getOrNull()
        ?: return Triple(emptyList(), null, null)

    while (networkInterfaces.hasMoreElements()) {
        val netInterface = networkInterfaces.nextElement()
        val name = netInterface.name ?: continue
        val active =
            runCatching { netInterface.isUp && !netInterface.isLoopback }.getOrDefault(false)
        if (!active) continue
        val addressPairs = netInterface.interfaceAddresses
            .map { it.address to it.networkPrefixLength }
            .filter { it.first != null }
        if (addressPairs.isEmpty()) continue
        val hosts = addressPairs.map { it.first }
        val addresses = addressPairs.map { (address, prefix) ->
            if (address is Inet6Address) {
                "${InetAddress.getByAddress(address.address).hostAddress}/$prefix"
            } else {
                "${address.hostAddress}/$prefix"
            }
        }
        interfaces += NetworkInterfaceInfo(
            name = name,
            addresses = addresses,
        ) to hosts
    }

    if (interfaces.isEmpty()) return Triple(emptyList(), null, null)

    var ipv4: String? = null
    var ipv6: String? = null
    val interfaceInfos = interfaces.map { it.first }
    interfaces.forEach { (_, hosts) ->
        if (ipv4 != null && ipv6 != null) return@forEach
        if (ipv4 == null) {
            ipv4 = hosts.filterIsInstance<Inet4Address>()
                .firstOrNull()
                ?.hostAddress
        }
        if (ipv6 == null) {
            ipv6 = hosts.filterIsInstance<Inet6Address>()
                .firstOrNull { addr ->
                    val firstByte = addr.address[0].toInt() and 0xFF
                    firstByte != 0xfc && firstByte != 0xfd &&
                        !addr.isLinkLocalAddress && !addr.isSiteLocalAddress &&
                        !addr.isLoopbackAddress && !addr.isAnyLocalAddress
                }?.hostAddress
                ?: hosts.filterIsInstance<Inet6Address>().firstOrNull()?.hostAddress
        }
    }

    return Triple(interfaceInfos, ipv4, ipv6)
}
