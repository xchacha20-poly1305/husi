package fr.husi.database

import fr.husi.Key
import fr.husi.database.preference.DataStorePreferenceDataStore
import fr.husi.database.preference.boolean
import fr.husi.database.preference.int
import fr.husi.database.preference.long
import fr.husi.database.preference.string

open class ApplicationPreferences(
    val configurationStore: DataStorePreferenceDataStore,
    defaultTheme: () -> Int,
) {
    val activeRemoteServerId = configurationStore.long(Key.ACTIVE_REMOTE_SERVER_ID)
    val appTheme = configurationStore.int(Key.APP_THEME, defaultTheme)
    val nightTheme = configurationStore.int(Key.NIGHT_THEME)
    val appLanguage = configurationStore.string(Key.APP_LANGUAGE)
    val speedInterval = configurationStore.int(Key.SPEED_INTERVAL) { 1000 }
    val logTerminalMode = configurationStore.boolean(Key.LOG_TERMINAL_MODE) { false }
    val dashboardWidgets = configurationStore.string(Key.DASHBOARD_WIDGETS)
}
