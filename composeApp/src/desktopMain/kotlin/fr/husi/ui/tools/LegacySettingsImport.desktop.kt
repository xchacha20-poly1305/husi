package fr.husi.ui.tools

internal actual suspend fun importLegacySettingPairs(rawSettings: Any) {
    throw UnsupportedOperationException("Legacy settings import not supported on desktop")
}
