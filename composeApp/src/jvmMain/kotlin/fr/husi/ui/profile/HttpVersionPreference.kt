package fr.husi.ui.profile

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.AnnotatedString
import fr.husi.compose.IconMaskColors
import fr.husi.compose.ListPreference
import fr.husi.compose.MaskedIcon
import fr.husi.compose.material3.Text
import fr.husi.fmt.HttpVersion
import fr.husi.resources.Res
import fr.husi.resources.nfc
import fr.husi.resources.protocol_version
import me.zhanghai.compose.preference.ListPreferenceType
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun HttpVersionPreference(
    value: Int,
    isTLS: Boolean,
    onValueChange: (Int) -> Unit,
) {
    val httpVersions = remember(isTLS) { HttpVersion.supported(isTLS) }
    ListPreference(
        value = value,
        values = httpVersions,
        onValueChange = onValueChange,
        title = { Text(stringResource(Res.string.protocol_version)) },
        icon = {
            MaskedIcon(Res.drawable.nfc, IconMaskColors.IconLightBlue)
        },
        summary = { Text(displayHttpVersion(value)) },
        type = ListPreferenceType.DROPDOWN_MENU,
        valueToText = { AnnotatedString(displayHttpVersion(it)) },
    )
}

internal fun displayHttpVersion(version: Int) = when (version) {
    HttpVersion.HTTP_1 -> "HTTP/1.1"
    HttpVersion.HTTP_2 -> "HTTP/2"
    HttpVersion.HTTP_3 -> "HTTP/3"
    else -> "HTTP/$version"
}
