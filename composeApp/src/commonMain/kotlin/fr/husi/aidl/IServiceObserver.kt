package fr.husi.aidl

expect interface IServiceObserver {
    fun onState(status: ServiceStatus)
    fun onSpeed(speed: SpeedDisplayData)
    fun onAlert(type: Int, message: String)
}
