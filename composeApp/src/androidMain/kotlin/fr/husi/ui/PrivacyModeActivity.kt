package fr.husi.ui

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import fr.husi.database.DataStore
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

open class PrivacyModeActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        applyPrivacyMode(DataStore.privacyMode.getBlocking())
        super.onCreate(savedInstanceState)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                DataStore.privacyMode.flow()
                    .collectLatest(::applyPrivacyMode)
            }
        }
    }

    override fun onStart() {
        applyPrivacyMode(DataStore.privacyMode.getBlocking())
        super.onStart()
    }

    private fun applyPrivacyMode(enabled: Boolean) {
        if (enabled) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            setRecentsScreenshotEnabled(!enabled)
        }
    }
}
