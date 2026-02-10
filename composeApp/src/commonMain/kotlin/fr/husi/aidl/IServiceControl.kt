package fr.husi.aidl

expect interface IServiceControl {
    fun getStatus(): ServiceStatus
    fun registerObserver(observer: IServiceObserver?)
    fun unregisterObserver(observer: IServiceObserver?)
}
