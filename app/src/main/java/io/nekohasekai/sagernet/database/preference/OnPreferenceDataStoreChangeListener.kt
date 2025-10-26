package io.nekohasekai.sagernet.database.preference

interface OnPreferenceDataStoreChangeListener {
    fun onPreferenceDataStoreChanged(store: PreferenceDataStore, key: String)
}
