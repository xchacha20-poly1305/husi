package fr.husi.aidl

import android.os.IInterface

actual interface IServiceObserver : IInterface {
    actual fun onState(status: ServiceStatus)
    actual fun onSpeed(speed: SpeedDisplayData)
    actual fun onAlert(type: Int, message: String)
}
