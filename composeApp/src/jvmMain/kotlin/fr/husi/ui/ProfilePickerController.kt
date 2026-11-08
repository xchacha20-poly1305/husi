package fr.husi.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class ProfilePickerController {
    var session by mutableStateOf<ProfilePickerSession?>(null)
        private set

    fun open(
        preSelected: Long?,
        onSelected: (Long) -> Unit,
    ) {
        session = ProfilePickerSession(
            preSelected = preSelected,
            onSelected = onSelected,
        )
    }

    fun dismiss() {
        session = null
    }

    fun select(id: Long) {
        session?.onSelected?.invoke(id)
        dismiss()
    }
}

data class ProfilePickerSession(
    val preSelected: Long?,
    val onSelected: (Long) -> Unit,
)
