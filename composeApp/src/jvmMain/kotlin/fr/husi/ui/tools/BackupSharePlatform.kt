package fr.husi.ui.tools

import androidx.compose.runtime.Composable
import io.github.vinceglb.filekit.PlatformFile

@Composable
internal expect fun rememberShareBackupFile(): (PlatformFile) -> Unit
