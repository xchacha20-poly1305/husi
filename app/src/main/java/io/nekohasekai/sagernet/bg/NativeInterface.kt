package io.nekohasekai.sagernet.bg

import android.net.NetworkCapabilities
import android.os.Process
import android.os.Build
import android.os.Build.VERSION.SDK_INT
import android.system.OsConstants
import androidx.annotation.RequiresApi
import io.nekohasekai.sagernet.SagerNet
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.ktx.Logs
import io.nekohasekai.sagernet.ktx.toStringIterator
import io.nekohasekai.sagernet.ktx.mapX
import io.nekohasekai.sagernet.utils.PackageCache
import libcore.InterfaceUpdateListener
import libcore.Libcore
import libcore.NetworkInterfaceIterator
import libcore.PlatformInterface
import libcore.WIFIState
import libcore.NetworkInterface as LibcoreNetworkInterface
import java.net.Inet6Address
import java.net.InetSocketAddress
import java.net.InterfaceAddress
import java.net.NetworkInterface

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

    override fun openTun(): Long {
        if (forTest) throw IllegalArgumentException()
        if (DataStore.vpnService == null) throw NullPointerException("no vpnService")
        return DataStore.vpnService!!.startVpn().toLong()
    }

    override fun useProcFS(): Boolean = SDK_INT < Build.VERSION_CODES.Q

    @RequiresApi(Build.VERSION_CODES.Q)
    override fun findConnectionOwner(
        ipProtocol: Int,
        sourceAddress: String,
        sourcePort: Int,
        destinationAddress: String,
        destinationPort: Int,
    ): Int {
        try {
            val uid = SagerNet.connectivity.getConnectionOwnerUid(
                ipProtocol,
                InetSocketAddress(sourceAddress, sourcePort),
                InetSocketAddress(destinationAddress, destinationPort),
            )
            if (uid == Process.INVALID_UID) error("android: connection owner not found")
            return uid
        } catch (e: Exception) {
            Logs.e(e)
            throw e
        }
    }

    override fun packageNameByUid(uid: Int): String {
        PackageCache.awaitLoadSync()

        if (uid <= 1000L) {
            return "android"
        }

        val packageNames = PackageCache.uidMap[uid]
        if (!packageNames.isNullOrEmpty()) for (packageName in packageNames) {
            return packageName
        }

        error("unknown uid $uid")
    }

    override fun readWIFIState(): WIFIState? {
        // TODO API 34
        @Suppress("DEPRECATION") val wifiInfo = SagerNet.wifi.connectionInfo ?: return null
        var ssid = wifiInfo.ssid
        if (ssid == "<unknown ssid>") return WifiState("", "")
        if (ssid.startsWith("\"") && ssid.endsWith("\"")) {
            ssid = ssid.substring(1, ssid.length - 1)
        }
        return WifiState(ssid, wifiInfo.bssid)
    }

    override fun getInterfaces(): NetworkInterfaceIterator {
        @Suppress("DEPRECATION") val networks = SagerNet.connectivity.allNetworks
        val networkInterfaces = NetworkInterface.getNetworkInterfaces().toList()
        val interfaces = mutableListOf<LibcoreNetworkInterface>()
        for (network in networks) {
            val boxInterface = LibcoreNetworkInterface()
            val linkProperties = SagerNet.connectivity.getLinkProperties(network) ?: continue
            val networkCapabilities =
                SagerNet.connectivity.getNetworkCapabilities(network) ?: continue
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
            boxInterface.addresses = networkInterface.interfaceAddresses.mapX {
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

    override fun isForTest(): Boolean = forTest

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

    private fun InterfaceAddress.toPrefix(): String {
        return if (address is Inet6Address) {
            "${Inet6Address.getByAddress(address.address).hostAddress}/${networkPrefixLength}"
        } else {
            "${address.hostAddress}/${networkPrefixLength}"
        }
    }

    private class WifiState(var mSSID: String, var mBSSID: String) : WIFIState {
        override fun getSSID(): String = mSSID
        override fun getBSSID(): String = mBSSID
    }
}
