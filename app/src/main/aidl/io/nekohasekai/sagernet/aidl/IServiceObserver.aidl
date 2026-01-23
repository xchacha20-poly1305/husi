package io.nekohasekai.sagernet.aidl;

import io.nekohasekai.sagernet.aidl.ServiceStatus;
import io.nekohasekai.sagernet.aidl.SpeedDisplayData;

interface IServiceObserver {
    oneway void onState(in ServiceStatus status);
    oneway void onSpeed(in SpeedDisplayData speed);
    oneway void onAlert(int type, String message);
}
