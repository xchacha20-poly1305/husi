package fr.husi.keyevent

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.input.key.KeyEvent

typealias KeyEventConsumer = (KeyEvent) -> Boolean

interface KeyEventManager {
    fun register(consumer: KeyEventConsumer)
    fun unregister(consumer: KeyEventConsumer)
}

object NopKeyEventManager : KeyEventManager {
    override fun register(consumer: KeyEventConsumer) {}

    override fun unregister(consumer: KeyEventConsumer) {}
}

val LocalKeyEventManager = staticCompositionLocalOf<KeyEventManager> {
    NopKeyEventManager
}
