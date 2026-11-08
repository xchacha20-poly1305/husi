package fr.husi.ui

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * One navigation request from a notification, on either platform.
 *
 * The channel is buffered, so a click that arrives before the window is composed is kept
 * until [MainScreen] collects it. It does not replay, so hiding and showing the window
 * does not navigate again.
 */
object PendingNavigation {
    private val pending = Channel<NavRoutes>(capacity = Channel.BUFFERED)

    val routes: Flow<NavRoutes> = pending.receiveAsFlow()

    fun request(route: NavRoutes) {
        pending.trySend(route)
    }
}
