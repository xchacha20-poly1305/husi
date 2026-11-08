package fr.husi.ui.log

import androidx.compose.runtime.Immutable

@Immutable data class TerminalLogEntry<out Level>(val level: Level, val message: String)
