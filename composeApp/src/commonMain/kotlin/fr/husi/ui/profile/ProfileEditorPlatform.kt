package fr.husi.ui.profile

import androidx.compose.runtime.Composable
import fr.husi.database.ProxyEntity

@Composable
internal expect fun platformSupportShortcut(): Boolean

@Composable
internal expect fun ShortcutMenuItem(entity: ProxyEntity, postClick: () -> Unit)