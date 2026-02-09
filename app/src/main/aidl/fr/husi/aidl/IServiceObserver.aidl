package fr.husi.aidl;

import fr.husi.aidl.ServiceStatus;
import fr.husi.aidl.SpeedDisplayData;

interface IServiceObserver {
    oneway void onState(in ServiceStatus status);
    oneway void onSpeed(in SpeedDisplayData speed);
    oneway void onAlert(int type, String message);
}
