package fr.husi.tailscale


data class TaildropFileState(
    val name: String,
    val size: Long,
    val senderName: String,
    val modifiedAt: Long,
)

data class TaildropReceivingFileState(
    val name: String,
    val size: Long,
    val receivedBytes: Long,
    val senderId: String,
    val senderName: String,
)

