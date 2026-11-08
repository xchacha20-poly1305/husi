package fr.husi.database.preference

import androidx.datastore.preferences.core.stringPreferencesKey
import fr.husi.database.callingUserIndex
import fr.husi.ktx.parsePort
import kotlinx.coroutines.flow.map

fun DataStorePreferenceDataStore.port(
    name: String,
    defaultValue: Int,
): PreferenceProxy<Int> {
    val preferenceKey = stringPreferencesKey(name)
    // 0 means "let the OS auto-assign an ephemeral port" and must stay 0
    // regardless of the calling user; only offset non-zero defaults to
    // avoid port collisions between multiple Android user profiles.
    val userIndex = callingUserIndex()
    val effectiveDefault = if (defaultValue == 0) {
        0
    } else {
        defaultValue + userIndex
    }
    return PreferenceProxy(
        key = name,
        defaultValue = { effectiveDefault },
        read = {
            val raw = readValue(preferenceKey)
            if (raw == null) {
                null
            } else {
                parsePort(raw, effectiveDefault)
            }
        },
        write = { writeValue(preferenceKey, "$it") },
        observe = {
            valueFlow(preferenceKey).map { raw ->
                if (raw == null) {
                    null
                } else {
                    parsePort(raw, effectiveDefault)
                }
            }
        },
        updateAtomic = { transform ->
            updateValue(preferenceKey) { current ->
                val currentPort = if (current == null) {
                    effectiveDefault
                } else {
                    parsePort(current, effectiveDefault)
                }
                "${transform(currentPort)}"
            }
        },
    )
}
