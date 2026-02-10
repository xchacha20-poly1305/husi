package fr.husi.ui.profile

import android.content.Intent
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import fr.husi.QuickToggleShortcut
import fr.husi.database.ProxyEntity
import fr.husi.lib.R
import fr.husi.resources.Res
import fr.husi.resources.create_shortcut
import org.jetbrains.compose.resources.stringResource

@Composable
internal actual fun platformSupportShortcut(): Boolean {
    val context = LocalContext.current
    return ShortcutManagerCompat.isRequestPinShortcutSupported(context)
}

@Composable
internal actual fun ShortcutMenuItem(entity: ProxyEntity, postClick: () -> Unit) {
    val context = LocalContext.current
    DropdownMenuItem(
        text = { Text(stringResource(Res.string.create_shortcut)) },
        onClick = {
            val name = entity.displayName()
            val shortcut = ShortcutInfoCompat
                .Builder(context, "shortcut-profile-${entity.id}")
                .setShortLabel(name)
                .setLongLabel(name)
                .setIcon(
                    IconCompat.createWithResource(
                        context,
                        R.drawable.ic_qu_shadowsocks_launcher,
                    ),
                )
                .setIntent(
                    Intent(context, QuickToggleShortcut::class.java)
                        .setAction(Intent.ACTION_MAIN)
                        .putExtra(QuickToggleShortcut.EXTRA_PROFILE_ID, entity.id),
                )
                .build()
            ShortcutManagerCompat.requestPinShortcut(context, shortcut, null)
            postClick()
        },
    )
}
