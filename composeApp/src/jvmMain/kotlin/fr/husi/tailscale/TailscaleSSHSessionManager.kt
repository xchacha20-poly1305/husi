package fr.husi.tailscale

import fr.husi.core.remote.CoreNotRunningException
import fr.husi.core.remote.CoreTarget
import fr.husi.ktx.readableMessage
import fr.husi.proto.daemon.TailscaleSSHClientMessage
import fr.husi.proto.daemon.TailscaleSSHInput
import fr.husi.proto.daemon.TailscaleSSHResize
import fr.husi.proto.daemon.TailscaleSSHServerMessage
import fr.husi.proto.daemon.TailscaleSSHStart
import fr.husi.repository.resolveRepository
import fr.husi.resources.Res
import fr.husi.resources.terminal_press_any_key
import fr.husi.resources.terminal_session_ended
import io.github.xchacha20_poly1305.libghostty.GhosttyTerminalSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okio.ByteString.Companion.toByteString

class TailscaleSSHSession internal constructor(
    override val id: Long,
    override val target: TailscaleSSHTarget,
    override val terminal: GhosttyTerminalSession,
) : TailscaleSSHSessionView {
    override val phase: StateFlow<TailscaleSSHPhase>
        field = MutableStateFlow(TailscaleSSHPhase.CONNECTING)

    /** The last authentication banner the peer sent, such as a check-mode login URL. */
    override val banner: StateFlow<String?>
        field = MutableStateFlow(null)

    internal val requests = Channel<TailscaleSSHClientMessage>(Channel.UNLIMITED)
    internal var job: Job? = null

    internal suspend fun handle(message: TailscaleSSHServerMessage) {
        message.authBanner?.let { banner.value = it.message }
        message.ready?.let { phase.value = TailscaleSSHPhase.RUNNING }
        message.output?.let { terminal.feedOutput(it.data_.toByteArray()) }
        message.exit?.let { finish(it.exitCode, it.signal, it.errorMessage) }
        message.error?.let {
            finish(exitCode = SESSION_FAILED, signal = "", errorMessage = it.message)
        }
    }

    internal suspend fun finish(exitCode: Int, signal: String, errorMessage: String) {
        if (terminal.isFinished) return
        requests.close()
        phase.value = TailscaleSSHPhase.FINISHED
        terminal.feedOutput(exitNotice(exitCode, signal, errorMessage).encodeToByteArray())
        terminal.finish(exitCode)
    }

    private suspend fun exitNotice(exitCode: Int, signal: String, errorMessage: String): String {
        val repository = resolveRepository()
        val ended = repository.getString(Res.string.terminal_session_ended)
        val pressAnyKey = repository.getString(Res.string.terminal_press_any_key)
        return buildString {
            append("\r\n[")
            append(ended)
            if (errorMessage.isNotEmpty()) {
                append(": ").append(errorMessage)
            } else if (exitCode != 0) {
                append(" (exit ").append(exitCode).append(")")
            }
            if (signal.isNotEmpty()) {
                append(" (signal ").append(signal).append(")")
            }
            append(" - ")
            append(pressAnyKey)
            append("]")
        }
    }

    internal companion object {
        /** The exit status of a session that ended without the peer reporting one. */
        const val SESSION_FAILED = -1
    }
}

data class TailscaleSSHState(
    val sessions: List<TailscaleSSHSession> = emptyList(),
    val activeSessionId: Long? = null,
) {
    val activeSession: TailscaleSSHSession?
        get() = sessions.firstOrNull { it.id == activeSessionId }
}

/**
 * Holds the Tailscale SSH sessions of the whole process, so that they survive the activity
 * showing them being recreated; that activity, or the desktop window, closes them all when the
 * user leaves it. A session ends when it is closed or when its SSH connection ends; an ended
 * session stays, showing why it ended, until it is closed.
 *
 * [open], [close], [select] and [duplicate] must be called on the main thread, as
 * [GhosttyTerminalSession] requires.
 */
