package fr.husi.repository

private const val NETWORKSETUP = "networksetup"

internal class MacOsSystemProxyBackend(
    private val runner: SystemCommandRunner = ProcessCommandRunner,
) : SystemProxyBackend {
    override fun enable(host: String, port: Int) {
        forEachNetworkService { service -> networksetupEnableArgs(service, host, port) }
    }

    override fun disable() {
        forEachNetworkService(::networksetupDisableArgs)
    }

    private fun forEachNetworkService(argsForService: (service: String) -> List<List<String>>) {
        val output = runner.run(listOf(NETWORKSETUP, "-listallnetworkservices"))
        for (service in parseNetworkServices(output)) {
            for (args in argsForService(service)) {
                runner.run(listOf(NETWORKSETUP) + args)
            }
        }
    }
}

/** Parses `networksetup -listallnetworkservices`: the first line is a legend, disabled services start with `*`. */
internal fun parseNetworkServices(output: String): List<String> {
    return output.lineSequence()
        .drop(1)
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("*") }
        .toList()
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
