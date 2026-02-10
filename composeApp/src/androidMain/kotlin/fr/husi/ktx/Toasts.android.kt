package fr.husi.ktx

import android.widget.Toast
import fr.husi.repository.androidRepo

actual fun showToast(message: String, long: Boolean) {
    val duration = if (long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT
    runOnMainDispatcher {
        Toast.makeText(androidRepo.context, message, duration).show()
    }
}
