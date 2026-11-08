package fr.husi.ui.remote

import fr.husi.core.remote.RemoteSessionState
import fr.husi.database.RemoteServer

data class RemoteControlState(
    val servers: List<RemoteServer>,
    val activeServerId: Long?,
    val isRemote: Boolean,
    val sessionState: RemoteSessionState?,
)

data class RemoteSessionInfo(
    val name: String,
    val state: RemoteSessionState,
    val startedAt: Long?,
)
