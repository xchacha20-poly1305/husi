package fr.husi.database

import kotlinx.serialization.Serializable

@Serializable
enum class RemoteBackend {
    AUTO,
    SING_BOX,
    CLASH
}

@Serializable
data class RemoteServer(
    val id: Long = 0L,
    val userOrder: Long = 0L,
    val name: String = "",
    val url: String = "",
    val secret: String = "",
    val backend: RemoteBackend = RemoteBackend.SING_BOX,
)
