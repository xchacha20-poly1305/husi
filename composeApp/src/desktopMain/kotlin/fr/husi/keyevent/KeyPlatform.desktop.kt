package fr.husi.keyevent

import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import fr.husi.repository.repo

actual val KeyEvent.isTypeControlPressed: Boolean
    get() = if (repo.isMacOs) isMetaPressed else isCtrlPressed