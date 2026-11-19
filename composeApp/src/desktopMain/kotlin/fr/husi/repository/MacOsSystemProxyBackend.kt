package fr.husi.repository

import java.net.InetAddress
import java.net.NetworkInterface

private const val NETWORKSETUP = "networksetup"
private const val LIST_NETWORK_SERVICE_ORDER = "-listnetworkserviceorder"
private const val DEVICE_MARKER = "Device:"
private const val DISABLED_SERVICE_MARKER = "*"

/** Represents an enabled macOS network service and its optional BSD interface name. */
internal data class MacOsNetworkService(
    val name: String,
    val device: String?,
)

internal class MacOsSystemProxyBackend(
    private val runner: SystemCommandRunner = ProcessCommandRunner,
    private val activeDevicesProvider: () -> Set<String> = ::detectActiveNetworkDevices,
) : SystemProxyBackend {

    private val lock = Any()
    private val appliedServices = LinkedHashSet<String>()

    override fun enable(host: String, port: Int) {
        val targets = resolveTargetServices()
        val staleServices = synchronized(lock) { appliedServices.filterNot { it in targets } }
        for (service in staleServices) {
            // Best-effort cleanup so a stale interface failure does not block enabling active ones.
            disableServiceBestEffort(service)
        }
        // Run networksetup sequentially; parallel writes contend on SCPreferencesLock in configd.
        for (service in targets) {
            var tracked = false
            for (args in networksetupEnableArgs(service, host, port)) {
                runner.run(listOf(NETWORKSETUP) + args)
                if (!tracked) {
                    synchronized(lock) {
                        appliedServices += service
                    }
                    tracked = true
                }
            }
        }
    }

    override fun disable() {
        val tracked = synchronized(lock) { appliedServices.toList() }
        val targets = tracked.ifEmpty { resolveTargetServices() }
        var firstError: Exception? = null
        for (service in targets) {
            val error = disableServiceBestEffort(service)
            if (firstError == null) {
                firstError = error
            }
        }
        if (firstError != null) {
            throw firstError
        }
    }

    private fun resolveTargetServices(): List<String> {
        val output = runner.run(listOf(NETWORKSETUP, LIST_NETWORK_SERVICE_ORDER))
        val services = parseNetworkServiceOrder(output)
        return selectTargetNetworkServices(services, activeDevicesProvider())
    }

    private fun disableServiceBestEffort(service: String): Exception? {
        var firstError: Exception? = null
        for (args in networksetupDisableArgs(service)) {
            try {
                runner.run(listOf(NETWORKSETUP) + args)
            } catch (e: Exception) {
                if (firstError == null) {
                    firstError = e
                }
            }
        }
        if (firstError == null) {
            synchronized(lock) {
                appliedServices.remove(service)
            }
        }
        return firstError
    }
}

/**
 * Parses `networksetup -listnetworkserviceorder` output into enabled services and their BSD devices.
 *
 * Disabled entries marked with `(*)` are skipped together with their following detail line.
 */
internal fun parseNetworkServiceOrder(output: String): List<MacOsNetworkService> {
    val result = mutableListOf<MacOsNetworkService>()
    var pendingServiceName: String? = null
    for (rawLine in output.lineSequence()) {
        val line = rawLine.trim()
        if (line.isEmpty() || !line.startsWith('(')) continue
        val closeParen = line.indexOf(')')
        if (closeParen <= 1) continue
        val headerToken = line.substring(1, closeParen).trim()
        if (headerToken == DISABLED_SERVICE_MARKER) {
            if (pendingServiceName != null) {
                result += MacOsNetworkService(name = pendingServiceName, device = null)
                pendingServiceName = null
            }
            continue
        } else if (headerToken.isNotEmpty() && headerToken.all { it in '0'..'9' }) {
            if (pendingServiceName != null) {
                result += MacOsNetworkService(name = pendingServiceName, device = null)
            }
            pendingServiceName = line.substring(closeParen + 1).trim().ifEmpty { null }
        } else if (pendingServiceName != null && line.endsWith(')')) {
            val detail = line.substring(1, line.length - 1)
            val markerIndex = detail.lastIndexOf(DEVICE_MARKER)
            if (markerIndex >= 0) {
                val device = detail.substring(markerIndex + DEVICE_MARKER.length).trim().ifEmpty { null }
                result += MacOsNetworkService(name = pendingServiceName, device = device)
                pendingServiceName = null
            }
        }
    }
    if (pendingServiceName != null) {
        result += MacOsNetworkService(name = pendingServiceName, device = null)
    }
    return result
}

/**
 * Selects enabled services whose BSD device is in [activeDevices], falling back to all enabled
 * services when [activeDevices] is empty or matches no service.
 */
internal fun selectTargetNetworkServices(
    services: List<MacOsNetworkService>,
    activeDevices: Set<String>,
): List<String> {
    if (services.isEmpty()) return emptyList()
    if (activeDevices.isNotEmpty()) {
        val matched = services
            .filter { it.device != null && it.device in activeDevices }
            .map { it.name }
            .distinct()
        if (matched.isNotEmpty()) {
            return matched
        }
    }
    return services.map { it.name }.distinct()
}

/** Returns BSD names of up, non-loopback network interfaces holding at least one routable address. */
internal fun detectActiveNetworkDevices(): Set<String> {
    val interfaces = runCatching { NetworkInterface.getNetworkInterfaces() }.getOrNull() ?: return emptySet()
    val result = LinkedHashSet<String>()
    while (interfaces.hasMoreElements()) {
        val networkInterface = interfaces.nextElement()
        val name = networkInterface.name ?: continue
        val active = runCatching {
            networkInterface.isUp &&
                !networkInterface.isLoopback &&
                hasRoutableAddress(networkInterface.interfaceAddresses.mapNotNull { it.address })
        }.getOrDefault(false)
        if (active) {
            result += name
        }
    }
    return result
}

internal fun hasRoutableAddress(addresses: Iterable<InetAddress>): Boolean {
    return addresses.any {
        !it.isLoopbackAddress &&
            !it.isLinkLocalAddress &&
            !it.isAnyLocalAddress &&
            !it.isMulticastAddress
    }
}

internal fun networksetupEnableArgs(service: String, host: String, port: Int): List<List<String>> {
    val portString = port.toString()
    return listOf(
        listOf("-setwebproxy", service, host, portString),
        listOf("-setsecurewebproxy", service, host, portString),
        listOf("-setsocksfirewallproxy", service, host, portString),
    )
}

internal fun networksetupDisableArgs(service: String): List<List<String>> {
    return listOf(
        listOf("-setwebproxystate", service, "off"),
        listOf("-setsecurewebproxystate", service, "off"),
        listOf("-setsocksfirewallproxystate", service, "off"),
    )
}
