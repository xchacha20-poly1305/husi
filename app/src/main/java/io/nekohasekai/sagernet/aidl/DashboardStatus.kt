package io.nekohasekai.sagernet.aidl

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
class DashboardStatus(
    val connections: List<Connection>,
    val memory: Long,
    val goroutines: Int,
) : Parcelable