package fr.husi.aidl

actual interface IServiceObserver {
    actual fun onState(status: ServiceStatus)
    actual fun onSpeed(speed: SpeedDisplayData)
    actual fun onAlert(type: Int, message: String)
}
