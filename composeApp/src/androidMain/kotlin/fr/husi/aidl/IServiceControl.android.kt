package fr.husi.aidl

import android.os.IInterface

actual interface IServiceControl : IInterface {
    actual fun getStatus(): ServiceStatus
    actual fun registerObserver(observer: IServiceObserver?)
    actual fun unregisterObserver(observer: IServiceObserver?)
}
