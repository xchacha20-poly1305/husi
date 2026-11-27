package fr.husi.ui.tools

internal actual suspend fun importLegacySettingPairs(rawSettings: Any, preserveKeys: Set<String>) {
    throw UnsupportedOperationException("Legacy settings import not supported on desktop")
}
