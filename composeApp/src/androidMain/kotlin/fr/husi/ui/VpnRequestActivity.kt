package fr.husi.ui

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContract
import androidx.core.content.getSystemService
import fr.husi.Key
import fr.husi.bg.VpnRequestNotification
import fr.husi.database.DataStore
import fr.husi.ktx.Logs
import fr.husi.ktx.broadcastReceiver
import fr.husi.ktx.showToast
import fr.husi.repository.resolveRepository
import fr.husi.resources.Res
import fr.husi.resources.vpn_permission_denied
import kotlinx.coroutines.runBlocking

class VpnRequestActivity : PrivacyModeActivity() {
    private var receiver: BroadcastReceiver? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The user is here now, so whatever asked them to come is done.
        VpnRequestNotification.cancel(this)
        if (getSystemService<KeyguardManager>()!!.isKeyguardLocked) {
            receiver = broadcastReceiver { _, _ -> connect.launch(null) }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(
                    receiver,
                    IntentFilter(Intent.ACTION_USER_PRESENT),
                    RECEIVER_NOT_EXPORTED,
                )
            } else {
                registerReceiver(receiver, IntentFilter(Intent.ACTION_USER_PRESENT))
            }
        } else connect.launch(null)
    }

    private val connect = registerForActivityResult(StartService()) {
        if (it) {
            val text = runBlocking { resolveRepository().getString(Res.string.vpn_permission_denied) }
            showToast(text, long = true)
        }
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (receiver != null) unregisterReceiver(receiver)
    }

    class StartService : ActivityResultContract<Void?, Boolean>() {
        private var cachedIntent: Intent? = null

        override fun getSynchronousResult(
            context: Context,
            input: Void?,
        ): SynchronousResult<Boolean>? {
            if (DataStore.serviceMode == Key.MODE_VPN) VpnService.prepare(context)?.let { intent ->
                cachedIntent = intent
                return null
            }
            resolveRepository().startService()
            return SynchronousResult(false)
        }

        override fun createIntent(context: Context, input: Void?) =
            cachedIntent!!.also { cachedIntent = null }

        override fun parseResult(resultCode: Int, intent: Intent?) =
            if (resultCode == RESULT_OK) {
                resolveRepository().startService()
                false
            } else {
                Logs.e("Failed to start VpnService: $intent")
                true
            }
    }


}
