package fr.husi.ui.configuration

import androidx.compose.runtime.Composable
import fr.husi.compose.DropdownMenuAction

@Composable
internal expect fun scannerMenuAction(onDismissMenu: () -> Unit): DropdownMenuAction?
