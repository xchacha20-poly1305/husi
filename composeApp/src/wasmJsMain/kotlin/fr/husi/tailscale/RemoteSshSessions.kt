package fr.husi.tailscale

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import fr.husi.remote.RemoteClient
import fr.husi.remote.RemoteState
import fr.husi.remote.number
import fr.husi.remote.obj
import fr.husi.remote.request
import fr.husi.remote.text
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
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.io.encoding.Base64

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
    private val requests = Channel<JsonObject>(Channel.UNLIMITED)
    private var job: Job? = null

    fun start(scope: CoroutineScope, client: RemoteClient) {
        requests.trySend(
            buildJsonObject {
                put(
                    "start",
                    buildJsonObject {
                        put("endpointTag", target.endpointTag)
                        put("peerAddress", target.peerAddress)
                        put("username", target.login.username)
                        put("terminalType", target.login.terminalType)
                        put("columns", 80)
                        put("rows", 24)
                        put("hostKeys", JsonArray(target.hostKeys.map(::JsonPrimitive)))
                    },
                )
            },
        )
        terminal.transport =
            object : GhosttyTerminalSession.Transport {
                override fun sendInput(data: ByteArray) {
                    requests.trySend(
                        buildJsonObject { put("input", request("data" to Base64.encode(data))) },
                    )
                }

                override fun sendResize(
                    columns: Int,
                    rows: Int,
                    widthPixels: Int,
                    heightPixels: Int,
                ) {
                    requests.trySend(
                        buildJsonObject {
                            put(
                                "resize",
                                buildJsonObject {
                                    put("columns", columns)
                                    put("rows", rows)
                                    put("widthPixels", widthPixels)
                                    put("heightPixels", heightPixels)
                                },
                            )
                        },
                    )
                }

                override fun close() {
                    requests.close()
                }
            }
        job = scope.launch {
            try {
                client.bidi("StartTailscaleSSHSession", requests.receiveAsFlow()).collect { message ->
                    when {
                        "output" in message -> {
                            terminal.feedOutput(Base64.decode(message.obj("output").text("data")))
                        }

                        "authBanner" in message -> banner.value = message.obj("authBanner").text("message")
                        "ready" in message -> phase.value = TailscaleSSHPhase.RUNNING
                        "exit" in message -> finish(
                            message.obj("exit").number("exitCode").toInt(),
                            message.obj("exit").text("errorMessage"),
                        )

                        "error" in message -> finish(-1, message.obj("error").text("message"))
                    }
                }
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
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
