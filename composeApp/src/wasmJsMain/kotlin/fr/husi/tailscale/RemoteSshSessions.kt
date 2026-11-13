package fr.husi.tailscale

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import fr.husi.ktx.Logs
import fr.husi.proto.daemon.TailscaleSSHClientMessage
import fr.husi.proto.daemon.TailscaleSSHInput
import fr.husi.proto.daemon.TailscaleSSHResize
import fr.husi.proto.daemon.TailscaleSSHStart
import fr.husi.remote.RemoteClient
import fr.husi.remote.RemoteState
import io.github.xchacha20_poly1305.libghostty.GhosttyTerminalSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import okio.ByteString.Companion.toByteString

internal class RemoteSshSessions(private val remote: RemoteState) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val sessions = mutableStateListOf<RemoteSshSession>()
    var activeSessionId by mutableStateOf<Long?>(null)
        private set

    private var nextId = 0L

    init {
        scope.launch {
            snapshotFlow { remote.selected?.id to remote.connected }
                .collect { (_, connected) ->
                    if (!connected) {
                        closeAll()
                    }
                }
        }
    }

    fun open(target: TailscaleSSHTarget) {
        if (!remote.connected) return
        val session = RemoteSshSession(++nextId, target)
        sessions.add(session)
        activeSessionId = session.id
        session.start(scope, remote.client)
    }

    fun select(id: Long) {
        activeSessionId = id
    }

    fun duplicate(id: Long) {
        sessions.firstOrNull { it.id == id }?.let { open(it.target) }
    }

    fun close(id: Long) {
        sessions.firstOrNull { it.id == id }?.close()
        sessions.removeAll { it.id == id }
        if (activeSessionId == id) activeSessionId = sessions.lastOrNull()?.id
    }

    fun closeAll() {
        sessions.toList().forEach { close(it.id) }
    }

    fun dispose() {
        closeAll()
        scope.cancel()
    }
}

internal class RemoteSshSession(override val id: Long, override val target: TailscaleSSHTarget) :
    TailscaleSSHSessionView {
    override val terminal =
        GhosttyTerminalSession(
            GhosttyTerminalSession.Options(terminfoName = target.login.terminalType),
        )
    override val phase: StateFlow<TailscaleSSHPhase>
        field = MutableStateFlow(TailscaleSSHPhase.CONNECTING)
    override val banner: StateFlow<String?>
        field = MutableStateFlow(null)
    private val requests = Channel<TailscaleSSHClientMessage>(Channel.UNLIMITED)
    private var job: Job? = null

    fun start(scope: CoroutineScope, client: RemoteClient) {
        requests.trySend(TailscaleSSHClientMessage(start = TailscaleSSHStart(
            endpointTag = target.endpointTag,
            peerAddress = target.peerAddress,
            username = target.login.username,
            terminalType = target.login.terminalType,
            columns = 80,
            rows = 24,
            hostKeys = target.hostKeys,
        )))
        val service = client.service
        terminal.transport =
            object : GhosttyTerminalSession.Transport {
                override fun sendInput(data: ByteArray) {
                    requests.trySend(TailscaleSSHClientMessage(input = TailscaleSSHInput(data.toByteString())))
                }

                override fun sendResize(
                    columns: Int,
                    rows: Int,
                    widthPixels: Int,
                    heightPixels: Int,
                ) {
                    requests.trySend(TailscaleSSHClientMessage(resize = TailscaleSSHResize(
                        columns, rows, widthPixels, heightPixels,
                    )))
                }

                override fun close() {
                    requests.close()
                }
            }
        job = scope.launch {
            try {
                service.startTailscaleSSHSession(requests.receiveAsFlow()).collect { message ->
                    message.output?.let { terminal.feedOutput(it.data_.toByteArray()) }
                    message.authBanner?.let { banner.value = it.message }
                    message.ready?.let { phase.value = TailscaleSSHPhase.RUNNING }
                    message.exit?.let { finish(it.exitCode, it.errorMessage) }
                    message.error?.let { finish(-1, it.message) }
                }
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                Logs.w("Remote SSH session failed", failure)
                finish(-1, failure.message.orEmpty())
            }
        }
    }

    private fun finish(code: Int, error: String) {
        phase.value = TailscaleSSHPhase.FINISHED
        requests.close()
        if (error.isNotEmpty()) terminal.feedOutput("\r\n$error\r\n".encodeToByteArray())
        terminal.finish(code)
    }

    fun close() {
        requests.close()
        job?.cancel()
        terminal.close()
    }
}
