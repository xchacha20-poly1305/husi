package io.nekohasekai.sagernet.aidl

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class Connection(
    val uuid: String = "",
    val inbound: String = "",
    val ipVersion: Short? = null,
    val network: String = "",
    var uploadTotal: Long = 0L,
    var downloadTotal: Long = 0L,
    val start: String = "",
    val src: String = "",
    val dst: String = "",
    val host: String = "",
    val matchedRule: String = "",
    val outbound: String = "",
    val chain: String = "",
    val protocol: String? = null,
    val process: String? = null,
) : Parcelable