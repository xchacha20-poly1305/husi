package fr.husi.cli

import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.options.option
import fr.husi.APP_NAME
import fr.husi.CLI_STREAM_TIMEOUT
import fr.husi.core.CoreClient
import fr.husi.core.CoreRpcException
import fr.husi.ktx.readableMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import kotlin.concurrent.thread
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

internal class VpnCliException(override val message: String) : Exception(message)

internal sealed class VpnAuthSignal(override val message: String) : Exception(message) {
    class ChallengeWithdrawn :
        VpnAuthSignal("challenge no longer pending, waiting for the next one")

    class DeadlineExpired :
        VpnAuthSignal("challenge deadline expired; the server will retry the connection")

    class StreamFailed(message: String) : VpnAuthSignal(message)
}

internal enum class VpnEndpointPhase {
    Connecting,
    AuthPending,
    Connected,
    Error,
}

internal class VpnEndpointView<E : Any>(
    val tag: String,
    val phase: VpnEndpointPhase,
    /** The phase as the core spells it, printed verbatim. */
    val state: String,
    val error: String,
    val challengeId: String?,
    val endpointState: E,
)

internal interface VpnAuthProtocol<E : Any> {
    /** Lower case protocol name: the `api <name>` command path and the word used in messages. */
    val name: String

    val canceledMessage: String

    fun subscribe(client: CoreClient): Flow<List<VpnEndpointView<E>>>

    fun challengeDeadline(endpointState: E): Long = 0L

    fun describe(block: BlockWriter, endpoint: VpnEndpointView<E>)

    suspend fun answer(
        client: CoreClient,
        prompter: VpnAuthPrompter,
        endpointTag: String,
        endpointState: E,
    )

    suspend fun cancel(client: CoreClient, endpointTag: String, challengeId: String)
}

internal fun endpointOptionHelp(protocolName: String) =
    "$protocolName endpoint tag (default: the only configured endpoint)"

/**
 * Picks the endpoint a command targets: an explicit `--endpoint` tag must exist, otherwise the
 * single configured endpoint is implied.
 */
internal fun <T> resolveVpnEndpoint(
    endpoints: List<T>,
    endpointTag: String?,
    protocolName: String,
    tagOf: (T) -> String,
): T {
    if (!endpointTag.isNullOrEmpty()) {
        return endpoints.firstOrNull { tagOf(it) == endpointTag }
            ?: throw VpnCliException("endpoint not found: $endpointTag")
    }
    return when (endpoints.size) {
        0 -> throw VpnCliException("no $protocolName endpoint is configured")
        1 -> endpoints[0]
        else -> throw VpnCliException(
            "multiple $protocolName endpoints; select one with --endpoint: " +
                endpoints.joinToString(", ") { tagOf(it) },
        )
    }
}

internal class ApiVpnStatusCommand<E : Any>(
    private val protocol: VpnAuthProtocol<E>,
) : ApiClientCommand("status") {
    override fun help(context: Context) = "Print ${protocol.name} endpoint status"

    override fun run() = withClient { client ->
        val endpoints = withTimeout(CLI_STREAM_TIMEOUT) { protocol.subscribe(client).first() }
        if (endpoints.isEmpty()) {
            writeStderrLine("no ${protocol.name} endpoint is configured")
            return@withClient
        }
        for ((index, endpoint) in endpoints.withIndex()) {
            if (index > 0) println()
            val block = BlockWriter()
            block.addLine("Endpoint", endpoint.tag)
            block.addLine("State", endpoint.state)
            protocol.describe(block, endpoint)
            block.flush()
            if (endpoint.challengeId != null) {
                writeStderrLine("")
                writeStderrLine("run \"$APP_NAME api ${protocol.name} auth\" to continue")
            }
        }
    }
}

