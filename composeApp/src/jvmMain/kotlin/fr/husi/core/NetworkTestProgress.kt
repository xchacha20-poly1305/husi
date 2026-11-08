package fr.husi.core

import fr.husi.proto.daemon.NetworkQualityTestProgress
import fr.husi.proto.daemon.STUNTestProgress

val STUNTestProgress.failure: String?
    get() = error.takeIf { isFinal && it.isNotEmpty() }

val NetworkQualityTestProgress.failure: String?
    get() = error.takeIf { isFinal && it.isNotEmpty() }
