package fr.husi.repository

import java.net.InetAddress
import java.net.NetworkInterface

internal data class MacOsProxyService(
    val device: String?,
    val enabled: Boolean,
    val inCurrentSet: Boolean,
    val values: Map<String, Any>,
)

internal interface MacOsProxyPreferences {
    fun update(
        trackedServices: Set<String>,
        applyPending: Boolean,
        changes: (Map<String, MacOsProxyService>) -> Map<String, Map<String, Any>>,
        committed: (needsApply: Boolean) -> Unit,
    )
}

internal class MacOsSystemProxyBackend(
    private val preferences: MacOsProxyPreferences = MacOsSystemConfiguration,
    private val activeDevicesProvider: () -> Set<String> = ::detectActiveNetworkDevices,
) : SystemProxyBackend {

    private var appliedServices = emptySet<String>()
    private var applyPending = false

    override val needsCleanup: Boolean
        get() = appliedServices.isNotEmpty() || applyPending

    override fun enable(host: String, port: Int) {
        val activeDevices = activeDevicesProvider()
        val requested = buildMap {
            for ((enableKey, hostKey, portKey) in macOsProxyKeys) {
                put(enableKey, 1)
                put(hostKey, host)
                put(portKey, port)
            }
        }
        var targets = emptySet<String>()
        preferences.update(
            trackedServices = appliedServices,
            applyPending = applyPending,
            changes = { services ->
                val eligible = services.filterValues { it.inCurrentSet && it.enabled }
                targets = eligible.filterValues { it.device in activeDevices }.keys.ifEmpty { eligible.keys }
                buildMap {
                    for ((id, service) in services) {
                        val fields = when (id) {
                            in targets -> requested.filter { (key, value) ->
                                service.values[key] != value
                            }

                            in appliedServices -> disabledFields(service)

                            else -> continue
                        }
                        if (fields.isNotEmpty()) {
                            put(id, fields)
                        }
                    }
                }
            },
            committed = { needsApply ->
                appliedServices = targets
                applyPending = needsApply
            },
        )
        applyPending = false
    }

    override fun disable() {
        if (!needsCleanup) return
        preferences.update(
            trackedServices = appliedServices,
            applyPending = applyPending,
            changes = { services ->
                buildMap {
                    for (id in appliedServices) {
                        val fields = services[id]?.let(::disabledFields).orEmpty()
                        if (fields.isNotEmpty()) {
                            put(id, fields)
                        }
                    }
                }
            },
            committed = { needsApply ->
                appliedServices = emptySet()
                applyPending = needsApply
            },
        )
        applyPending = false
    }

    private fun disabledFields(service: MacOsProxyService): Map<String, Int> {
        return buildMap {
            for ((enableKey) in macOsProxyKeys) {
                val enabled = service.values[enableKey]
                if (enabled != null && enabled != 0) {
                    put(enableKey, 0)
                }
            }
        }
    }
}

internal val macOsProxyKeys = listOf("HTTP", "HTTPS", "SOCKS").map {
    listOf("${it}Enable", "${it}Proxy", "${it}Port")
}

/** Returns BSD names of up, non-loopback network interfaces holding at least one routable address. */
internal fun detectActiveNetworkDevices(): Set<String> {
    val interfaces = runCatching { NetworkInterface.getNetworkInterfaces() }.getOrNull() ?: return emptySet()
    val result = LinkedHashSet<String>()
    while (interfaces.hasMoreElements()) {
        val networkInterface = interfaces.nextElement()
        val name = networkInterface.name ?: continue
        val active = runCatching {
            networkInterface.isUp
                && !networkInterface.isLoopback
                && hasRoutableAddress(networkInterface.interfaceAddresses.mapNotNull { it.address })
        }.getOrDefault(false)
        if (active) {
            result += name
        }
    }
    return result
}

internal fun hasRoutableAddress(addresses: Iterable<InetAddress>): Boolean {
    return addresses.any {
        !it.isLoopbackAddress
            && !it.isLinkLocalAddress
            && !it.isAnyLocalAddress
            && !it.isMulticastAddress
    }
}
