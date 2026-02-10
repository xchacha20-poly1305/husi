package fr.husi.bg

import java.net.InetAddress

actual suspend fun resolveByDefaultNetwork(host: String): List<InetAddress> =
    InetAddress.getAllByName(host).filterNotNull()
