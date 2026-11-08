package fr.husi.ui.remote

import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MenuDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import fr.husi.compose.DropdownMenuSectionHeader
import fr.husi.compose.material3.Text
import fr.husi.database.RemoteServer
import fr.husi.resources.Res
import fr.husi.resources.remote_target
import fr.husi.resources.remote_target_local
import fr.husi.resources.remote_target_manage
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

@Composable
fun RemoteTargetMenuContent(
    servers: List<RemoteServer>,
    activeId: Long?,
    onSelectServer: suspend (Long) -> Unit,
    groupIndex: Int,
    groupCount: Int,
    onManage: () -> Unit,
    onDismiss: () -> Unit,
    onSelectLocal: (suspend () -> Unit)? = null,
) {
    val scope = rememberCoroutineScope()
    val localItemCount = if (onSelectLocal == null) 0 else 1
    val itemCount = 1 + localItemCount + servers.size
    DropdownMenuGroup(
        shapes = MenuDefaults.groupShape(groupIndex, groupCount),
    ) {
        DropdownMenuSectionHeader(stringResource(Res.string.remote_target))
        if (onSelectLocal != null)
            DropdownMenuItem(
                selected = activeId == null,
                onClick = {
                    onDismiss()
                    scope.launch { onSelectLocal() }
                },
                text = { Text(stringResource(Res.string.remote_target_local)) },
                shapes = MenuDefaults.itemShape(0, itemCount),
            )
        servers.forEachIndexed { index, server ->
            DropdownMenuItem(
                selected = activeId == server.id,
                onClick = {
                    onDismiss()
                    scope.launch { onSelectServer(server.id) }
                },
                text = { Text(server.name.ifBlank { server.url }) },
                shapes = MenuDefaults.itemShape(index + localItemCount, itemCount),
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
