package io.nekohasekai.sagernet.aidl;

import io.nekohasekai.sagernet.aidl.ServiceStatus;

interface IServiceObserver {
    oneway void onState(in ServiceStatus status);
    oneway void onAlert(int type, String message);
}