internal class ApiVpnCancelCommand<E : Any>(
    private val protocol: VpnAuthProtocol<E>,
) : ApiClientCommand("cancel") {
    private val endpointOption by option("--endpoint", help = endpointOptionHelp(protocol.name))

    override fun help(context: Context) = "Cancel the pending ${protocol.name} challenge"

    override fun run() = withClient { client ->
        val endpoints = withTimeout(CLI_STREAM_TIMEOUT) { protocol.subscribe(client).first() }
        val endpoint = resolveVpnEndpoint(endpoints, endpointOption, protocol.name) { it.tag }
        val challengeId = endpoint.challengeId
            ?: throw VpnCliException("no pending authentication challenge on ${endpoint.tag}")
        protocol.cancel(client, endpoint.tag, challengeId)
        println("${endpoint.tag}: ${protocol.canceledMessage}")
    }
}

internal abstract class ApiVpnAuthCommand<E : Any>(
    protected val protocolName: String,
) : ApiClientCommand("auth") {
    private val endpointOption by option("--endpoint", help = endpointOptionHelp(protocolName))

    /** Built after parsing, so it can read this command's options. */
    protected abstract fun protocol(): VpnAuthProtocol<E>

    override fun run() = withClient { client ->
        // No SIGINT handler: Ctrl-C just kills the process and the challenge stays pending in
        // the core. sing-box installs one only because Go needs it to cancel the stream context.
        val protocol = protocol()
        coroutineScope {
            val watcher = VpnStatusWatcher<E>()
            val collector = launch { watcher.collectFrom(protocol.subscribe(client)) }
            try {
                val first = withTimeout(CLI_STREAM_TIMEOUT) { watcher.awaitChange(0L) }
                first.error?.let { throw VpnCliException(it) }
                val endpoint = resolveVpnEndpoint(first.endpoints, endpointOption, protocolName) {
                    it.tag
                }
                if (endpoint.challengeId == null) {
                    when (endpoint.phase) {
                        VpnEndpointPhase.Connected ->
                            throw VpnCliException("endpoint ${endpoint.tag} is already connected")

                        VpnEndpointPhase.Error ->
                            throw VpnCliException("endpoint ${endpoint.tag} failed: ${endpoint.error}")

                        else -> Unit
                    }
                }
                val input = InteractiveConsoleInput().also { it.start() }
                runVpnAuthLoop(client, protocol, watcher, input, endpoint.tag)
            } finally {
                collector.cancel()
            }
        }
    }
}

private suspend fun <E : Any> runVpnAuthLoop(
    client: CoreClient,
    protocol: VpnAuthProtocol<E>,
    watcher: VpnStatusWatcher<E>,
    input: InteractiveConsoleInput,
    endpointTag: String,
) {
    var answeredId = ""
    var waitingPrinted = false
    var revision = 0L
    while (true) {
        val snapshot = watcher.awaitChange(revision)
        snapshot.error?.let { throw VpnCliException(it) }
        revision = snapshot.revision
        val endpoint = snapshot.endpoints.firstOrNull { it.tag == endpointTag }
            ?: throw VpnCliException("endpoint not found: $endpointTag")
        val challengeId = endpoint.challengeId
        if (challengeId == null) {
            when (endpoint.phase) {
                VpnEndpointPhase.Connected -> {
                    println("$endpointTag: connected")
                    return
                }

                VpnEndpointPhase.Error ->
                    throw VpnCliException("endpoint $endpointTag failed: ${endpoint.error}")

                else -> if (!waitingPrinted) {
                    waitingPrinted = true
                    printErrorLine("waiting for an authentication challenge on $endpointTag...")
                }
            }
            continue
        }
        if (challengeId == answeredId) continue
        answeredId = challengeId
        waitingPrinted = false
        try {
            answerVpnChallenge(client, protocol, watcher, input, endpoint, challengeId)
        } catch (e: VpnAuthSignal.ChallengeWithdrawn) {
            printErrorLine(e.message)
        } catch (e: VpnAuthSignal.DeadlineExpired) {
            writeAuthError(protocol.name, e.message)
        } catch (e: VpnAuthSignal.StreamFailed) {
            throw VpnCliException(e.message)
        }
    }
}

