package fr.husi.ui.dashboard

import fr.husi.fmt.SingBoxOptions
import fr.husi.ktx.splitAddress
import fr.husi.ui.RouteSettingsUiState

internal fun createRouteDraft(
    fields: Set<ConnectionFields>,
    connection: ConnectionDetailState,
): RouteSettingsUiState {
    var domains = ""
    var ip = ""
    var port = ""
    var source = ""
    var sourcePort = ""
    var network = emptySet<String>()
    var protocol = emptySet<String>()
    var packages = emptySet<String>()

    for (field in fields) {
        when (field) {
            ConnectionFields.HOST -> {
                if (connection.host.isNotBlank()) {
                    domains = "full:${connection.host}"
                }
            }

            ConnectionFields.DESTINATION -> {
                splitAddress(connection.dst)?.let { (dstIp, dstPort) ->
                    ip = dstIp
                    port = dstPort
                }
            }

            ConnectionFields.SOURCE -> {
                splitAddress(connection.src)?.let { (srcIp, srcPort) ->
                    source = srcIp
                    sourcePort = srcPort
                }
            }

            ConnectionFields.NETWORK -> {
                if (connection.network.isNotBlank()) {
                    network = setOf(connection.network)
                }
            }

            ConnectionFields.PROTOCOL -> {
                if (connection.protocol != null) {
                    protocol = setOf(connection.protocol)
                }
            }

            ConnectionFields.PROCESS -> {
                if (connection.processes?.isNotEmpty() == true) {
                    packages = connection.processes.toSet()
                }
            }

        }
    }

    return RouteSettingsUiState(
        name = connection.uuid,
        action = SingBoxOptions.ACTION_ROUTE,
        domains = domains,
        ip = ip,
        port = port,
        source = source,
        sourcePort = sourcePort,
        network = network,
        protocol = protocol,
        packages = packages,
    )
}

