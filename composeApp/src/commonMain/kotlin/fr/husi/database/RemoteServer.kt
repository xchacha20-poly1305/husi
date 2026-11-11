package fr.husi.database

import kotlinx.serialization.Serializable

@Serializable
data class RemoteServer(
    val id: Long = 0L,
    val userOrder: Long = 0L,
    val name: String = "",
    val url: String = "",
    val secret: String = "",
)
