package fr.husi.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.core.requireObject
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.optional
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import fr.husi.APP_NAME
import fr.husi.CLI_STREAM_TIMEOUT
import fr.husi.DesktopMain
import fr.husi.core.CoreClient
import fr.husi.core.CoreRpcException
import fr.husi.libcore.Libcore
import fr.husi.proto.daemon.Connection
import fr.husi.proto.daemon.Group
import fr.husi.proto.daemon.Log
import fr.husi.proto.daemon.ServiceStatus
import fr.husi.proto.daemon.Status
import fr.husi.ui.LogLevel
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.dropWhile
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format.DateTimeComponents
import kotlinx.datetime.format.format
import kotlinx.datetime.offsetAt
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

internal abstract class ApiClientCommand(name: String) : CliktCommand(name) {
    protected val root by requireObject<DesktopMain>()
    private val api: ApiCommand
        get() = generateSequence(currentContext) { it.parent }
            .map { it.command }
            .filterIsInstance<ApiCommand>()
            .first()

    protected fun <T> withClient(block: suspend (CoreClient) -> T): T {
        val base = root.socketBasePath
        val api = api
        val url = api.url.trim()
        val client = try {
            if (url.isEmpty() || url.equals("local", ignoreCase = true)) {
                connectExistingHost(base)
            } else {
                val serverURL = if (url.contains("://")) {
                    url
                } else {
                    "http://$url"
                }
                try {
                    connectRemoteClient(serverURL, api.secret)
                } catch (e: Exception) {
                    echo(
                        "failed to connect to API service at $serverURL: ${e.message}",
                        err = true,
                    )
                    throw ProgramResult(1)
                }
            }
        } catch (e: LinkageError) {
            echo(libcoreLoadFailureMessage(e), err = true)
            throw ProgramResult(1)
        } catch (e: ProgramResult) {
            throw e
        } catch (_: Exception) {
            null
        }
        if (client == null) {
            val sockets = hostSocketPaths(base).joinToString()
            echo("No running $APP_NAME instance (tried: $sockets).", err = true)
            throw ProgramResult(1)
        }
        return try {
            runBlocking {
                try {
                    block(client)
                } catch (e: CoreRpcException) {
                    echo(e.message, err = true)
                    throw ProgramResult(1)
                } catch (e: VpnCliException) {
                    echo(e.message, err = true)
                    throw ProgramResult(1)
                } catch (_: TimeoutCancellationException) {
                    // A wedged stream must not reach the user as a Kotlin stack trace.
                    echo("Timed out waiting for the core host to respond.", err = true)
                    throw ProgramResult(1)
                }
            }
        } finally {
            runCatching { runBlocking { client.close() } }
        }
    }
}

class ApiCommand : CliktCommand("api") {
    val url by option(
        "--url",
        help = "API service URL. Default: local. Env: BOX_API_URL",
        envvar = "BOX_API_URL",
    ).default("", defaultForHelp = "local")

    val secret by option(
        "--secret",
        help = "API service secret. Env: BOX_API_SECRET",
        envvar = "BOX_API_SECRET",
    ).default("")

    init {
        subcommands(
            ApiStatusCommand(),
            ApiVersionCommand(),
            ApiLogsCommand(),
            ApiModeCommand(),
            ApiOutboundsCommand(),
            ApiGroupCommand(),
            ApiConnectionCommand(),
            ApiOpenVPNCommand(),
            ApiOpenConnectCommand(),
        )
    }

    override fun aliases() = mapOf(
        "log" to listOf("logs"),
        "modes" to listOf("mode"),
        "outbound" to listOf("outbounds"),
        "groups" to listOf("group"),
        "connections" to listOf("connection"),
    )

    override fun help(context: Context) = "API service client"

    override fun run() = Unit
}

private class ApiStatusCommand : ApiClientCommand("status") {
    override fun help(context: Context) = "Print the service status"

