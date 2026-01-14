package io.nekohasekai.sagernet.aidl;

import io.nekohasekai.sagernet.aidl.IServiceObserver;
import io.nekohasekai.sagernet.aidl.ServiceStatus;

interface IServiceControl {
    ServiceStatus getStatus();
    void registerObserver(in IServiceObserver observer);
    void unregisterObserver(in IServiceObserver observer);
}
