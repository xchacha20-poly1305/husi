package io.nekohasekai.sagernet.database.preference

import android.content.Context
import androidx.datastore.core.DataMigration
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import io.nekohasekai.sagernet.Key
import io.nekohasekai.sagernet.ktx.Logs

class RoomToDataStoreMigration(
    private val context: Context,
) : DataMigration<Preferences> {

    override suspend fun shouldMigrate(currentData: Preferences): Boolean {
        // Migrate once; before this runs DataStore is empty so no existing keys get clobbered.
        return currentData[booleanPreferencesKey(MIGRATION_KEY)] != true
    }

    override suspend fun migrate(currentData: Preferences): Preferences {
        val prefs = currentData.toMutablePreferences()

        return try {
            val dbFile = context.getDatabasePath(Key.DB_PUBLIC)
            if (!dbFile.exists()) {
                Logs.i("no old database found, skipping migration")
                prefs[booleanPreferencesKey(MIGRATION_KEY)] = true
                return prefs.toPreferences()
            }

            val kvPairs = PublicDatabase.kvPairDao.all()
            Logs.i("migrating ${kvPairs.size} preferences from Room to DataStore")

            /**
             * Legacy compatibility workaround for stringToInt delegate migration.
             *
             * These preference keys previously used the `stringToInt` delegate, which stored
             * integer values as strings in the database (e.g., "9000", "1"). After removing
             * androidx.preference dependency, these fields now use the `int` delegate, which
             * expects Long type in DataStore.
             *
             * We need to convert these String values to Long during migration to prevent
             * "String cannot be cast to Long" errors.
             */
            val legacyStringToIntKeys = setOf(
                Key.NIGHT_THEME,
                Key.NETWORK_INTERFACE_STRATEGY,
                Key.MTU,
                Key.SPEED_INTERVAL,
                Key.RULES_PROVIDER,
                Key.LOG_LEVEL,
                Key.LOG_MAX_SIZE,
                Key.PROVIDER_HYSTERIA2,
                Key.PROVIDER_JUICITY,
                Key.TUN_IMPLEMENTATION,
                Key.CERT_PROVIDER,
                Key.NTP_PORT,
                Key.UPLOAD_SPEED,
                Key.DOWNLOAD_SPEED,

                // In legacy code, they are not use stringToInt and use int.
                Key.CONNECTION_TEST_CONCURRENT,
                Key.CONNECTION_TEST_TIMEOUT,
            )

            var successCount = 0
            kvPairs.forEach { pair ->
                try {
                    @Suppress("DEPRECATION")
                    when (pair.valueType) {
                        KeyValuePair.TYPE_BOOLEAN -> {
                            pair.boolean?.let {
                                prefs[booleanPreferencesKey(pair.key)] = it
                                successCount++
                            }
                        }

                        KeyValuePair.TYPE_FLOAT -> {
                            pair.float?.let {
                                prefs[floatPreferencesKey(pair.key)] = it
                                successCount++
                            }
                        }

                        KeyValuePair.TYPE_LONG,
                        KeyValuePair.TYPE_INT,
                            -> {
                            pair.long?.let {
                                prefs[longPreferencesKey(pair.key)] = it
                                successCount++
                            }
                        }

                        KeyValuePair.TYPE_STRING -> {
                            pair.string?.let { stringValue ->
                                // Legacy stringToInt workaround: convert String to Long
                                if (pair.key in legacyStringToIntKeys) {
                                    stringValue.toLongOrNull()?.let { longValue ->
                                        prefs[longPreferencesKey(pair.key)] = longValue
                                        successCount++
                                    } ?: run {
                                        Logs.w("Failed to convert legacy stringToInt value for key ${pair.key}: $stringValue")
                                    }
                                } else {
                                    prefs[stringPreferencesKey(pair.key)] = stringValue
                                    successCount++
                                }
                            }
                        }

                        KeyValuePair.TYPE_STRING_SET -> {
                            pair.stringSet?.let {
                                prefs[stringSetPreferencesKey(pair.key)] = it
                                successCount++
                            }
                        }

                        else -> {
                            Logs.w("Unknown type ${pair.valueType} for key ${pair.key}")
                        }
                    }
                } catch (e: Exception) {
                    Logs.e("Failed to migrate key ${pair.key}", e)
                }
            }

            Logs.i("Successfully migrated $successCount/${kvPairs.size} preferences")

            prefs[booleanPreferencesKey(MIGRATION_KEY)] = true

            prefs.toPreferences()
        } catch (e: Exception) {
            Logs.e("Migration failed", e)
            prefs[booleanPreferencesKey(MIGRATION_KEY)] = true
            prefs.toPreferences()
        }
    }

    override suspend fun cleanUp() {
        try {
            val dbFile = context.getDatabasePath(Key.DB_PUBLIC)
            if (PublicDatabase.hasInstance()) {
                try {
                    PublicDatabase.instance.close()
                } catch (closeException: Exception) {
                    Logs.w("Failed to close old Room database", closeException)
                } finally {
                    PublicDatabase.clearInstance()
                }
            }
            if (dbFile.exists()) {
                if (dbFile.delete()) {
                    Logs.i("Cleaned up old Room database")
                } else {
                    Logs.w("Failed to delete old Room database file")
                }
            }
        } catch (e: Exception) {
            Logs.e("Failed to clean up old database", e)
        }
    }

    companion object {
        const val MIGRATION_KEY = "__datastore_migrated_from_room__"
    }
}
