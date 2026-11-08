package fr.husi.tailscale


data class TaildropSendFileState(
    val name: String,
    val size: Long,
    val sentBytes: Long = 0,
    val completed: Boolean = false,
)

data class TaildropSendSession(
    val id: Long,
    val endpointTag: String,
    val peerName: String,
    val files: List<TaildropSendFileState>,
    val finished: Boolean = false,
    val errorMessage: String? = null,
)

