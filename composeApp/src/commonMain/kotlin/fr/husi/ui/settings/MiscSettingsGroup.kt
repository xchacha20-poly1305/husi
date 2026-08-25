package fr.husi.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.AnnotatedString
import fr.husi.CertProvider
import fr.husi.compose.IconMaskColors
import fr.husi.compose.collectAsStateWithLifecycle
import fr.husi.compose.IconMaskShapes
import fr.husi.compose.LinkOrContentTextField
import fr.husi.compose.ListPreference
import fr.husi.compose.MaskedIcon
import fr.husi.compose.SliderPreference
import fr.husi.compose.SwitchPreference
import fr.husi.compose.TextFieldPreference
import fr.husi.compose.material3.Text
import fr.husi.database.DataStore
import fr.husi.ktx.contentOrUnset
import fr.husi.resources.Res
import fr.husi.resources.apps
import fr.husi.resources.cast_connected
import fr.husi.resources.cert_chrome
import fr.husi.resources.certificate_authority
import fr.husi.resources.connection_test_ignore_handshake_time
import fr.husi.resources.connection_test_unified_delay
import fr.husi.resources.connection_test_url
import fr.husi.resources.fast_forward
import fr.husi.resources.follow_system
import fr.husi.resources.mozilla
import fr.husi.resources.push_pin
import fr.husi.resources.question_mark
import fr.husi.resources.system_and_user
import fr.husi.resources.test_concurrency
import fr.husi.resources.test_timeout
import fr.husi.resources.timer
import fr.husi.ui.DisableProcessTextPreference
import fr.husi.ui.HideLauncherIconPreference
import fr.husi.ui.PlatformMiscOptions
import me.zhanghai.compose.preference.ListPreferenceType
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun MiscSettingsGroup(
    needReload: () -> Unit,
    needRestart: () -> Unit,
) {
    val connectionTestUrlValue by DataStore.connectionTestURL.collectAsStateWithLifecycle()
    TextFieldPreference(
        value = connectionTestUrlValue,
        onValueChange = { DataStore.connectionTestURL.setBlocking(it) },
        title = { Text(stringResource(Res.string.connection_test_url)) },
        textToValue = { it },
        icon = {
            MaskedIcon(
                Res.drawable.cast_connected,
                color = IconMaskColors.IconCyan,
            )
        },
        summary = { Text(contentOrUnset(connectionTestUrlValue)) },
        valueToText = { it },
    ) { value, onValueChange, onOk ->
        LinkOrContentTextField(value, onValueChange, onOk)
    }

    val connectionTestConcurrentValue by DataStore.connectionTestConcurrent.collectAsStateWithLifecycle()
    var concurrentPreview by remember { mutableFloatStateOf(connectionTestConcurrentValue.toFloat()) }
    SliderPreference(
        value = connectionTestConcurrentValue.toFloat(),
        onValueChange = { DataStore.connectionTestConcurrent.setBlocking(it.toInt()) },
        sliderValue = concurrentPreview,
        onSliderValueChange = { concurrentPreview = it },
        title = { Text(stringResource(Res.string.test_concurrency)) },
        valueRange = 1f..32f,
        valueSteps = 32,
        icon = {
            MaskedIcon(
                Res.drawable.fast_forward,
                color = IconMaskColors.IconLightGreen,
            )
        },
        valueText = { Text(concurrentPreview.toInt().toString()) },
    )

    val connectionTestTimeoutValue by DataStore.connectionTestTimeout.collectAsStateWithLifecycle()
    var timeoutPreview by remember { mutableFloatStateOf(connectionTestTimeoutValue.toFloat()) }
    SliderPreference(
        value = connectionTestTimeoutValue.toFloat(),
        onValueChange = { DataStore.connectionTestTimeout.setBlocking(it.toInt()) },
        sliderValue = timeoutPreview,
        onSliderValueChange = { timeoutPreview = it },
        title = { Text(stringResource(Res.string.test_timeout)) },
        valueRange = 1024f..8192f,
        valueSteps = 20,
        icon = {
            MaskedIcon(Res.drawable.apps, color = IconMaskColors.IconWarmGray)
        },
        valueText = { Text(timeoutPreview.toInt().toString()) },
    )

    val connectionTestUnifiedDelay by DataStore.connectionTestUnifiedDelay.collectAsStateWithLifecycle()
    SwitchPreference(
        value = connectionTestUnifiedDelay,
        onValueChange = {
            DataStore.connectionTestUnifiedDelay.setBlocking(it)
            needReload()
        },
        title = { Text(stringResource(Res.string.connection_test_unified_delay)) },
        icon = {
            MaskedIcon(Res.drawable.timer, IconMaskColors.IconLightGreen)
        },
    )

    val connectionTestIgnoreHandshakeTime by DataStore.connectionTestIgnoreHandshakeTime.collectAsStateWithLifecycle()
    SwitchPreference(
        value = connectionTestIgnoreHandshakeTime,
        onValueChange = {
            DataStore.connectionTestIgnoreHandshakeTime.setBlocking(it)
            needReload()
        },
        title = { Text(stringResource(Res.string.connection_test_ignore_handshake_time)) },
        icon = {
            MaskedIcon(Res.drawable.question_mark, IconMaskColors.IconLightGreen)
        },
    )
    PlatformMiscOptions(needReload)

    val certProviderValue by DataStore.certProvider.collectAsStateWithLifecycle()

    fun certProviderTextRes(index: Int): StringResource = when (index) {
        CertProvider.SYSTEM -> Res.string.follow_system
        CertProvider.MOZILLA -> Res.string.mozilla
        CertProvider.SYSTEM_AND_USER -> Res.string.system_and_user
        CertProvider.CHROME -> Res.string.cert_chrome
        else -> Res.string.mozilla
    }
    ListPreference(
        value = certProviderValue,
        onValueChange = {
            DataStore.certProvider.setBlocking(it)
            needRestart()
        },
        values = listOf(
            CertProvider.SYSTEM,
            CertProvider.MOZILLA,
            CertProvider.SYSTEM_AND_USER,
            CertProvider.CHROME,
        ),
        title = { Text(stringResource(Res.string.certificate_authority)) },
        icon = {
            MaskedIcon(
                Res.drawable.push_pin,
                color = IconMaskColors.IconCoral,
                shape = IconMaskShapes.credential(),
            )
        },
        summary = { Text(stringResource(certProviderTextRes(certProviderValue))) },
        type = ListPreferenceType.DROPDOWN_MENU,
        valueToText = { AnnotatedString(stringResource(certProviderTextRes(it))) },
    )

    DisableProcessTextPreference()
    HideLauncherIconPreference()
}
