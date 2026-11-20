package fr.husi

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import fr.husi.lib.R
import fr.husi.repository.resolveRepository
import fr.husi.resources.Res
import fr.husi.resources.quick_toggle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class CreateQuickToggleShortcutActivity : Activity() {

    private val scope = CoroutineScope(Dispatchers.Main.immediate)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        scope.launch {
            val shortLabel = resolveRepository().getString(Res.string.quick_toggle)
            setResult(
                RESULT_OK,
                ShortcutManagerCompat.createShortcutResultIntent(
                    this@CreateQuickToggleShortcutActivity,
                    ShortcutInfoCompat.Builder(
                        this@CreateQuickToggleShortcutActivity,
                        QuickToggleActivity.SHORTCUT_ID_TOGGLE,
                    )
                        .setIntent(
                            Intent(
                                this@CreateQuickToggleShortcutActivity,
                                QuickToggleActivity::class.java,
                            ).setAction(Intent.ACTION_MAIN),
                        )
                        .setIcon(
                            IconCompat.createWithResource(
                                this@CreateQuickToggleShortcutActivity,
                                R.drawable.ic_qu_shadowsocks_launcher,
                            ),
                        )
                        .setShortLabel(shortLabel)
                        .build(),
                ),
            )
            finish()
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
