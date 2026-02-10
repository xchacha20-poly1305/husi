package fr.husi.libcore

expect fun createBoxService(isBgProcess: Boolean): Service?

expect fun loadCA(provider: Int)