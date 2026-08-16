package fr.husi.ui.openvpn

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import fr.husi.compose.material3.Text
import fr.husi.resources.Res
import fr.husi.resources.auth_response
import fr.husi.resources.password
import fr.husi.resources.username
import fr.husi.vpn.OPENVPN_CHALLENGE_CREDENTIALS
import fr.husi.vpn.OPENVPN_CHALLENGE_OPEN_URL
import fr.husi.vpn.OPENVPN_CHALLENGE_SECRET
import fr.husi.vpn.OpenVPNChallengeState
import fr.husi.vpn.formatOpenVPNRemaining
import org.jetbrains.compose.resources.stringResource

@Composable
fun OpenVPNAuthChallengeContent(
    challenge: OpenVPNChallengeState,
    username: String,
    onUsernameChange: (String) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    secret: String,
    onSecretChange: (String) -> Unit,
    nowEpochSeconds: Long,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (challenge.previousError.isNotEmpty()) {
            Text(
                challenge.previousError,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
        if (challenge.message.isNotEmpty()) {
            Text(challenge.message, style = MaterialTheme.typography.bodyMedium)
        }
        if (challenge.kind == OPENVPN_CHALLENGE_OPEN_URL && challenge.url.isNotEmpty()) {
            Text(challenge.url, style = MaterialTheme.typography.bodyMedium)
        }
        if (challenge.kind == OPENVPN_CHALLENGE_SECRET && challenge.username.isNotEmpty()) {
            Text(
                stringResource(Res.string.username) + ": " + challenge.username,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        formatOpenVPNRemaining(challenge.deadline, nowEpochSeconds)?.let { remaining ->
            val expired = challenge.deadline in 1..nowEpochSeconds
            Text(
                remaining,
                style = MaterialTheme.typography.labelLarge,
                color = if (expired) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.tertiary
                },
            )
        }
        when (challenge.kind) {
            OPENVPN_CHALLENGE_CREDENTIALS -> {
                OutlinedTextField(
                    value = username,
                    onValueChange = onUsernameChange,
                    label = { Text(stringResource(Res.string.username)) },
                    singleLine = true,
                    enabled = enabled,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.None,
                        autoCorrectEnabled = false,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = onPasswordChange,
                    label = { Text(stringResource(Res.string.password)) },
                    singleLine = true,
                    enabled = enabled,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (challenge.secretMessage.isNotEmpty()) {
                    SecretField(challenge, secret, onSecretChange, enabled)
                }
            }

            OPENVPN_CHALLENGE_SECRET -> SecretField(challenge, secret, onSecretChange, enabled)
        }
    }
}

@Composable
private fun SecretField(
    challenge: OpenVPNChallengeState,
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = {
            Text(challenge.secretMessage.ifEmpty { stringResource(Res.string.auth_response) })
        },
        singleLine = true,
        enabled = enabled,
        visualTransformation = if (challenge.echo) {
            VisualTransformation.None
        } else {
            PasswordVisualTransformation()
        },
        keyboardOptions = if (challenge.echo) {
            KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                autoCorrectEnabled = false,
            )
        } else {
            KeyboardOptions(keyboardType = KeyboardType.Password)
        },
        modifier = Modifier.fillMaxWidth(),
    )
}
