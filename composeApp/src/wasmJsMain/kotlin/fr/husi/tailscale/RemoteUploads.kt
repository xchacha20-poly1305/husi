@file:OptIn(ExperimentalWasmJsInterop::class)

package fr.husi.tailscale

import androidx.compose.runtime.mutableStateListOf
import fr.husi.ktx.emptyAsNull
import fr.husi.remote.RemoteState
import fr.husi.remote.flag
import fr.husi.remote.items
import fr.husi.remote.number
import fr.husi.remote.obj
import fr.husi.remote.parseObject
import fr.husi.remote.text

internal class RemoteUploads(private val remote: RemoteState) {
    val sessions = mutableStateListOf<TaildropSendSession>()

    fun send(tag: String, peer: TailscalePeerState) {
        var uploadId: Long? = null
        uploadFiles(
            tag,
            peer.stableId,
            data = { data ->
                val update = parseObject(data)
                if ("files" in update) {
                    val id = update.number("uploadId")
                    uploadId = id
                    sessions.add(
                        TaildropSendSession(
                            id,
                            tag,
                            peer.displayName,
                            update.items("files").map {
                                TaildropSendFileState(it.text("name"), it.number("size"))
                            },
                        ),
                    )
                } else {
                    val id = uploadId ?: return@uploadFiles
                    val progress = update.obj("progress")
                    val index = sessions.indexOfFirst { it.id == id }
                    if (index >= 0 && progress.isNotEmpty()) {
                        val session = sessions[index]
                        sessions[index] =
                            session.copy(
                                files =
                                    session.files.mapIndexed { fileIndex, file ->
                                        if (fileIndex == progress.number("fileIndex").toInt()) {
                                            file.copy(
                                                sentBytes = progress.number("sentBytes"),
                                                completed = progress.flag("fileCompleted"),
                                            )
                                        } else {
                                            file
                                        }
                                    },
                            )
                    }
                }
            },
            end = { error ->
                val index = sessions.indexOfFirst { it.id == uploadId }
                if (index >= 0) {
                    sessions[index] = sessions[index].copy(
                        finished = true,
                        errorMessage = error.emptyAsNull(),
                    )
                } else if (error.isNotEmpty()) {
                    remote.error = error
                }
            },
        )
    }

    fun cancel(id: Long) {
        cancelUpload(id.toInt())
    }

    fun dismiss(id: Long) {
        sessions.removeAll { it.id == id }
    }
}

/**
 * Lets the user pick files and sends them to [peer]. [data] receives the protobuf JSON of each
 * update; [end] receives the error, or an empty string when sending finished or was cancelled.
 */
@JsFun("(endpoint,peer,data,end) => husiRpc.upload(endpoint,peer,data,end)")
private external fun uploadFiles(
    endpoint: String,
    peer: String,
    data: (String) -> Unit,
    end: (String) -> Unit,
)

@JsFun("(id) => husiRpc.cancelUpload(id)")
private external fun cancelUpload(id: Int)