private suspend fun <E : Any> answerVpnChallenge(
    client: CoreClient,
    protocol: VpnAuthProtocol<E>,
    watcher: VpnStatusWatcher<E>,
    input: InteractiveConsoleInput,
    endpoint: VpnEndpointView<E>,
    challengeId: String,
) = coroutineScope {
    val prompter = VpnAuthPrompter(input)
    val watchJob = launch {
        watchVpnChallenge(protocol, watcher, endpoint.tag, challengeId, prompter)
    }
    try {
        protocol.answer(client, prompter, endpoint.tag, endpoint.endpointState)
    } finally {
        watchJob.cancel()
    }
}

private suspend fun <E : Any> watchVpnChallenge(
    protocol: VpnAuthProtocol<E>,
    watcher: VpnStatusWatcher<E>,
    endpointTag: String,
    challengeId: String,
    prompter: VpnAuthPrompter,
) {
    var revision = 0L
    while (true) {
        val snapshot = watcher.awaitChange(revision)
        snapshot.error?.let {
            prompter.abort(VpnAuthSignal.StreamFailed(it))
            return
        }
        revision = snapshot.revision
        val endpoint = snapshot.endpoints.firstOrNull { it.tag == endpointTag }
        if (endpoint == null || endpoint.challengeId != challengeId) {
            prompter.abort(VpnAuthSignal.ChallengeWithdrawn())
            return
        }
        val deadline = protocol.challengeDeadline(endpoint.endpointState)
        if (deadline == 0L) continue
        val next = withTimeoutOrNull(vpnRemaining(deadline)) { watcher.awaitChange(revision) }
        if (next == null) {
            prompter.abort(VpnAuthSignal.DeadlineExpired())
            return
        }
        // The new snapshot is re-read from the top: awaitChange(revision) hands it back.
    }
}

internal class VpnStatusWatcher<E : Any> {
    internal data class Snapshot<E : Any>(
        val endpoints: List<VpnEndpointView<E>>,
        val revision: Long,
        val error: String?,
    )

    val snapshots = MutableStateFlow(Snapshot<E>(emptyList(), revision = 0L, error = null))

    /** Mirrors sing-box: collects until the stream ends or fails, then reports a terminal error. */
    suspend fun collectFrom(updates: Flow<List<VpnEndpointView<E>>>) {
        try {
            updates.collect { endpoints ->
                snapshots.update { Snapshot(endpoints, it.revision + 1, error = null) }
            }
            fail("api service closed the status stream")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            fail(e.readableMessage)
        }
    }

    /** The first snapshot newer than [sinceRevision]; returns the current one when it already is. */
    suspend fun awaitChange(sinceRevision: Long): Snapshot<E> =
        snapshots.first { it.revision > sinceRevision }

    private fun fail(message: String) {
        snapshots.update { Snapshot(it.endpoints, it.revision + 1, message) }
    }
}

internal class InteractiveConsoleInput {
    internal class ReadRequest(val prompt: String, val hidden: Boolean) {
        val result = CompletableDeferred<Result<String>>()
    }

    val requests = Channel<ReadRequest>(Channel.RENDEZVOUS)

    fun start() {
        thread(isDaemon = true, name = "cli-auth-input") {
            runBlocking {
                for (request in requests) {
                    System.err.print(request.prompt)
                    request.result.complete(runCatching { readConsoleLine(request.hidden) })
                }
            }
        }
    }

    private fun readConsoleLine(hidden: Boolean): String {
        if (hidden) {
            val console = System.console() ?: throw IOException("no console for hidden input")
            val chars = console.readPassword() ?: throw IOException("stdin closed")
            return String(chars)
        }
        return readlnOrNull() ?: throw IOException("stdin closed")
    }
}

internal class VpnAuthPrompter(private val input: InteractiveConsoleInput) {
    /** Completed at most once; every pending or later prompt then throws the signal. */
    val aborted = CompletableDeferred<VpnAuthSignal>()

    fun abort(signal: VpnAuthSignal) {
        aborted.complete(signal)
    }