    override fun run() = withClient { client ->
        val samples = mutableListOf<Status>()
        try {
            withTimeout(CLI_STREAM_TIMEOUT) {
                client.subscribeStatus(1.seconds).take(2).collect { samples += it }
            }
        } catch (_: TimeoutCancellationException) {
            // The first sample already carries everything but the rates.
        }
        val status = samples.lastOrNull()
        if (status == null) {
            echo("The core host sent no status sample.", err = true)
            throw ProgramResult(1)
        }
        val (serviceStatus, startedAt) = coroutineScope {
            val serviceStatusDeferred = async {
                runCatching {
                    withTimeout(CLI_STREAM_TIMEOUT) { client.subscribeServiceStatus().first() }
                }.getOrNull()
            }
            val startedAtDeferred = async {
                runCatching { client.getStartedAt() }.getOrNull()
            }
            serviceStatusDeferred.await() to startedAtDeferred.await()
        }

        val state = serviceStatus?.status?.name?.lowercase().orEmpty()
        val uptime = if (startedAt != null && startedAt > 0L) {
            formatGoDuration((System.currentTimeMillis() - startedAt).milliseconds)
        } else {
            ""
        }
        val connections = if (status.trafficAvailable) {
            "${status.connectionsIn} in / ${status.connectionsOut} out"
        } else {
            "- in / ${status.connectionsOut} out"
        }
        val uplink = if (status.trafficAvailable) {
            "${Libcore.formatBytes(status.uplink)}/s (${Libcore.formatBytes(status.uplinkTotal)} total)"
        } else {
            ""
        }
        val downlink = if (status.trafficAvailable) {
            "${Libcore.formatBytes(status.downlink)}/s (${Libcore.formatBytes(status.downlinkTotal)} total)"
        } else {
            ""
        }
        val block = BlockWriter()
        block.addLine("State", state)
        block.addLine("Uptime", uptime)
        block.addLine("Memory", Libcore.formatMemoryBytes(status.memory))
        block.addLine("Goroutines", status.goroutines.toString())
        block.addLine("Connections", connections)
        block.addLine("Uplink", uplink)
        block.addLine("Downlink", downlink)
        if (serviceStatus?.status == ServiceStatus.Type.FATAL) {
            block.addLine("Error", serviceStatus.errorMessage)
        }
        block.flush()
    }
}

private class ApiVersionCommand : ApiClientCommand("version") {
    override fun help(context: Context) = "Print the API service version"

    override fun run() = withClient { client ->
        val daemonVersion = client.getDaemonVersion()
        println(daemonVersion.version)
        // No mismatch check: Kotlin has no daemon.APIVersion constant to compare against.
        // The husi line is informational, so losing it must not fail a command that already
        // printed its answer.
        val husiVersion = runCatching { client.getVersion() }.getOrNull()
        if (husiVersion != null) {
            writeStderrLine(
                "husi ${husiVersion.version}, sing-box daemon API ${husiVersion.apiVersion}",
            )
        }
    }
}

private class ApiLogsCommand : ApiClientCommand("logs") {
    private val follow by option(
        "-f",
        "--follow",
        help = "Keep printing new log entries until interrupted",
    ).flag()

    private val levelOption by option(
        "--level",
        help = "Print entries at this level or more severe (default: the service log level)",
    )

    private val search by option(
        "--search",
        help = "Print entries containing this text, case-insensitive",
    ).default("")

    override fun help(context: Context) = "Print the service logs"

    override fun run() {
        val explicitLevel = levelOption?.let { parseApiLogLevel(it) }
        withClient { client ->
            val threshold = explicitLevel ?: client.getDefaultLogLevel().levelValue
            val searchQuery = search.trim().lowercase()
            if (follow) {
                // No SIGINT handler: Ctrl-C just kills the process. sing-box installs one only
                // because Go needs it to cancel the stream context.
                var backlog = true
                client.subscribeLog().collect { batch ->
                    printLogBatch(batch, threshold, searchQuery, backlog)
                    backlog = false
                }
            } else {
                val batch = withTimeout(CLI_STREAM_TIMEOUT) {
                    client.subscribeLog().first()
                }
                printLogBatch(batch, threshold, searchQuery, backlog = true)
            }
        }
    }

