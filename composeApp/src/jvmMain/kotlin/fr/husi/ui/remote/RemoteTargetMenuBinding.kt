package fr.husi.ui.remote

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MenuDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.husi.core.remote.RemoteControlManager
import fr.husi.database.RemoteServer
import org.koin.compose.koinInject

@Composable
fun RemoteTargetMenuGroup(
    groupIndex: Int,
    groupCount: Int,
    onManage: () -> Unit,
    onDismiss: () -> Unit,
    remoteControl: RemoteControlManager = koinInject(),
) {
    val servers by remoteControl.servers.collectAsStateWithLifecycle(emptyList())
    val session by remoteControl.session.collectAsStateWithLifecycle()
    RemoteTargetMenuContent(
        servers = servers,
        activeId = session?.server?.id,
        onSelectServer = { id ->
            servers.firstOrNull { it.id == id }?.let { remoteControl.enterRemote(it) }
        },
        onSelectLocal = { remoteControl.exitRemote() },
        groupIndex = groupIndex,
        groupCount = groupCount,
        onManage = onManage,
        onDismiss = onDismiss,
    )
}

@Composable
fun RemoteTargetMenuSection(
    groupIndex: Int,
    groupCount: Int,
    onManage: () -> Unit,
    onDismiss: () -> Unit,
) {
    RemoteTargetMenuGroup(
        groupIndex = groupIndex,
        groupCount = groupCount,
        onManage = onManage,
        onDismiss = onDismiss,
    )
    if (groupIndex < groupCount - 1) {
        Spacer(Modifier.height(MenuDefaults.GroupSpacing))
    }
}

internal fun serverDisplayName(server: RemoteServer): String {
    return server.name.ifBlank { server.url }
}
