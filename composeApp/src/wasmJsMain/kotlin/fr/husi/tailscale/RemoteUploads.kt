package fr.husi.tailscale

import androidx.compose.runtime.mutableStateListOf
import fr.husi.ktx.Logs
import fr.husi.proto.daemon.TaildropFileChunk
import fr.husi.proto.daemon.TaildropFileDone
import fr.husi.proto.daemon.TaildropOutgoingFile
import fr.husi.proto.daemon.TaildropSendClientMessage
import fr.husi.proto.daemon.TaildropSendStart
import fr.husi.remote.RemoteState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import okio.ByteString.Companion.toByteString

internal class RemoteUploads(private val remote: RemoteState, private val scope: CoroutineScope) {
    val sessions = mutableStateListOf<TaildropSendSession>()
    private val jobs = mutableMapOf<Long, Job>()
    private var nextId = 0L

    fun send(tag: String, peer: TailscalePeerState) {
        if (!remote.connected) return
        // Capture this session before opening the picker: switching servers closes its channel.
        val service = remote.client.service
        val id = ++nextId
        val job = scope.launch(start = CoroutineStart.LAZY) {
            var failureMessage: String? = null
            try {
                val files = pickFiles()
                if (files.isEmpty()) return@launch
                currentCoroutineContext().ensureActive()
                sessions.add(TaildropSendSession(
                    id, tag, peer.displayName,
                    files.map { TaildropSendFileState(it.name, it.size.toLong()) },
                ))
                val requests = flow {
                    emit(TaildropSendClientMessage(start = TaildropSendStart(
                        endpointTag = tag,
                        peerStableID = peer.stableId,
                        files = files.map { TaildropOutgoingFile(it.name, it.size.toLong()) },
                    )))
                    for (file in files) {
                        var offset = 0L
                        while (offset < file.size.toLong()) {
                            val bytes = file.read(offset, UPLOAD_CHUNK_BYTES)
                            check(bytes.isNotEmpty()) { "File ended before its declared size" }
                            emit(TaildropSendClientMessage(chunk = TaildropFileChunk(bytes.toByteString())))
                            offset += bytes.size
                        }
                        emit(TaildropSendClientMessage(fileDone = TaildropFileDone()))
                    }
                }
                service.sendTaildropFiles(requests).collect { update ->
                    val progress = update.progress ?: return@collect
                    updateSession(id) { session ->
                        session.copy(files = session.files.mapIndexed { index, file ->
                            if (index == progress.fileIndex) file.copy(
                                sentBytes = progress.sentBytes,
                                completed = progress.fileCompleted,
                            ) else file
                        })
                    }
                }
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                failureMessage = failure.message ?: failure.toString()
                Logs.w("Upload failed", failure)
            } finally {
                jobs.remove(id)
                updateSession(id) { it.copy(finished = true, errorMessage = failureMessage) }
            }
        }
        jobs[id] = job
        job.start()
    }

    private fun updateSession(id: Long, update: (TaildropSendSession) -> TaildropSendSession) {
        val index = sessions.indexOfFirst { it.id == id }
        if (index >= 0) sessions[index] = update(sessions[index])
    }

    fun cancel(id: Long) {
        jobs[id]?.cancel()
    }

    fun dismiss(id: Long) {
        sessions.removeAll { it.id == id }
    }

    private companion object {
        const val UPLOAD_CHUNK_BYTES = 64 * 1024
    }
}
