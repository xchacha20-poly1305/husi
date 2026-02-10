package fr.husi.database

internal expect object SagerDatabaseProvider {
    fun create(): SagerDatabase
}
