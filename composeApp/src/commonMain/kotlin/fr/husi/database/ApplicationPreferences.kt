package fr.husi.database

import fr.husi.Key
import fr.husi.NETWORK_QUALITY_CONFIG_URL
import fr.husi.TrafficSortMode
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
    val trafficDescending = configurationStore.boolean(Key.TRAFFIC_DESCENDING) { false }
    val trafficSortMode = configurationStore.int(Key.TRAFFIC_SORT_MODE) { TrafficSortMode.START }
    val trafficConnectionQuery = configurationStore.int(Key.TRAFFIC_CONNECTION_QUERY) { 1 shl 0 }
    val networkQualityConfigUrl = configurationStore.string(Key.NETWORK_QUALITY_CONFIG_URL) { NETWORK_QUALITY_CONFIG_URL }
    val networkQualitySerial = configurationStore.boolean(Key.NETWORK_QUALITY_SERIAL) { false }
    val networkQualityMaxRuntime = configurationStore.int(Key.NETWORK_QUALITY_MAX_RUNTIME) { 30 }
    val networkQualityHttp3 = configurationStore.boolean(Key.NETWORK_QUALITY_HTTP3) { false }
    val proxySetOrder = configurationStore.int(Key.PROXY_SET_ORDER)
}