    private fun parseApiLogLevel(value: String): Int {
        val normalized = value.trim()
        if (normalized.equals("warning", ignoreCase = true)) {
            return LogLevel.WARN.ordinal
        }
        val match = LogLevel.entries.firstOrNull { it.name.equals(normalized, ignoreCase = true) }
        if (match == null) {
            echo("unknown log level: $value", err = true)
            throw ProgramResult(1)
        }
        return match.ordinal
    }
}

private class ApiModeCommand : ApiClientCommand("mode") {
    override val invokeWithoutSubcommand = true

    init {
        subcommands(ApiModeListCommand(), ApiModeSetCommand())
    }

    override fun help(context: Context) = "Print the current clash mode"

    override fun run() {
        if (currentContext.invokedSubcommand != null) return
        withClient { client ->
            val currentMode = client.getClashModeStatus().currentMode.ifEmpty { "-" }
            println(currentMode)
        }
    }
}

private class ApiModeListCommand : ApiClientCommand("list") {
    override fun help(context: Context) = "List clash modes"

    override fun run() = withClient { client ->
        val modes = client.getClashModeStatus().modeListList
        if (modes.isEmpty()) {
            writeStderrLine("no clash modes")
            return@withClient
        }
        print(modes.joinToString("") { "$it\n" })
    }
}

private class ApiModeSetCommand : ApiClientCommand("set") {
    private val mode: String by argument(name = "mode")

    override fun help(context: Context) =
        "Set the clash mode.\n\nThe value is not validated against the mode list: setting an unknown mode reports success."

    override fun run() = withClient { client ->
        client.setClashMode(mode)
    }
}

private class ApiOutboundsCommand : ApiClientCommand("outbounds") {
    override fun help(context: Context) = "List outbounds"

    override fun run() = withClient { client ->
        val outbounds = withTimeout(CLI_STREAM_TIMEOUT) {
            client.subscribeOutbounds().first().outboundsList
        }
        val table = TableWriter(
            header = listOf("TAG", "TYPE", "DELAY"),
            emptyMessage = "no outbounds",
        )
        for (item in outbounds) {
            table.addRow(item.tag, item.type, formatDelay(item.urlTestDelay))
        }
        table.flush()
    }
}

private class ApiGroupCommand : CliktCommand("group") {
    init {
        subcommands(
            ApiGroupListCommand(),
            ApiGroupShowCommand(),
            ApiGroupSelectCommand(),
            ApiGroupUrlTestCommand(),
        )
    }

    override fun help(context: Context) = "Manage outbound groups"

    override fun run() = Unit
}

private class ApiGroupListCommand : ApiClientCommand("list") {
    override fun help(context: Context) = "List outbound groups"

    override fun run() = withClient { client ->
        val groups = fetchGroups(client)
        val table = TableWriter(
            header = listOf("TAG", "TYPE", "SELECTED"),
            emptyMessage = "no groups",
        )
        for (group in groups) {
            table.addRow(group.tag, group.type, group.selected)
        }
        table.flush()
    }
}

private class ApiGroupShowCommand : ApiClientCommand("show") {
    private val groupTag: String by argument(name = "group")

    override fun help(context: Context) = "Show an outbound group"

    override fun run() = withClient { client ->
        val group = fetchGroups(client).firstOrNull { it.tag == groupTag }
        if (group == null) {
            echo("group not found: $groupTag", err = true)
            throw ProgramResult(1)
        }
        val block = BlockWriter()
        block.addLine("Tag", group.tag)
        block.addLine("Type", group.type)
        block.addLine("Selected", group.selected)
        block.flush()
        val table = TableWriter(header = listOf("TAG", "TYPE", "DELAY"))
        for (item in group.itemsList) {
            table.addRow(item.tag, item.type, formatDelay(item.urlTestDelay))
        }
        table.flush()
    }
}

private class ApiGroupSelectCommand : ApiClientCommand("select") {
    private val groupTag: String by argument(name = "group")
    private val outboundTag: String by argument(name = "outbound")

    override fun help(context: Context) = "Select an outbound in a group"

    override fun run() = withClient { client ->
        client.selectOutbound(groupTag, outboundTag)
    }
}

private class ApiGroupUrlTestCommand : ApiClientCommand("urltest") {
    private val groupTag: String by argument(name = "group")

