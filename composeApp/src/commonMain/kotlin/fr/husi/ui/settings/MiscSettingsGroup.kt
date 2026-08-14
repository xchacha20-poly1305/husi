package fr.husi.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.AnnotatedString
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.husi.CONNECTION_TEST_URL
import fr.husi.CertProvider
import fr.husi.Key
import fr.husi.compose.IconMaskColors
import fr.husi.compose.IconMaskShapes
import fr.husi.compose.LinkOrContentTextField
import fr.husi.compose.MaskedIcon
import fr.husi.compose.PreferenceDivider
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
import me.zhanghai.compose.preference.ListPreference
import me.zhanghai.compose.preference.ListPreferenceType
import me.zhanghai.compose.preference.SliderPreference
import me.zhanghai.compose.preference.SwitchPreference
import me.zhanghai.compose.preference.TextFieldPreference
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun MiscSettingsGroup(
    needReload: () -> Unit,
    needRestart: () -> Unit,
) {
    val connectionTestUrlValue by DataStore.configurationStore
        .stringFlow(Key.CONNECTION_TEST_URL, CONNECTION_TEST_URL)
        .collectAsStateWithLifecycle(CONNECTION_TEST_URL)
    TextFieldPreference(
        value = connectionTestUrlValue,
        onValueChange = { DataStore.connectionTestURL = it },
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
    PreferenceDivider()

    val connectionTestConcurrentValue by DataStore.configurationStore
        .intFlow(Key.CONNECTION_TEST_CONCURRENT, 5)
        .collectAsStateWithLifecycle(5)
    var concurrentPreview by remember { mutableFloatStateOf(connectionTestConcurrentValue.toFloat()) }
    SliderPreference(
        value = connectionTestConcurrentValue.toFloat(),
        onValueChange = { DataStore.connectionTestConcurrent = it.toInt() },
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
    PreferenceDivider()

    val connectionTestTimeoutValue by DataStore.configurationStore
        .intFlow(Key.CONNECTION_TEST_TIMEOUT, 3000)
        .collectAsStateWithLifecycle(3000)
    var timeoutPreview by remember { mutableFloatStateOf(connectionTestTimeoutValue.toFloat()) }
    SliderPreference(
        value = connectionTestTimeoutValue.toFloat(),
        onValueChange = { DataStore.connectionTestTimeout = it.toInt() },
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
    PreferenceDivider()

    val connectionTestUnifiedDelay by DataStore.configurationStore
        .booleanFlow(Key.CONNECTION_TEST_UNIFIED_DELAY, false)
        .collectAsStateWithLifecycle(false)
    SwitchPreference(
        value = connectionTestUnifiedDelay,
        onValueChange = {
            DataStore.connectionTestUnifiedDelay = it
            needReload()
        },
        title = { Text(stringResource(Res.string.connection_test_unified_delay)) },
        icon = {
            MaskedIcon(Res.drawable.timer, IconMaskColors.IconLightGreen)
        },
    )
    PreferenceDivider()

    val connectionTestIgnoreHandshakeTime by DataStore.configurationStore
        .booleanFlow(Key.CONNECTION_TEST_IGNORE_HANDSHAKE_TIME, false)
        .collectAsStateWithLifecycle(false)
    SwitchPreference(
        value = connectionTestIgnoreHandshakeTime,
        onValueChange = {
            DataStore.connectionTestIgnoreHandshakeTime = it
            needReload()
        },
        title = { Text(stringResource(Res.string.connection_test_ignore_handshake_time)) },
        icon = {
            MaskedIcon(Res.drawable.question_mark, IconMaskColors.IconLightGreen)
        },
    )
    PlatformMiscOptions(needReload)
    PreferenceDivider()

    val certProviderValue by DataStore.configurationStore
        .intFlow(Key.CERT_PROVIDER, CertProvider.MOZILLA)
        .collectAsStateWithLifecycle(CertProvider.MOZILLA)

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
            DataStore.certProvider = it
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
    PreferenceDivider()

    DisableProcessTextPreference()
    HideLauncherIconPreference()
}
