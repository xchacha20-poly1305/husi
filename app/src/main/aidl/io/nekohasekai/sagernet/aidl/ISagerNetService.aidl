package io.nekohasekai.sagernet.aidl;

import io.nekohasekai.sagernet.aidl.ISagerNetServiceCallback;

interface ISagerNetService {
  int getState();
  String getProfileName();

  void registerCallback(in ISagerNetServiceCallback cb, int id);
  oneway void unregisterCallback(in ISagerNetServiceCallback cb);

  int urlTest();

  oneway void enableDashboardStatus(boolean enable);
  oneway void closeConnection(String id);
  oneway void resetNetwork();
  List<String> getClashModes();
  String getClashMode();
  oneway void setClashMode(String mode);
}
