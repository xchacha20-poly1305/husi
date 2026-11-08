@file:OptIn(ExperimentalWasmJsInterop::class)

package fr.husi.tailscale

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.husi.remote.RemoteState
import fr.husi.remote.flag
import fr.husi.remote.items
import fr.husi.remote.number
import fr.husi.remote.request
import fr.husi.remote.text
import kotlin.js.JsString
import kotlin.js.Promise
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.await
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
                remote.client
                    .stream("StartTailscalePing", request("endpointTag" to tag, "peerIP" to ip))
                    .collect { update ->
                        val latency = update.text("latencyMs").toDoubleOrNull() ?: 0.0
                        ping =
                            ping.copy(
                                hasResult = true,
                                latencyMilliseconds = latency,
                                direct = update.flag("isDirect"),
                                derpRegionCode = update.text("derpRegionCode"),
                                endpoint = update.text("endpoint"),
                                peerRelay = update.text("peerRelay"),
                                error = update.text("error"),
                                latencyHistory =
                                    (ping.latencyHistory + latency.toFloat()).takeLast(30),
                            )
                    }
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
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
                remote.client
                    .stream("SubscribeTaildropInbox", request("endpointTag" to tag))
                    .collect { inbox ->
                        files =
                            inbox.items("files").map {
                                TaildropFileState(
                                    it.text("name"),
                                    it.number("size"),
                                    it.text("senderName"),
                                    it.number("modifiedAt"),
                                )
                            }
                        receiving =
                            inbox.items("receiving").map {
                                TaildropReceivingFileState(
                                    it.text("name"),
                                    it.number("size"),
                                    it.number("receivedBytes"),
                                    it.text("senderID"),
                                    it.text("senderName"),
                                )
                            }
                        if (files.isNotEmpty())
                            remote.call("MarkTaildropInboxRead", request("endpointTag" to tag))
                    }
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                remote.error = failure.message.orEmpty()
            }
        }
    }

    fun delete(name: String) {
        remote.call("DeleteTaildropFile", request("endpointTag" to tag, "name" to name))
    }

    fun deleteAll() {
        files.forEach { delete(it.name) }
    }

    fun cancelReceiving(sender: String, name: String) {
        remote.call(
            "CancelTaildropReceiving",
            request("endpointTag" to tag, "senderID" to sender, "name" to name),
        )
    }

    fun download(name: String) {
        viewModelScope.launch {
            try {
                downloadFile(tag, name).await()
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                remote.error = failure.message.orEmpty()
            }
        }
    }
}

/** Streams the Taildrop file into a browser download. */
@JsFun("(endpoint,name) => husiRpc.download(endpoint,name)")
private external fun downloadFile(endpoint: String, name: String): Promise<JsString?>
