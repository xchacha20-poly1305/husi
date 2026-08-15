package fr.husi.ui.remote

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MenuDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.husi.compose.DropdownMenuSectionHeader
import fr.husi.compose.material3.Text
import fr.husi.core.remote.RemoteControlManager
import fr.husi.database.RemoteServer
import fr.husi.resources.Res
import fr.husi.resources.remote_target
import fr.husi.resources.remote_target_local
import fr.husi.resources.remote_target_manage
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
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
    val scope = rememberCoroutineScope()
    val activeId = session?.server?.id ?: RemoteControlManager.LOCAL_TARGET_ID
    val itemCount = 2 + servers.size

    DropdownMenuGroup(
        shapes = MenuDefaults.groupShape(groupIndex, groupCount),
    ) {
        DropdownMenuSectionHeader(stringResource(Res.string.remote_target))
        DropdownMenuItem(
            selected = activeId == RemoteControlManager.LOCAL_TARGET_ID,
            onClick = {
                onDismiss()
                scope.launch { remoteControl.exitRemote() }
            },
            text = { Text(stringResource(Res.string.remote_target_local)) },
            shapes = MenuDefaults.itemShape(0, itemCount),
        )
        servers.forEachIndexed { index, server ->
            DropdownMenuItem(
                selected = activeId == server.id,
                onClick = {
                    onDismiss()
                    scope.launch { remoteControl.enterRemote(server) }
                },
                text = { Text(serverDisplayName(server)) },
                shapes = MenuDefaults.itemShape(index + 1, itemCount),
            )
        }
        DropdownMenuItem(
            text = { Text(stringResource(Res.string.remote_target_manage)) },
            onClick = {
                onDismiss()
                onManage()
            },
            shape = MenuDefaults.itemShape(itemCount - 1, itemCount).shape,
        )
    }
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
