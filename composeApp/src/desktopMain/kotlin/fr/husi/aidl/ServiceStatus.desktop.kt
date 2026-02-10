package fr.husi.aidl

actual data class ServiceStatus actual constructor(
    actual val state: Int,
    actual val profileName: String?,
    actual val started: Boolean,
    actual val connected: Boolean,
)
