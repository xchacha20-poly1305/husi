package fr.husi.tailscale

import fr.husi.core.remote.CoreNotRunningException
import fr.husi.core.remote.CoreTarget
import kotlin.concurrent.Volatile

internal class TailscalePresence private constructor(
    private val isPresent: (List<TailscaleEndpointState>) -> Boolean,
) {
    private var skippedInitial = false

    @Volatile
    private var boundTarget: CoreTarget? = null

    fun requireTarget(): CoreTarget = boundTarget ?: throw CoreNotRunningException()

    fun shouldClose(snapshot: TailscaleSnapshot): Boolean {
        snapshot.target?.let { target ->
            val bound = boundTarget
            if (bound == null) {
                boundTarget = target
            } else if (bound != target) {
                return true
            }
        }
        val present = isPresent(snapshot.endpoints)
        if (!skippedInitial) {
            skippedInitial = true
            return !present && snapshot.endpoints.isNotEmpty()
        }
        return !present
    }

    companion object {
        fun endpoint(endpointTag: String) = TailscalePresence { endpoints ->
            endpoints.any { it.endpointTag == endpointTag }
        }

        fun peer(endpointTag: String, peerId: String) = TailscalePresence { endpoints ->
            endpoints.firstOrNull { it.endpointTag == endpointTag }?.findPeer(peerId) != null
        }
    }
}