    override fun help(context: Context) =
        "Start a URL test.\n\nThe tests are only spawned: results appear in `outbounds --group <group>` a few seconds later."

    override fun run() = withClient { client ->
        client.daemonUrlTest(groupTag)
    }
}

private class ApiConnectionCommand : CliktCommand("connection") {
    init {
        subcommands(
            ApiConnectionListCommand(),
            ApiConnectionShowCommand(),
            ApiConnectionCloseCommand(),
        )
    }

    override fun help(context: Context) = "Manage connections"

    override fun run() = Unit
}

private class ApiConnectionListCommand : ApiClientCommand("list") {
    private val columnsOption by option(
        "--columns",
        help = "Columns to display (available: ${CONNECTION_COLUMN_NAMES.joinToString(", ")})",
    ).default(DEFAULT_CONNECTION_COLUMNS)

    override fun help(context: Context) = "List open connections"

    override fun run() = withClient { client ->
        val names = columnsOption.split(",")
        val columns = mutableListOf<ConnectionColumn>()
        var sampleRates = false
        for (name in names) {
            val column = ConnectionColumn.fromFlag(name)
            if (column == null) {
                echo(
                    "unknown column: $name, available: ${CONNECTION_COLUMN_NAMES.joinToString(", ")}",
                    err = true,
                )
                throw ProgramResult(1)
            }
            if (column == ConnectionColumn.RATE) {
                sampleRates = true
            }
            columns += column
        }
        val (connections, rates) = if (sampleRates) {
            fetchConnectionsAndRates(client)
        } else {
            fetchConnections(client) to emptyMap()
        }
        val table = TableWriter(
            header = columns.map { it.header },
            emptyMessage = "no connections",
        )
        for (connection in connections) {
            if (connection.closedAt != 0L) continue
            table.addRow(
                *Array(columns.size) { index ->
                    columns[index].value(connection, rates)
                },
            )
        }
        table.flush()
    }
}

private class ApiConnectionShowCommand : ApiClientCommand("show") {
    private val id: String by argument(name = "id")

    override fun help(context: Context) = "Print connection details"

    override fun run() = withClient { client ->
        val connection = fetchConnections(client).firstOrNull { it.id == id }
        if (connection == null) {
            echo("connection not found: $id", err = true)
            throw ProgramResult(1)
        }
        val state = if (connection.closedAt != 0L) "closed" else "open"
        val ipVersion = if (connection.ipVersion != 0) connection.ipVersion.toString() else ""
        val outbound = apiOutboundLabel(connection)
        val block = BlockWriter()
        block.addLine("ID", connection.id)
        block.addLine("State", state)
        block.addLine("Created", formatApiTime(connection.createdAt))
        block.addLine("Closed", formatApiTime(connection.closedAt))
        block.addLine("Network", connection.network)
        block.addLine("IP version", ipVersion)
        block.addLine("Protocol", connection.protocol)
        block.addLine("Inbound", apiInboundLabel(connection))
        block.addLine("Source", connection.source)
        block.addLine("Destination", connection.destination)
        block.addLine("Domain", connection.domain)
        block.addLine("User", connection.user)
        block.addLine("Process", formatProcessInfo(connection))
        block.addLine("Rule", connection.rule)
        block.addLine("Outbound", outbound)
        block.addLine("Chain", connection.chainListList.joinToString(" <- "))
        block.addLine("From outbound", connection.fromOutbound)
        block.addLine("Uplink", Libcore.formatBytes(connection.uplinkTotal))
        block.addLine("Downlink", Libcore.formatBytes(connection.downlinkTotal))
        block.flush()
    }
}

private class ApiConnectionCloseCommand : ApiClientCommand("close") {
    private val id: String? by argument(name = "id").optional()
    private val all by option("--all", help = "Close all connections").flag()

    override fun help(context: Context) =
        "Close connections.\n\nThe id must be a full UUID; the service reports success for an unknown or already closed connection."

    override fun run() {
        if (all && id != null) {
            echo("--all takes no connection id", err = true)
            throw ProgramResult(1)
        }
        if (!all && id == null) {
            echo("missing connection id", err = true)
            throw ProgramResult(1)
        }
        withClient { client ->
            if (all) {
                client.closeAllConnections()
            } else {
                client.closeConnection(id!!)
            }
        }
    }
}

