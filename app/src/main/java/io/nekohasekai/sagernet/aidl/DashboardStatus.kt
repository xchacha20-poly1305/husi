package io.nekohasekai.sagernet.aidl

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * @param isStop marks traffic loop stop.
 */
@Parcelize
class DashboardStatus(
    val connections: List<Connection>,
    val memory: Long,
    val goroutines: Int,
    val isStop: Boolean,
) : Parcelable