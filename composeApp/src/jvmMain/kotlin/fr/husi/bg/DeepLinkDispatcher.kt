package fr.husi.bg

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow

object DeepLinkDispatcher {
    private val deepLinks = Channel<String>(Channel.BUFFERED)

    val flow = deepLinks.receiveAsFlow()

    fun emit(deepLink: String) {
        if (deepLink.isBlank()) return
        deepLinks.trySend(deepLink)
    }
}