private const val DEFAULT_CONNECTION_COLUMNS =
    "id,network,destination,inbound,outbound,total"

private val CONNECTION_COLUMN_NAMES = ConnectionColumn.entries.map { it.flagName }

private data class ConnectionRate(val uplink: Long, val downlink: Long)

private enum class ConnectionColumn(val flagName: String, val header: String) {
    ID("id", "ID") {
        override fun value(connection: Connection, rates: Map<String, ConnectionRate>) =
            connection.id
    },
    NETWORK("network", "NETWORK") {
        override fun value(connection: Connection, rates: Map<String, ConnectionRate>) =
            connection.network
    },
    SOURCE("source", "SOURCE") {
        override fun value(connection: Connection, rates: Map<String, ConnectionRate>) =
            connection.source
    },
    DESTINATION("destination", "DESTINATION") {
        override fun value(connection: Connection, rates: Map<String, ConnectionRate>) =
            connectionDestination(connection)
    },
    INBOUND("inbound", "INBOUND") {
        override fun value(connection: Connection, rates: Map<String, ConnectionRate>) =
            apiInboundLabel(connection)
    },
    OUTBOUND("outbound", "OUTBOUND") {
        override fun value(connection: Connection, rates: Map<String, ConnectionRate>) =
            connection.outbound
    },
    CHAIN("chain", "CHAIN") {
        override fun value(connection: Connection, rates: Map<String, ConnectionRate>) =
            connection.chainListList.asReversed().joinToString("/")
    },
    RULE("rule", "RULE") {
        override fun value(connection: Connection, rates: Map<String, ConnectionRate>) =
            connection.rule
    },
    PROTOCOL("protocol", "PROTOCOL") {
        override fun value(connection: Connection, rates: Map<String, ConnectionRate>) =
            connection.protocol
    },
    USER("user", "USER") {
        override fun value(connection: Connection, rates: Map<String, ConnectionRate>) =
            connection.user
    },
    PROCESS("process", "PROCESS") {
        override fun value(connection: Connection, rates: Map<String, ConnectionRate>) =
            processColumn(connection)
    },
    CREATED("created", "CREATED") {
        override fun value(connection: Connection, rates: Map<String, ConnectionRate>) =
            formatApiTime(connection.createdAt)
    },
    RATE("rate", "RATE") {
        override fun value(connection: Connection, rates: Map<String, ConnectionRate>): String {
            val rate = rates[connection.id]
            if (rate == null || (rate.uplink == 0L && rate.downlink == 0L)) return ""
            return "↑${Libcore.formatBytes(rate.uplink)}/s ↓${Libcore.formatBytes(rate.downlink)}/s"
        }
    },
    TOTAL("total", "TOTAL") {
        override fun value(connection: Connection, rates: Map<String, ConnectionRate>) =
            "↑${Libcore.formatBytes(connection.uplinkTotal)} ↓${Libcore.formatBytes(connection.downlinkTotal)}"
    },
    ;

    abstract fun value(connection: Connection, rates: Map<String, ConnectionRate>): String

    companion object {
        fun fromFlag(name: String): ConnectionColumn? =
            entries.firstOrNull { it.flagName == name }
    }
}

private fun processColumn(connection: Connection): String {
    if (!connection.hasProcessInfo()) return ""
    val processInfo = connection.processInfo
    if (processInfo.processPath.isNotEmpty()) return processInfo.processPath
    if (processInfo.packageNamesCount > 0) return processInfo.getPackageNames(0)
    return ""
}

private fun printLogBatch(
    batch: Log,
    threshold: Int,
    searchQuery: String,
    backlog: Boolean,
) {
    if (batch.reset && batch.messagesList.isEmpty() && !backlog) {
        writeStderrLine("log buffer cleared")
        return
    }
    val output = buildString {
        for (entry in batch.messagesList) {
            if (entry.levelValue > threshold) continue
            val plainMessage = stripColors(entry.message)
            if (searchQuery.isNotEmpty() && !plainMessage.lowercase().contains(searchQuery)) {
                continue
            }
            append(
                if (stdoutIsTerminal) {
                    entry.message
                } else {
                    plainMessage
                },
            )
            append('\n')
        }
    }
    print(output)
}

