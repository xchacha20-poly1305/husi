package fr.husi.repository

import fr.husi.libcore.Libcore

internal interface SystemProxyBackend {
    fun enable(host: String, port: Int)
    fun disable()
}

internal object LibcoreSystemProxyBackend : SystemProxyBackend {
    override fun enable(host: String, port: Int) {
        Libcore.setSystemProxy(host, port)
    }

    override fun disable() {
        Libcore.clearSystemProxy()
    }
}
