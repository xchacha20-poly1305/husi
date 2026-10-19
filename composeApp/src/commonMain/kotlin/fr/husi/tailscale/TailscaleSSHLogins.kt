package fr.husi.tailscale

import fr.husi.database.DataStore
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

const val DEFAULT_SSH_USERNAME = "root"
const val DEFAULT_SSH_TERMINAL_TYPE = "xterm-256color"

@Serializable
data class TailscaleSSHLogin(
    val username: String = DEFAULT_SSH_USERNAME,
    val terminalType: String = DEFAULT_SSH_TERMINAL_TYPE,
)

/** The login last used for each peer, keyed by the peer's stable ID and kept in [DataStore.tailscaleSSHLogins]. */
object TailscaleSSHLogins {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun get(peerStableId: String): TailscaleSSHLogin =
        decode(DataStore.tailscaleSSHLogins.get())[peerStableId] ?: TailscaleSSHLogin()

    /** A login equal to the default is dropped rather than stored. */
    suspend fun put(peerStableId: String, login: TailscaleSSHLogin) {
        DataStore.tailscaleSSHLogins.update { stored ->
            val logins = decode(stored).toMutableMap()
            if (login == TailscaleSSHLogin()) {
                logins.remove(peerStableId)
            } else {
                logins[peerStableId] = login
            }
            json.encodeToString(logins)
        }
    }

    private fun decode(stored: String): Map<String, TailscaleSSHLogin> {
        if (stored.isBlank()) return emptyMap()
        return try {
            json.decodeFromString(stored)
        } catch (_: SerializationException) {
            emptyMap()
        }
    }
}