private fun formatDelay(delay: Int): String = if (delay <= 0) "" else "$delay ms"

/**
 * Go's `time.RFC3339` layout has no fractional seconds, while the ISO formats print them whenever
 * the instant carries any — and these timestamps are millisecond precision. Truncate so the output
 * matches sing-box.
 */
internal fun formatApiTime(millis: Long): String {
    if (millis == 0L) return ""
    val instant = Instant.fromEpochSeconds(Instant.fromEpochMilliseconds(millis).epochSeconds)
    val offset = TimeZone.currentSystemDefault().offsetAt(instant)
    return DateTimeComponents.Formats.ISO_DATE_TIME_OFFSET.format {
        setDateTimeOffset(instant, offset)
    }
}

/** Go's `time.Duration.String()` truncated to seconds (`1h2m3s`), no spaces. */
internal fun formatGoDuration(duration: Duration): String {
    val totalSeconds = duration.inWholeSeconds.coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return buildString {
        when {
            hours > 0 -> {
                append(hours)
                append('h')
                append(minutes)
                append('m')
                append(seconds)
                append('s')
            }

            minutes > 0 -> {
                append(minutes)
                append('m')
                append(seconds)
                append('s')
            }

            else -> {
                append(seconds)
                append('s')
            }
        }
    }
}

private fun apiInboundLabel(connection: Connection): String {
    if (connection.inbound.isEmpty()) return connection.inboundType
    return "${connection.inboundType}/${connection.inbound}"
}

private fun apiOutboundLabel(connection: Connection): String {
    val outbound = connection.outbound
    return if (outbound.isNotEmpty() && connection.outboundType.isNotEmpty()) {
        "$outbound (${connection.outboundType})"
    } else {
        outbound
    }
}

private fun connectionDestination(connection: Connection): String {
    val destination = connection.destination
    val domain = connection.domain
    if (domain.isEmpty()) return destination
    val portIndex = destination.lastIndexOf(':')
    if (portIndex == -1) return domain
    return domain + destination.substring(portIndex)
}

private fun formatProcessInfo(connection: Connection): String {
    if (!connection.hasProcessInfo()) return ""
    val processInfo = connection.processInfo
    var process = when {
        processInfo.processPath.isNotEmpty() -> processInfo.processPath
        processInfo.packageNamesCount > 0 -> processInfo.getPackageNames(0)
        else -> ""
    }
    process = when {
        process.isEmpty() -> if (processInfo.userId != -1) processInfo.userId.toString() else ""
        processInfo.userName.isNotEmpty() -> "$process (${processInfo.userName})"
        processInfo.userId != -1 -> "$process (${processInfo.userId})"
        else -> process
    }
    return process
}

private suspend fun fetchConnections(client: CoreClient): List<Connection> {
    return withTimeout(CLI_STREAM_TIMEOUT) {
        client.subscribeConnections(1.seconds).first { it.reset }
    }.eventsList.mapNotNull { event ->
        if (event.hasConnection()) event.connection else null
    }.sortedBy { it.createdAt }
}

private suspend fun fetchConnectionsAndRates(
    client: CoreClient,
): Pair<List<Connection>, Map<String, ConnectionRate>> {
    val batches = withTimeout(CLI_STREAM_TIMEOUT) {
        client.subscribeConnections(1.seconds).dropWhile { !it.reset }.take(2).toList()
    }
    val snapshot = batches.firstOrNull() ?: return emptyList<Connection>() to emptyMap()
    val connections = snapshot.eventsList.mapNotNull { event ->
        if (event.hasConnection()) event.connection else null
    }.sortedBy { it.createdAt }
    val rates = batches.getOrNull(1)?.eventsList.orEmpty().associate { event ->
        event.id to ConnectionRate(event.uplinkDelta, event.downlinkDelta)
    }
    return connections to rates
}

private suspend fun fetchGroups(client: CoreClient): List<Group> {
    return withTimeout(CLI_STREAM_TIMEOUT) {
        client.subscribeGroups().first().groupList
    }
}
