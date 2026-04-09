package fr.husi.compose

import androidx.compose.runtime.Composable
import fr.husi.compose.material3.Icon
import fr.husi.compose.material3.Text
import fr.husi.resources.Res
import fr.husi.resources.error
import fr.husi.resources.question_mark
import fr.husi.ui.MainViewModelUiEvent
import fr.husi.ui.stringOrRes
import org.jetbrains.compose.resources.vectorResource

@Composable
fun MainViewModelAlertDialog(
    dialog: MainViewModelUiEvent.AlertDialog,
    onConsumed: () -> Unit,
) {
    ScrollableDialog(
        onDismissRequest = {
            dialog.onDismiss?.invoke()
            onConsumed()
        },
        confirmButton = {
            TextButton(stringOrRes(dialog.confirmButton.label)) {
                dialog.confirmButton.onClick()
                onConsumed()
            }
        },
        dismissButton = dialog.dismissButton?.let { button ->
            {
                TextButton(stringOrRes(button.label)) {
                    button.onClick()
                    onConsumed()
                }
            }
        },
        icon = {
            Icon(
                vectorResource(
                    if (dialog.dismissButton != null) {
                        Res.drawable.question_mark
                    } else {
                        Res.drawable.error
                    },
                ),
                null,
            )
        },
        title = { Text(stringOrRes(dialog.title)) },
        text = { Text(stringOrRes(dialog.message)) },
    )
}
