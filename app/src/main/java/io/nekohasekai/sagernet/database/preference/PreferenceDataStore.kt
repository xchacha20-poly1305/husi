package io.nekohasekai.sagernet.database.preference

interface PreferenceDataStore {
    fun getBoolean(key: String, defValue: Boolean): Boolean
    fun getFloat(key: String, defValue: Float): Float
    fun getInt(key: String, defValue: Int): Int
    fun getLong(key: String, defValue: Long): Long
    fun getString(key: String, defValue: String?): String?
    fun getStringSet(key: String, defValue: MutableSet<String>?): MutableSet<String>?

    fun putBoolean(key: String, value: Boolean)
    fun putFloat(key: String, value: Float)
    fun putInt(key: String, value: Int)
    fun putLong(key: String, value: Long)
    fun putString(key: String, value: String?)
    fun putStringSet(key: String, values: MutableSet<String>?)
}
