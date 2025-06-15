package io.nekohasekai.sagernet.aidl;

import io.nekohasekai.sagernet.aidl.ISagerNetServiceCallback;
import io.nekohasekai.sagernet.aidl.ProxySet;
import io.nekohasekai.sagernet.aidl.URLTestResult;

interface ISagerNetService {
  int getState();
  String getProfileName();

  void registerCallback(in ISagerNetServiceCallback cb, int id);
  oneway void unregisterCallback(in ISagerNetServiceCallback cb);

  int urlTest(String tag);

  oneway void enableDashboardStatus(boolean enable);
  oneway void closeConnection(String id);
  oneway void resetNetwork();
  List<String> getClashModes();
  String getClashMode();
  oneway void setClashMode(String mode);

  List<ProxySet> queryProxySet();
  boolean groupSelect(String group, String proxy);
  URLTestResult groupURLTest(String tag, int timeout);
}
