package fr.husi.aidl

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
actual data class ServiceStatus actual constructor(
    actual val state: Int,
    actual val profileName: String?,
    actual val started: Boolean,
    actual val connected: Boolean,
) : Parcelable
