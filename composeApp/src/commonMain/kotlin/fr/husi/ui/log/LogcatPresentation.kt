package fr.husi.ui.log

data class LogcatPresentation<Level>(
    val pause: Boolean,
    val searchQuery: String?,
    val logLevel: Level,
    val logs: List<TerminalLogEntry<Level>>,
    val errorMessage: String? = null,
    val connecting: Boolean = false,
)
