package fr.husi.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogModalityType
import androidx.compose.ui.window.v2.DialogWindow
import androidx.compose.ui.window.v2.rememberDialogStateWithBounds
import fr.husi.compose.theme.AppTheme
import fr.husi.resources.Res
import fr.husi.resources.ic_service_active
import fr.husi.ui.LocalSnackbarEmitter
import fr.husi.ui.SnackbarEmitter
import fr.husi.ui.SnackbarEmitterEffect
import org.jetbrains.compose.resources.painterResource

@OptIn(ExperimentalComposeUiApi::class)
@Composable
actual fun WindowedDialog(
    onDismissRequest: () -> Unit,
    title: String,
    windowSize: DpSize,
    buttons: @Composable RowScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    DialogWindow(
        onCloseRequest = onDismissRequest,
        state = rememberDialogStateWithBounds(initialSize = windowSize),
        title = title,
        icon = painterResource(Res.drawable.ic_service_active),
        modalityType = DialogModalityType.Modeless,
        onPreviewKeyEvent = { event ->
            if (event.type == KeyEventType.KeyDown && event.key == Key.Escape) {
                onDismissRequest()
                true
            } else {
                false
            }
        },
    ) {
        AppTheme {
            val snackbarEmitter = remember { SnackbarEmitter() }
            val snackbarHostState = remember { SnackbarHostState() }
            SnackbarEmitterEffect(snackbarEmitter, snackbarHostState)
            CompositionLocalProvider(LocalSnackbarEmitter provides snackbarEmitter) {
                Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { innerPadding ->
                    Column(Modifier.fillMaxSize().padding(innerPadding)) {
                        Column(Modifier.weight(1f).padding(top = 16.dp)) {
                            content()
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp, vertical = 16.dp),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            buttons()
                        }
                    }
                }
            }
        }
    }
}
