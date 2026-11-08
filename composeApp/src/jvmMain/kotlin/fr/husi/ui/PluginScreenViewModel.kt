package fr.husi.ui

import androidx.compose.runtime.Immutable
import fr.husi.fmt.PluginEntry

@Immutable
data class PluginDisplay(
    val id: String,
    val packageName: String,
    val version: String,
    val versionCode: Long,
    val provider: String,
    val entry: PluginEntry? = null,
    val path: String? = null,
)
