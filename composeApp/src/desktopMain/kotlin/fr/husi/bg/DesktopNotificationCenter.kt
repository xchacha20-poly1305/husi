package fr.husi.bg

import dev.nucleusframework.notification.common.NotificationManager
import dev.nucleusframework.notification.common.NotificationResult
import dev.nucleusframework.notification.common.notification
import fr.husi.ktx.Logs
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

object DesktopNotificationCenter {

    private val activatedEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val activations: SharedFlow<Unit> = activatedEvents

    fun show(title: String, message: String) {
        runCatching {
            when (
                val result = notification(
                    title = title,
                    message = message,
                    onActivated = { activatedEvents.tryEmit(Unit) },
                ).send()
            ) {
                is NotificationResult.Success -> {}

                is NotificationResult.Failure -> {
                    Logs.w("show desktop notification: ${result.reason}")
                }
            }
        }.onFailure {
            Logs.w("show desktop notification", it)
        }
    }

    /** Eagerly sets up the OS notification backend (Start Menu shortcut/AUMID on Windows). */
    fun initialize() {
        runCatching {
            NotificationManager.initialize()
        }.onFailure {
            Logs.w("initialize desktop notification center", it)
        }
    }
}
