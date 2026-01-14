package io.nekohasekai.sagernet.aidl

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class ServiceStatus(
    val state: Int = 0,
    val profileName: String? = null,
    val started: Boolean = false,
    val connected: Boolean = false,
) : Parcelable
