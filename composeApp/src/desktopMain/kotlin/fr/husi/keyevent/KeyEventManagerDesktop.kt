package fr.husi.keyevent

import androidx.compose.ui.input.key.KeyEvent

class KeyEventManagerDesktop : KeyEventManager {

    private val consumers = mutableListOf<KeyEventConsumer>()

    override fun register(consumer: KeyEventConsumer) {
        if (!consumers.contains(consumer)) {
            consumers.add(consumer)
        }
    }

    override fun unregister(consumer: KeyEventConsumer) {
        consumers.remove(consumer)
    }

    fun dispatch(event: KeyEvent): Boolean {
        for (i in consumers.size - 1 downTo 0) {
            if (consumers[i](event)) return true
        }
        return false
    }

}