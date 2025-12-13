package io.nekohasekai.sagernet.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import io.nekohasekai.sagernet.bg.SagerConnection
import io.nekohasekai.sagernet.compose.theme.AppTheme
import kotlinx.coroutines.launch

class MainActivity : ComposeActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        connection.connect(this)
        lifecycleScope.launch {
            connection.binderDied.collect {
                connection.reconnect(this@MainActivity)
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
                MainScreen(
                    viewModel = viewModel,
                    connection = connection,
                    exit = ::finish,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)

        val uri = intent.data ?: return
        viewModel.importFromUri(uri)
    }

    private val connection = SagerConnection(SagerConnection.CONNECTION_ID_MAIN_ACTIVITY_FOREGROUND, true)

    override fun onStart() {
        connection.updateConnectionId(SagerConnection.CONNECTION_ID_MAIN_ACTIVITY_FOREGROUND)
        super.onStart()
    }

    override fun onStop() {
        connection.updateConnectionId(SagerConnection.CONNECTION_ID_MAIN_ACTIVITY_BACKGROUND)
        super.onStop()
    }

    override fun onDestroy() {
        super.onDestroy()
        connection.disconnect(this)
    }

}
