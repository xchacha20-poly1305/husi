@file:OptIn(ExperimentalWasmJsInterop::class)

package fr.husi.tailscale

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.husi.ktx.Logs
import fr.husi.proto.daemon.CancelTaildropReceivingRequest
import fr.husi.proto.daemon.DeleteTaildropFileRequest
import fr.husi.proto.daemon.DownloadTaildropFileRequest
import fr.husi.proto.daemon.MarkTaildropInboxReadRequest
import fr.husi.proto.daemon.SubscribeTaildropInboxRequest
import fr.husi.proto.daemon.TailscalePingRequest
import fr.husi.remote.RemoteState
import io.github.xchacha20_poly1305.kurpc.WebCallOptions
import io.github.xchacha20_poly1305.kurpc.WebStreamTransport
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

internal class RemotePeerModel(private val tag: String, private val remote: RemoteState) :
    ViewModel() {
    var ping by mutableStateOf(TailscalePingState())
        private set

    private var pingJob: Job? = null

    fun stopPing() {
        pingJob?.cancel()
    }

    fun startPing(ip: String) {
        stopPing()
        ping = TailscalePingState(running = true)
        pingJob = viewModelScope.launch {
            try {
                remote.client.service
                    .startTailscalePing(TailscalePingRequest(tag, ip))
                    .collect { update ->
                        val latency = update.latencyMs
                        ping = ping.copy(
                            hasResult = true,
                            latencyMilliseconds = latency,
                            direct = update.isDirect,
                            derpRegionCode = update.derpRegionCode,
                            endpoint = update.endpoint,
                            peerRelay = update.peerRelay,
                            error = update.error,
                            latencyHistory = (ping.latencyHistory + latency.toFloat()).takeLast(30),
                        )
                    }
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                Logs.w("Tailscale ping failed", failure)
                ping = ping.copy(error = failure.message.orEmpty())
            } finally {
                ping = ping.copy(running = false)
            }
        }
    }
}

internal class RemoteInboxModel(private val tag: String, private val remote: RemoteState) :
    ViewModel() {
    var files by mutableStateOf(emptyList<TaildropFileState>())
        private set

    var receiving by mutableStateOf(emptyList<TaildropReceivingFileState>())
        private set

    init {
        viewModelScope.launch {
            try {
                remote.client.service
                    .subscribeTaildropInbox(SubscribeTaildropInboxRequest(tag))
                    .collect { inbox ->
                        files =
                            inbox.files.map {
                                TaildropFileState(
                                    it.name,
                                    it.size,
                                    it.senderName,
                                    it.modifiedAt,
                                )
                            }
                        receiving =
                            inbox.receiving.map {
                                TaildropReceivingFileState(
                                    it.name,
                                    it.size,
                                    it.receivedBytes,
                                    it.senderID,
                                    it.senderName,
                                )
                            }
                        if (files.isNotEmpty()) {
                            remote.call { markTaildropInboxRead(MarkTaildropInboxReadRequest(tag)) }
                        }
                    }
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                Logs.w("RemoteTailscaleModels operation failed", failure)
            }
        }
    }

    fun delete(name: String) {
        remote.call { deleteTaildropFile(DeleteTaildropFileRequest(tag, name)) }
    }

    fun deleteAll() {
        files.forEach { delete(it.name) }
    }

    fun cancelReceiving(sender: String, name: String) {
        remote.call { cancelTaildropReceiving(CancelTaildropReceivingRequest(tag, sender, name)) }
    }

    fun download(name: String) {
        viewModelScope.launch {
            try {
                val download = browserDownload()
                var size = 0L
                var expected = 0L
                remote.client.service.downloadTaildropFile(
                    DownloadTaildropFileRequest(tag, name),
                    WebCallOptions(serverStreamingTransport = WebStreamTransport.Fetch),
                ).collect { chunk ->
                    if (chunk.size != 0L) {
                        expected = chunk.size
                    }
                    size += chunk.data_.size
                    check(size <= MAX_DOWNLOAD_BYTES) { "Browser download exceeds 512 MiB" }
                    download.append(chunk.data_.toByteArray().toBrowserBytes())
                }
                check(size == expected) { "Incomplete file download" }
                download.save(name)
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                Logs.w("RemoteTailscaleModels operation failed", failure)
            }
        }
    }
}

private const val MAX_DOWNLOAD_BYTES = 512L * 1024 * 1024
