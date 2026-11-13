package fr.husi.tailscale

import fr.husi.core.TAILDROP_CHUNK_SIZE
import fr.husi.core.remote.CoreNotRunningException
import fr.husi.core.remote.CoreTarget
import fr.husi.ktx.readableMessage
import fr.husi.proto.daemon.TaildropFileChunk
import fr.husi.proto.daemon.TaildropFileDone
import fr.husi.proto.daemon.TaildropOutgoingFile
import fr.husi.proto.daemon.TaildropSendClientMessage
import fr.husi.proto.daemon.TaildropSendServerMessage
import fr.husi.proto.daemon.TaildropSendStart
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okio.BufferedSource
import okio.ByteString.Companion.toByteString
import okio.buffer

class TaildropSendManager(
    private val targets: StateFlow<CoreTarget?>,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {
    val sessions: StateFlow<List<TaildropSendSession>>
        field = MutableStateFlow(emptyList<TaildropSendSession>())

    private val access = Any()
    private var nextSessionId = 0L
    private val jobs = mutableMapOf<Long, Job>()

    fun send(tag: String, peerStableId: String, peerName: String, files: List<TaildropSource>): Long {
        if (files.isEmpty()) return NO_SESSION_ID
        val sessionId = synchronized(access) { ++nextSessionId }
        sessions.update { current ->
            current + TaildropSendSession(
                id = sessionId,
                endpointTag = tag,
                peerName = peerName,
                files = files.map { file -> TaildropSendFileState(name = file.name, size = file.size) },
            )
        }
        val target = targets.value
        val job = scope.launch(start = CoroutineStart.LAZY) {
            runSession(sessionId, target, tag, peerStableId, files)
        }
        synchronized(access) { jobs[sessionId] = job }
        job.start()
        return sessionId
    }

    fun cancel(sessionId: Long) {
        val job = synchronized(access) { jobs.remove(sessionId) }
        dismiss(sessionId)
        job?.cancel()
    }

    fun cancelAll() {
        for (session in sessions.value) {
            if (!session.finished) {
                cancel(session.id)
            }
        }
    }

    fun dismiss(sessionId: Long) {
        sessions.update { current -> current.filterNot { it.id == sessionId } }
    }

    private suspend fun runSession(
        sessionId: Long,
        target: CoreTarget?,
        tag: String,
        peerStableId: String,
        files: List<TaildropSource>,
    ) {
        try {
            val client = (target ?: throw CoreNotRunningException()).client
            client.sendTaildropFiles(requestFlow(tag, peerStableId, files)).collect { message ->
                message.applyProgress(sessionId)
            }
            updateSession(sessionId) { copy(finished = true) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            updateSession(sessionId) { copy(finished = true, errorMessage = e.readableMessage) }
        } finally {
            synchronized(access) { jobs.remove(sessionId) }
        }
    }

    private fun requestFlow(
        tag: String,
        peerStableId: String,
        files: List<TaildropSource>,
    ): Flow<TaildropSendClientMessage> = flow {
        emit(startMessage(tag, peerStableId, files))
        for (file in files) {
            file.open().buffer().use { source -> writeFile(source) }
            emit(FILE_DONE)
        }
    }

    private suspend fun FlowCollector<TaildropSendClientMessage>.writeFile(source: BufferedSource) {
        val chunk = ByteArray(TAILDROP_CHUNK_SIZE)
        while (true) {
            var filled = 0
            while (filled < chunk.size) {
                val read = source.read(chunk, filled, chunk.size - filled)
                if (read == -1) {
                    if (filled > 0) emit(chunkMessage(chunk, filled))
                    return
                }
                filled += read
            }
            emit(chunkMessage(chunk, chunk.size))
        }
    }

    private fun TaildropSendServerMessage.applyProgress(sessionId: Long) {
        val update = progress ?: return
        updateFile(sessionId, update.fileIndex) {
            if (update.fileCompleted) {
                copy(size = update.sentBytes, sentBytes = update.sentBytes, completed = true)
            } else {
                copy(sentBytes = update.sentBytes)
            }
        }
    }

    private fun updateSession(sessionId: Long, reducer: TaildropSendSession.() -> TaildropSendSession) {
        sessions.update { current ->
            current.map { session ->
                if (session.id == sessionId) {
                    session.reducer()
                } else {
                    session
                }
            }
        }
    }

    private fun updateFile(
        sessionId: Long,
        fileIndex: Int,
        reducer: TaildropSendFileState.() -> TaildropSendFileState,
    ) {
        updateSession(sessionId) {
            if (fileIndex !in files.indices) {
                this
            } else {
                copy(
                    files = files.mapIndexed { index, file ->
                        if (index == fileIndex) {
                            file.reducer()
                        } else {
                            file
                        }
                    },
                )
            }
        }
    }

    companion object {
        const val NO_SESSION_ID = -1L

        private val FILE_DONE: TaildropSendClientMessage = TaildropSendClientMessage(fileDone = TaildropFileDone())
    }
}

private fun startMessage(
    tag: String,
    peerStableId: String,
    files: List<TaildropSource>,
): TaildropSendClientMessage = TaildropSendClientMessage(
    start = TaildropSendStart(
        endpointTag = tag,
        peerStableID = peerStableId,
        files = files.map { file ->
            TaildropOutgoingFile(name = file.name, size = file.size)
        },
    ),
)

private fun chunkMessage(chunk: ByteArray, length: Int): TaildropSendClientMessage = TaildropSendClientMessage(
    chunk = TaildropFileChunk(data_ = chunk.toByteString(0, length)),
)
