package fr.husi.ui.remote

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import fr.husi.compose.IconMaskColors
import fr.husi.compose.ListPreference
import fr.husi.compose.MaskedIcon
import fr.husi.compose.material3.Text
import fr.husi.database.RemoteBackend
import fr.husi.resources.Res
import fr.husi.resources.remote_backend
import fr.husi.resources.remote_backend_auto
import fr.husi.resources.settings
import org.jetbrains.compose.resources.stringResource

@Composable
fun RemoteBackendPreference(
    value: RemoteBackend,
    onValueChange: (RemoteBackend) -> Unit,
    modifier: Modifier = Modifier,
) {
    ListPreference(
        modifier = modifier,
        value = value,
        onValueChange = onValueChange,
        values = RemoteBackend.entries,
        title = { Text(stringResource(Res.string.remote_backend)) },
        summary = { Text(remoteBackendLabel(value)) },
        valueToText = { AnnotatedString(remoteBackendLabel(it)) },
        icon = { MaskedIcon(Res.drawable.settings, IconMaskColors.IconLightBlue) },
    )
}

@Composable
internal fun remoteBackendLabel(value: RemoteBackend): String =
    when (value) {
        RemoteBackend.AUTO -> stringResource(Res.string.remote_backend_auto)
        RemoteBackend.SING_BOX -> "sing-box"
        RemoteBackend.CLASH -> "Clash"
    }
