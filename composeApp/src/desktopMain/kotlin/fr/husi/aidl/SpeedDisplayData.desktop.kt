package fr.husi.aidl

actual data class SpeedDisplayData actual constructor(
    // Bytes per second
    actual var txRateProxy: Long,
    actual var rxRateProxy: Long,
    actual var txRateDirect: Long,
    actual var rxRateDirect: Long,

    // Bytes for the current session
    // Outbound "bypass" usage is not counted
    actual var txTotal: Long,
    actual var rxTotal: Long,
)
