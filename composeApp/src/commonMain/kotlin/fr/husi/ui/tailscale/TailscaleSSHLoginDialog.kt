package fr.husi.ui.tailscale

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import fr.husi.compose.TextButton
import fr.husi.compose.material3.Icon
import fr.husi.compose.material3.Text
import fr.husi.resources.Res
import fr.husi.resources.cancel
import fr.husi.resources.connect
import fr.husi.resources.tailscale_ssh_terminal_type
import fr.husi.resources.tailscale_ssh_terminal_type_hint
import fr.husi.resources.terminal
import fr.husi.resources.username
import fr.husi.tailscale.DEFAULT_SSH_TERMINAL_TYPE
import fr.husi.tailscale.TailscaleSSHLogin
import fr.husi.tailscale.TailscaleSSHLogins
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource

@Composable
internal fun TailscaleSSHLoginDialog(
    peerStableId: String,
    peerName: String,
    onDismiss: () -> Unit,
    onConnect: (TailscaleSSHLogin) -> Unit,
) {
    var username by remember { mutableStateOf("") }
    var terminalType by remember { mutableStateOf("") }
    LaunchedEffect(peerStableId) {
        val login = TailscaleSSHLogins.get(peerStableId)
        username = login.username
        terminalType = login.terminalType
    }
    val login = TailscaleSSHLogin(
        username = username.trim(),
        terminalType = terminalType.trim().ifEmpty { DEFAULT_SSH_TERMINAL_TYPE },
    )
    val canConnect = login.username.isNotEmpty()

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(stringResource(Res.string.connect)) {
                if (canConnect) onConnect(login)
            }
        },
        dismissButton = {
            TextButton(stringResource(Res.string.cancel), onDismiss)
        },
        icon = { Icon(vectorResource(Res.drawable.terminal), null) },
        title = { Text(peerName) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text(stringResource(Res.string.username)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.None,
                        autoCorrectEnabled = false,
                        imeAction = ImeAction.Next,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = terminalType,
                    onValueChange = { terminalType = it },
                    label = { Text(stringResource(Res.string.tailscale_ssh_terminal_type)) },
                    supportingText = { Text(stringResource(Res.string.tailscale_ssh_terminal_type_hint)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.None,
                        autoCorrectEnabled = false,
                        imeAction = ImeAction.Done,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
    )
}
