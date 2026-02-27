package fr.husi.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.CompositionLocalProvider
import androidx.lifecycle.lifecycleScope
import fr.husi.Key
import fr.husi.compose.theme.AppTheme
import fr.husi.database.DataStore
import fr.husi.permission.LocalPermissionPlatform
import fr.husi.permission.rememberAndroidPermissionPlatform
import fr.husi.service.ServiceConnector
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

class MainActivity : ComposeActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        ServiceConnector.connect()
        lifecycleScope.launch {
            DataStore.configurationStore.stringFlow(Key.SERVICE_MODE, Key.MODE_VPN)
                .drop(1)
                .collect {
                    ServiceConnector.reconnect()
                }
        }

        when (intent.action) {
            Intent.ACTION_VIEW -> onNewIntent(intent)
            Intent.ACTION_PROCESS_TEXT -> {
                viewModel.parseProxy(intent.getStringExtra(Intent.EXTRA_PROCESS_TEXT))
            }

            else -> {}
        }

        setContent {
            AppTheme {
                val permissionPlatform = rememberAndroidPermissionPlatform()
                CompositionLocalProvider(
                    LocalPermissionPlatform provides permissionPlatform,
                ) {
                    MainScreen(
                        viewModel = viewModel,
                        moveToBackground = { moveTaskToBack(true) },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)

        val uri = intent.data ?: return
        viewModel.importFromUri(uri.toString())
    }

    override fun onStart() {
        ServiceConnector.updateConnectionId(ServiceConnector.connectionIdMainActivityForeground)
        super.onStart()
    }

    override fun onStop() {
        ServiceConnector.updateConnectionId(ServiceConnector.connectionIdMainActivityBackground)
        super.onStop()
    }

    override fun onDestroy() {
        super.onDestroy()
        ServiceConnector.disconnect()
    }

}
