package fr.husi.ui.remote

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.TextObfuscationMode
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedSecureTextField
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import fr.husi.BuildConfig
import fr.husi.compose.SimpleIconButton
import fr.husi.compose.material3.Button
import fr.husi.compose.material3.Card
import fr.husi.compose.material3.Text
import fr.husi.resources.Res
import fr.husi.resources.app_name
import fr.husi.resources.confirm
import fr.husi.resources.connecting
import fr.husi.resources.connection_test
import fr.husi.resources.connection_test_error
import fr.husi.resources.hide
import fr.husi.resources.profile_name
import fr.husi.resources.remote_server_secret
import fr.husi.resources.remote_server_url_invalid
import fr.husi.resources.remote_test_success
import fr.husi.resources.server_address
import fr.husi.resources.show
import fr.husi.resources.visibility
import fr.husi.resources.visibility_off
import fr.husi.resources.web_setup
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource

@Composable
internal fun RemoteSetupScreen(
    uiState: RemoteServerEditUiState,
    nameState: TextFieldState,
    urlState: TextFieldState,
    secretState: TextFieldState,
    onSave: () -> Unit,
    onTestConnection: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current
    var secretVisible by rememberSaveable { mutableStateOf(false) }
    val confirm = {
        focusManager.clearFocus()
        onSave()
    }

    Box(
        modifier = modifier.fillMaxSize().safeDrawingPadding().imePadding(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Card(
                modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Column(
                    modifier = Modifier.padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(
                        text = stringResource(Res.string.web_setup),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        style = MaterialTheme.typography.headlineMedium,
                        textAlign = TextAlign.Center,
                    )
                    OutlinedTextField(
                        state = nameState,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(Res.string.profile_name)) },
                        lineLimits = TextFieldLineLimits.SingleLine,
                        shape = RoundedCornerShape(16.dp),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        onKeyboardAction = { focusManager.moveFocus(FocusDirection.Next) },
                    )
                    OutlinedTextField(
                        state = urlState,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(Res.string.server_address)) },
                        lineLimits = TextFieldLineLimits.SingleLine,
                        shape = RoundedCornerShape(16.dp),
                        isError = uiState.urlError,
                        supportingText = if (uiState.urlError) {
                            { Text(stringResource(Res.string.remote_server_url_invalid)) }
                        } else {
                            null
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Uri,
                            autoCorrectEnabled = false,
                            imeAction = ImeAction.Next,
                        ),
                        onKeyboardAction = { focusManager.moveFocus(FocusDirection.Next) },
                    )
                    OutlinedSecureTextField(
                        state = secretState,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(Res.string.remote_server_secret)) },
                        shape = RoundedCornerShape(16.dp),
                        textObfuscationMode = if (secretVisible) {
                            TextObfuscationMode.Visible
                        } else {
                            TextObfuscationMode.Hidden
                        },
                        trailingIcon = {
                            SimpleIconButton(
                                imageVector = vectorResource(
                                    if (secretVisible) {
                                        Res.drawable.visibility_off
                                    }
                                    else{
                                        Res.drawable.visibility
                                    },
                                ),
                                contentDescription = stringResource(
                                    if (secretVisible) {
                                        Res.string.hide
                                    } else {
                                        Res.string.show
                                    },
                                ),
                                onClick = { secretVisible = !secretVisible },
                            )
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            autoCorrectEnabled = false,
                            imeAction = ImeAction.Done,
                        ),
                        onKeyboardAction = { confirm() },
                    )
                    SetupConnectionTest(
                        state = uiState.test,
                        onTest = onTestConnection,
                    )
                    Button(
                        onClick = confirm,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp),
                        shape = CircleShape,
                    ) {
                        Text(stringResource(Res.string.confirm))
                    }
                }
            }
            Text(
                text = "${stringResource(Res.string.app_name)} ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                modifier = Modifier.padding(top = 16.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun SetupConnectionTest(
    state: RemoteServerTestState,
    onTest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val statusColor = when (state) {
        RemoteServerTestState.Idle,
        RemoteServerTestState.Testing -> MaterialTheme.colorScheme.onSurfaceVariant
        is RemoteServerTestState.Success -> MaterialTheme.colorScheme.primary
        RemoteServerTestState.InvalidURL,
        is RemoteServerTestState.Failure -> MaterialTheme.colorScheme.error
    }
    val message = when (state) {
        RemoteServerTestState.Idle -> null
        RemoteServerTestState.Testing -> stringResource(Res.string.connecting)
        RemoteServerTestState.InvalidURL -> stringResource(Res.string.remote_server_url_invalid)
        is RemoteServerTestState.Success -> stringResource(Res.string.remote_test_success, state.version)
        is RemoteServerTestState.Failure -> stringResource(Res.string.connection_test_error, state.message)
    }
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        TextButton(
            onClick = onTest,
            enabled = state != RemoteServerTestState.Testing,
            modifier = Modifier.semantics { message?.let { stateDescription = it } },
        ) {
            Text(stringResource(Res.string.connection_test))
            Spacer(
                Modifier
                    .padding(start = 8.dp)
                    .size(8.dp)
                    .background(statusColor, CircleShape),
            )
        }
        if (message != null) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = statusColor,
                textAlign = TextAlign.Center,
            )
        }
    }
}
