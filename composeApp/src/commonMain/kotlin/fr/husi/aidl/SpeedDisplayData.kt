package fr.husi.aidl

expect class SpeedDisplayData(
    txRateProxy: Long = 0L,
    rxRateProxy: Long = 0L,
    txRateDirect: Long = 0L,
    rxRateDirect: Long = 0L,
    txTotal: Long = 0L,
    rxTotal: Long = 0L,
) {
    var txRateProxy: Long
    var rxRateProxy: Long
    var txRateDirect: Long
    var rxRateDirect: Long
    var txTotal: Long
    var rxTotal: Long
}
