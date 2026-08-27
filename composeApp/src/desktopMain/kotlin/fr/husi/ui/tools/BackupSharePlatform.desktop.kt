package fr.husi.ui.tools

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import io.github.vinceglb.filekit.PlatformFile

@Composable
internal actual fun rememberShareBackupFile(): (PlatformFile) -> Unit = remember { {} }
