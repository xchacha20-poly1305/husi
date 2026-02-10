package fr.husi.aidl

actual interface IServiceControl {
    actual fun getStatus(): ServiceStatus
    actual fun registerObserver(observer: IServiceObserver?)
    actual fun unregisterObserver(observer: IServiceObserver?)
}
