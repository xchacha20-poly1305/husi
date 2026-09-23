package fr.husi.ui

import androidx.compose.runtime.Composable

/**
 * Desktop hosts the auth challenge dialogs in `DesktopMain`'s `application {}` so the windows
 * appear even while the main window is hidden in the tray.
 */
@Composable
internal actual fun MainScreenAuthChallengeDialogs(onDismissed: () -> Unit) {
}
