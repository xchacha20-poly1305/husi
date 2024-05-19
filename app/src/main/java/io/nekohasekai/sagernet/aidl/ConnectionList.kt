package io.nekohasekai.sagernet.aidl

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
class ConnectionList(
    var connections: List<Connection> = listOf()
) : Parcelable