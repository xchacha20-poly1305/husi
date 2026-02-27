package fr.husi.keyevent

import androidx.compose.ui.input.key.KeyEvent

actual val KeyEvent.isTypeControlPressed: Boolean
    get() = false