package fr.husi.bg

import fr.husi.database.DataStore
import fr.husi.libcore.ConnectionOwner
import fr.husi.libcore.InterfaceUpdateListener
import fr.husi.libcore.LocalDNSTransport
import fr.husi.libcore.NetworkInterfaceIterator
import fr.husi.libcore.PlatformInterface
import fr.husi.libcore.WIFIState
import java.net.InetAddress

class DesktopPlatformInterface : PlatformInterface {

    override fun anchorSSID(): String {
        return DataStore.anchorSSID
    }

    override fun autoDetectInterfaceControl(fd: Int): Boolean {
        throw UnsupportedOperationException()
    }

    override fun closeDefaultInterfaceMonitor(listener: InterfaceUpdateListener?) {
        throw UnsupportedOperationException()
    }

    override fun deviceName(): String? {
        return InetAddress.getLocalHost().getHostName()
    }

    override fun findConnectionOwner(
        ipProtocol: Int,
        sourceAddress: String?,
        sourcePort: Int,
        destinationAddress: String?,
        destinationPort: Int,
    ): ConnectionOwner? {
        throw UnsupportedOperationException()
    }

    override fun getInterfaces(): NetworkInterfaceIterator? {
        throw UnsupportedOperationException()
    }

    override fun hasCoreFunction() = false

    override fun localDNSTransport(): LocalDNSTransport? {
        throw UnsupportedOperationException()
    }

    override fun onGroupSelectedChange(
        group: String?,
        old: String?,
        now: String?,
    ) {
        TODO("Implement me")
    }

    override fun openTun(): Int {
        throw UnsupportedOperationException()
    }

    override fun readWIFIState(): WIFIState? {
        throw UnsupportedOperationException()
    }

    override fun startDefaultInterfaceMonitor(listener: InterfaceUpdateListener?) {
        throw UnsupportedOperationException()
    }

    override fun useProcFS() = false

}