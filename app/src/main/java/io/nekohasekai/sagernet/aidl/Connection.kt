package io.nekohasekai.sagernet.aidl

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class Connection(
    var uuid: String = "",
    var inbound: String = "",
    var ipVersion: Short = 0,
    var network: String = "",
    var uploadTotal: Long = 0L,
    var downloadTotal: Long = 0L,
    var start: String = "",
    var src: String = "",
    var dst: String = "",
    var host: String = "",
    var matchedRule: String = "",
    var outbound: String = "",
    var chain: String = "",
) : Parcelable