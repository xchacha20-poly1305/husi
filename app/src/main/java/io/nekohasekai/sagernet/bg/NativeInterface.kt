package io.nekohasekai.sagernet.bg

import android.net.NetworkCapabilities
import android.os.Build
import android.os.Build.VERSION.SDK_INT
import android.os.Process
import android.system.OsConstants
import androidx.annotation.RequiresApi
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.ktx.Logs
import io.nekohasekai.sagernet.ktx.toPrefix
import io.nekohasekai.sagernet.ktx.toStringIterator
import io.nekohasekai.sagernet.repository.repo
import io.nekohasekai.sagernet.utils.PackageCache
import libcore.ConnectionOwner
import libcore.InterfaceUpdateListener
import libcore.Libcore
import libcore.LocalDNSTransport
import libcore.NetworkInterfaceIterator
import libcore.PlatformInterface
import libcore.WIFIState
import java.net.InetSocketAddress
import java.net.NetworkInterface
import libcore.NetworkInterface as LibcoreNetworkInterface

class NativeInterface(val forTest: Boolean) : PlatformInterface {

    //  libbox interface

    override fun anchorSSID(): String = DataStore.anchorSSID

    override fun autoDetectInterfaceControl(fd: Int): Boolean {
        return DataStore.vpnService?.protect(fd) == true
    }

    override fun startDefaultInterfaceMonitor(listener: InterfaceUpdateListener) {
        if (forTest) throw IllegalArgumentException()
        DefaultNetworkMonitor.setListener(listener)
    }

    override fun closeDefaultInterfaceMonitor(listener: InterfaceUpdateListener) {
        if (forTest) throw IllegalArgumentException()
        DefaultNetworkMonitor.setListener(null)
    }

    override fun deviceName(): String = Build.MODEL

    override fun openTun(): Int {
        if (forTest) throw IllegalArgumentException()
        if (DataStore.vpnService == null) throw NullPointerException("no vpnService")
        return DataStore.vpnService!!.startVpn()
    }

    override fun useProcFS(): Boolean = SDK_INT < Build.VERSION_CODES.Q

    @RequiresApi(Build.VERSION_CODES.Q)
    override fun findConnectionOwner(
        ipProtocol: Int,
        sourceAddress: String,
        sourcePort: Int,
        destinationAddress: String,
        destinationPort: Int,
    ): ConnectionOwner {
        try {
            val uid = repo.connectivity.getConnectionOwnerUid(
                ipProtocol,
                InetSocketAddress(sourceAddress, sourcePort),
                InetSocketAddress(destinationAddress, destinationPort),
            )
            if (uid == Process.INVALID_UID) error("android: connection owner not found")
            PackageCache.awaitLoadSync()
            val packages = PackageCache.uidMap[uid]
            return ConnectionOwner(uid, packages?.firstOrNull().orEmpty())
        } catch (e: Exception) {
            Logs.e(e)
            throw e
        }
    }

    override fun readWIFIState(): WIFIState? {
        // TODO API 34
        @Suppress("DEPRECATION") val wifiInfo = repo.wifi.connectionInfo ?: return null
        var ssid = wifiInfo.ssid
        if (ssid == "<unknown ssid>") return WifiState("", "")
        if (ssid.startsWith("\"") && ssid.endsWith("\"")) {
            ssid = ssid.substring(1, ssid.length - 1)
        }
        return WifiState(ssid, wifiInfo.bssid)
    }

    override fun getInterfaces(): NetworkInterfaceIterator {
        @Suppress("DEPRECATION") val networks = repo.connectivity.allNetworks
        val networkInterfaces = NetworkInterface.getNetworkInterfaces().toList()
        val interfaces = mutableListOf<LibcoreNetworkInterface>()
        for (network in networks) {
            val boxInterface = LibcoreNetworkInterface()
            val linkProperties = repo.connectivity.getLinkProperties(network) ?: continue
            val networkCapabilities = repo.connectivity.getNetworkCapabilities(network) ?: continue
            boxInterface.name = linkProperties.interfaceName
            val networkInterface =
                networkInterfaces.find { it.name == boxInterface.name } ?: continue
            boxInterface.dnsServer = linkProperties.dnsServers.mapNotNull { it.hostAddress }.let {
                it.toStringIterator(it.size)
            }
            boxInterface.type = when {
                networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> Libcore.InterfaceTypeWIFI
                networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> Libcore.InterfaceTypeCellular
                networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> Libcore.InterfaceTypeEthernet
                else -> Libcore.InterfaceTypeOther
            }
            boxInterface.index = networkInterface.index
            runCatching {
                boxInterface.mtu = networkInterface.mtu
            }.onFailure { e ->
                Logs.e("failed to get mtu for interface ${boxInterface.name}", e)
            }
            boxInterface.addresses = networkInterface.interfaceAddresses.map {
                it.toPrefix()
            }.let {
                it.toStringIterator(it.size)
            }
            var dumpFlags = 0
            if (networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
                dumpFlags = OsConstants.IFF_UP or OsConstants.IFF_RUNNING
            }
            if (networkInterface.isLoopback) {
                dumpFlags = dumpFlags or OsConstants.IFF_LOOPBACK
            }
            if (networkInterface.isPointToPoint) {
                dumpFlags = dumpFlags or OsConstants.IFF_POINTOPOINT
            }
            if (networkInterface.supportsMulticast()) {
                dumpFlags = dumpFlags or OsConstants.IFF_MULTICAST
            }
            boxInterface.flags = dumpFlags
            boxInterface.metered =
                !networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
            interfaces.add(boxInterface)
        }
        return InterfaceArray(interfaces.iterator(), interfaces.size)
    }

    override fun localDNSTransport(): LocalDNSTransport = LocalResolver

    override fun onGroupSelectedChange(group: String, old: String, now: String) {
        DataStore.baseService?.data?.proxy?.trafficLooper?.updateSelectedTag(group, old, now)
    }

    private class InterfaceArray(
        private val iterator: Iterator<LibcoreNetworkInterface>,
        private val size: Int,
    ) :
        NetworkInterfaceIterator {

        override fun hasNext(): Boolean {
            return iterator.hasNext()
        }

        override fun next(): LibcoreNetworkInterface {
            return iterator.next()
        }

        override fun length(): Int = size

    }

    private class WifiState(val mSSID: String, val mBSSID: String) : WIFIState {
        override fun getSSID(): String = mSSID
        override fun getBSSID(): String = mBSSID
    }
}
