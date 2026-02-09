package fr.husi.aidl;

import fr.husi.aidl.IServiceObserver;
import fr.husi.aidl.ServiceStatus;

interface IServiceControl {
    ServiceStatus getStatus();
    void registerObserver(in IServiceObserver observer);
    void unregisterObserver(in IServiceObserver observer);
}