    suspend fun read(prompt: String, hidden: Boolean): String {
        val request = InteractiveConsoleInput.ReadRequest(prompt, hidden)
        select<Unit> {
            input.requests.onSend(request) {}
            aborted.onAwait { throw it }
        }
        return select {
            request.result.onAwait { result ->
                result.getOrElse { throw VpnCliException(it.readableMessage) }
            }
            aborted.onAwait { throw it }
        }
    }

    suspend fun promptText(label: String, value: String): String {
        var prompt = label.removeSuffix(":")
        if (value.isNotEmpty()) prompt += " [$value]"
        return read("$prompt: ", hidden = false).ifEmpty { value }
    }

    suspend fun promptPassword(label: String, value: String): String {
        var prompt = label.removeSuffix(":")
        if (value.isNotEmpty()) prompt += " (unchanged)"
        return read("$prompt: ", hidden = true).ifEmpty { value }
    }

    suspend fun promptConfirm(prompt: String): Boolean =
        when (read(prompt, hidden = false).trim().lowercase()) {
            "", "y", "yes" -> true
            else -> false
        }
}

internal fun writeAuthError(protocolName: String, message: String) =
    printErrorLine("$protocolName auth: $message")

internal fun writeAuthHeader(endpointTag: String, title: String) =
    printErrorLine("\n$endpointTag: $title")

internal fun writeAuthBanner(banner: String) {
    for (line in banner.replace("\r\n", "\n").split('\n')) {
        printErrorLine("  $line")
    }
}

internal fun requireAuthTerminal(protocolName: String) {
    if (!isTerminal) {
        throw VpnCliException("$protocolName auth: authentication requires an interactive terminal")
    }
}

internal enum class VpnSubmitOutcome { REJECTED, STALE, FATAL }

private val FATAL_SUBMIT_CODES =
    setOf("Unavailable", "Canceled", "DeadlineExceeded", "Unauthenticated", "Unimplemented")

internal fun classifyVpnSubmitError(e: CoreRpcException): VpnSubmitOutcome = when {
    e.code in FATAL_SUBMIT_CODES -> VpnSubmitOutcome.FATAL
    "no pending" in e.message -> VpnSubmitOutcome.STALE
    else -> VpnSubmitOutcome.REJECTED
}

/**
 * Runs one submission attempt.
 *
 * @return true when the core accepted the answer, false when the user should be prompted again.
 */
internal suspend fun submitVpnResponse(
    protocolName: String,
    rejectedPrefix: String = "submit rejected",
    submit: suspend () -> Unit,
): Boolean {
    try {
        submit()
        return true
    } catch (e: CoreRpcException) {
        when (classifyVpnSubmitError(e)) {
            VpnSubmitOutcome.STALE -> throw VpnAuthSignal.ChallengeWithdrawn()
            VpnSubmitOutcome.FATAL -> throw VpnCliException(e.message)
            VpnSubmitOutcome.REJECTED -> {
                writeAuthError(protocolName, "$rejectedPrefix: ${e.message}")
                return false
            }
        }
    }
}

/** Go's `time.Until(deadline)`; [deadline] is unix seconds. */
internal fun vpnRemaining(deadline: Long): Duration =
    (deadline.seconds - System.currentTimeMillis().milliseconds).coerceAtLeast(Duration.ZERO)

/** ` (<remaining> remaining)`, or "" when the challenge never expires. */
internal fun formatVpnRemainingSuffix(deadline: Long): String {
    if (deadline == 0L) return ""
    return " (${formatGoDuration(vpnRemaining(deadline))} remaining)"
}

/** `<RFC3339> (<elapsed>)` like sing-box; [connectedSince] is unix seconds, "" when unset. */
internal fun formatVpnConnectedSince(connectedSince: Long): String {
    if (connectedSince == 0L) return ""
    val since = connectedSince.seconds
    val elapsed = System.currentTimeMillis().milliseconds - since
    return "${formatApiTime(since.inWholeMilliseconds)} (${formatGoDuration(elapsed)})"
}