class TailscaleSSHSessionManager(
    private val targets: StateFlow<CoreTarget?>,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {
    val state: StateFlow<TailscaleSSHState>
        field = MutableStateFlow(TailscaleSSHState())

    private val access = Any()
    private var nextSessionId = 0L

    fun open(target: TailscaleSSHTarget): Long {
        val sessionId = synchronized(access) { ++nextSessionId }
        val terminal = GhosttyTerminalSession(
            GhosttyTerminalSession.Options(
                reportedVersion = TERMINAL_REPORTED_VERSION,
                terminfoName = target.login.terminalType,
            ),
        )
        val session = TailscaleSSHSession(sessionId, target, terminal)
        session.requests.trySend(startMessage(target, terminal))
        terminal.transport = TailscaleSSHTransport(session.requests)
        state.update { current ->
            current.copy(sessions = current.sessions + session, activeSessionId = sessionId)
        }
        val coreTarget = targets.value
        session.job = scope.launch(start = CoroutineStart.LAZY) {
            runSession(session, coreTarget)
        }.also { it.start() }
        return sessionId
    }

    fun close(sessionId: Long) {
        val session = state.value.sessions.firstOrNull { it.id == sessionId } ?: return
        state.update { current ->
            val remaining = current.sessions.filterNot { it.id == sessionId }
            val activeSessionId = if (current.activeSessionId == sessionId) {
                remaining.lastOrNull()?.id
            } else {
                current.activeSessionId
            }
            current.copy(sessions = remaining, activeSessionId = activeSessionId)
        }
        session.job?.cancel()
        session.terminal.close()
    }

    fun closeAll() {
        for (session in state.value.sessions) {
            close(session.id)
        }
    }

    fun select(sessionId: Long) {
        state.update { current ->
            if (current.sessions.none { it.id == sessionId }) {
                return@update current
            }
            current.copy(activeSessionId = sessionId)
        }
    }

    /** Opens another session to the peer and with the login of [sessionId]. */
    fun duplicate(sessionId: Long) {
        val session = state.value.sessions.firstOrNull { it.id == sessionId } ?: return
        open(session.target)
    }

    private suspend fun runSession(session: TailscaleSSHSession, coreTarget: CoreTarget?) {
        try {
            val client = (coreTarget ?: throw CoreNotRunningException()).client
            client.startTailscaleSSHSession(session.requests.consumeAsFlow()).collect { message ->
                session.handle(message)
            }
            // A stream that ends without an exit status lost the connection to the core.
            session.finish(
                exitCode = TailscaleSSHSession.SESSION_FAILED,
                signal = "",
                errorMessage = "",
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            session.finish(
                exitCode = TailscaleSSHSession.SESSION_FAILED,
                signal = "",
                errorMessage = e.readableMessage,
            )
        }
    }

    private fun startMessage(target: TailscaleSSHTarget, terminal: GhosttyTerminalSession) = TailscaleSSHClientMessage(
        start = TailscaleSSHStart(
            endpointTag = target.endpointTag,
            peerAddress = target.peerAddress,
            username = target.login.username,
            terminalType = target.login.terminalType,
            columns = terminal.columns.takeIf { it > 0 } ?: DEFAULT_COLUMNS,
            rows = terminal.rows.takeIf { it > 0 } ?: DEFAULT_ROWS,
            hostKeys = target.hostKeys,
        ),
    )

    private companion object {
        const val TERMINAL_REPORTED_VERSION = "husi"
        const val DEFAULT_COLUMNS = 80
        const val DEFAULT_ROWS = 24
    }
}

/** Queues the terminal's input and window sizes after the start message; never blocks. */
private class TailscaleSSHTransport(
    private val requests: SendChannel<TailscaleSSHClientMessage>,
) : GhosttyTerminalSession.Transport {
    override fun sendInput(data: ByteArray) {
        requests.trySend(
            TailscaleSSHClientMessage(input = TailscaleSSHInput(data_ = data.toByteString())),
        )
    }

    override fun sendResize(columns: Int, rows: Int, widthPixels: Int, heightPixels: Int) {
        requests.trySend(
            TailscaleSSHClientMessage(
                resize = TailscaleSSHResize(
                    columns = columns,
                    rows = rows,
                    widthPixels = widthPixels,
                    heightPixels = heightPixels,
                ),
            ),
        )
    }

    override fun close() {
        requests.close()
    }
}
