package fr.husi.ui.dashboard

expect suspend fun buildPlatformNetworkInfo(): Triple<List<NetworkInterfaceInfo>, String?, String?>
