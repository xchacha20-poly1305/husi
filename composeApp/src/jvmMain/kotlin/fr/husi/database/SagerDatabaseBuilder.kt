package fr.husi.database

/**
 * The object wrapper is must.
 * Otherwise, "[MissingType]: Element 'fr.husi.database.SagerDatabase' references a type that is not present"
 */
internal expect object SagerDatabaseProvider {
    fun create(): SagerDatabase
}
