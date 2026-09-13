package fr.husi.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.husi.bg.AppUpdateChecker
import fr.husi.bg.AppUpdateInfo
import fr.husi.bg.AppUpdateInstaller
import fr.husi.bg.ShizukuAvailability
import fr.husi.bg.todayEpochDay
import fr.husi.compose.IconMaskColors
import fr.husi.compose.IconMaskShapes
import fr.husi.compose.MaskedIcon
import fr.husi.compose.PasswordPreference
import fr.husi.compose.Preference
import fr.husi.compose.SwitchPreference
import fr.husi.compose.collectAsStateWithLifecycle
import fr.husi.compose.material3.Text
import fr.husi.database.DataStore
import fr.husi.ktx.Logs
import fr.husi.ktx.readableMessage
import fr.husi.resources.Res
import fr.husi.resources.app_update_auto_check
import fr.husi.resources.app_update_auto_check_sum
import fr.husi.resources.app_update_check_now
import fr.husi.resources.app_update_checking
import fr.husi.resources.app_update_last_check
import fr.husi.resources.app_update_never_checked
import fr.husi.resources.app_update_only_when_connected
import fr.husi.resources.app_update_only_when_connected_sum
import fr.husi.resources.app_update_pre_release
import fr.husi.resources.app_update_pre_release_sum
import fr.husi.resources.app_update_shizuku_denied
import fr.husi.resources.app_update_shizuku_not_installed
import fr.husi.resources.app_update_shizuku_not_running
import fr.husi.resources.app_update_shizuku_unsupported
import fr.husi.resources.app_update_token
import fr.husi.resources.app_update_up_to_date
import fr.husi.resources.app_update_use_shizuku
import fr.husi.resources.app_update_use_shizuku_sum
import fr.husi.resources.cached
import fr.husi.resources.fiber_smart_record
import fr.husi.resources.password
import fr.husi.resources.public_icon
import fr.husi.resources.security
import fr.husi.resources.update
import fr.husi.ui.AppUpdateDialog
import fr.husi.ui.LocalSnackbarEmitter
import fr.husi.ui.StringOrRes
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun AppUpdateSettingsGroup() {
    val scope = rememberCoroutineScope()
    val snackbar = LocalSnackbarEmitter.current

    val autoCheck by DataStore.appUpdateAutoCheck.collectAsStateWithLifecycle()
    val preRelease by DataStore.appUpdatePreRelease.collectAsStateWithLifecycle()
    val onlyWhenConnected by DataStore.appUpdateOnlyWhenConnected.collectAsStateWithLifecycle()
    val token by DataStore.appUpdateToken.collectAsStateWithLifecycle()
    val useShizuku by DataStore.appUpdateUseShizuku.collectAsStateWithLifecycle()
    val lastCheckEpochDay by DataStore.appUpdateLastCheckEpochDay.collectAsStateWithLifecycle()

    val shizuku by AppUpdateInstaller.shizukuAvailability.collectAsStateWithLifecycle()
    LifecycleResumeEffect(Unit) {
        AppUpdateInstaller.refreshShizukuAvailability()
        onPauseOrDispose {}
    }

    var checking by remember { mutableStateOf(false) }
    var found by remember { mutableStateOf<AppUpdateInfo?>(null) }

    fun checkNow() {
        checking = true
        scope.launch {
            try {
                DataStore.appUpdateLastCheckEpochDay.set(todayEpochDay())
                val update = AppUpdateChecker().check()
                if (update == null) {
                    snackbar.show(StringOrRes.Res(Res.string.app_update_up_to_date))
                } else {
                    found = update
                }
            } catch (e: Exception) {
                Logs.e("check app update", e)
                snackbar.show(StringOrRes.Direct(e.readableMessage))
            } finally {
                checking = false
            }
        }
    }

    SwitchPreference(
        value = autoCheck,
        onValueChange = { DataStore.appUpdateAutoCheck.setBlocking(it) },
        title = { Text(stringResource(Res.string.app_update_auto_check)) },
        icon = { MaskedIcon(Res.drawable.update, color = IconMaskColors.IconLightGreen) },
        summary = { Text(stringResource(Res.string.app_update_auto_check_sum)) },
    )

    AnimatedVisibility(visible = autoCheck) {
        SwitchPreference(
            value = onlyWhenConnected,
            onValueChange = { DataStore.appUpdateOnlyWhenConnected.setBlocking(it) },
            title = { Text(stringResource(Res.string.app_update_only_when_connected)) },
            icon = { MaskedIcon(Res.drawable.public_icon, color = IconMaskColors.IconLightBlue) },
            summary = { Text(stringResource(Res.string.app_update_only_when_connected_sum)) },
        )
    }

    SwitchPreference(
        value = preRelease,
        onValueChange = { DataStore.appUpdatePreRelease.setBlocking(it) },
        title = { Text(stringResource(Res.string.app_update_pre_release)) },
        icon = {
            MaskedIcon(
                Res.drawable.fiber_smart_record,
                color = IconMaskColors.IconLightYellow,
                shape = IconMaskShapes.risk(),
            )
        },
        summary = { Text(stringResource(Res.string.app_update_pre_release_sum)) },
    )

    PasswordPreference(
        value = token,
        onValueChange = { DataStore.appUpdateToken.setBlocking(it) },
        title = { Text(stringResource(Res.string.app_update_token)) },
        icon = {
            MaskedIcon(
                Res.drawable.password,
                color = IconMaskColors.IconCoral,
                shape = IconMaskShapes.credential(),
            )
        },
    )

    SwitchPreference(
        value = useShizuku,
        onValueChange = { enable ->
            if (!enable) {
                DataStore.appUpdateUseShizuku.setBlocking(false)
                return@SwitchPreference
            }
            scope.launch {
                val granted =
                    AppUpdateInstaller.requestShizukuPermission() == ShizukuAvailability.Granted
                DataStore.appUpdateUseShizuku.setBlocking(granted)
            }
        },
        title = { Text(stringResource(Res.string.app_update_use_shizuku)) },
        enabled = shizuku == ShizukuAvailability.Granted ||
            shizuku == ShizukuAvailability.NotGranted,
        icon = {
            MaskedIcon(
                Res.drawable.security,
                color = IconMaskColors.IconLavender,
                shape = IconMaskShapes.risk(),
            )
        },
        summary = { Text(stringResource(shizukuSummary(shizuku))) },
    )

    Preference(
        title = { Text(stringResource(Res.string.app_update_check_now)) },
        enabled = !checking,
        icon = { MaskedIcon(Res.drawable.cached, color = IconMaskColors.IconCyan) },
        summary = {
            Text(
                if (checking) {
                    stringResource(Res.string.app_update_checking)
                } else {
                    lastCheckSummary(lastCheckEpochDay)
                },
            )
        },
        onClick = { checkNow() },
    )

    found?.let { info ->
        AppUpdateDialog(
            info = info,
            onDismissRequest = { found = null },
            onSkipVersion = {
                DataStore.appUpdateSkippedVersion.setBlocking(info.version)
                found = null
            },
        )
    }
}

@Composable
private fun lastCheckSummary(lastCheckEpochDay: Long): String {
    if (lastCheckEpochDay <= 0L) return stringResource(Res.string.app_update_never_checked)
    val date = remember(lastCheckEpochDay) { LocalDate.fromEpochDays(lastCheckEpochDay) }
    return stringResource(Res.string.app_update_last_check, date.toString())
}

private fun shizukuSummary(availability: ShizukuAvailability) = when (availability) {
    ShizukuAvailability.NotInstalled -> Res.string.app_update_shizuku_not_installed
    ShizukuAvailability.NotRunning -> Res.string.app_update_shizuku_not_running
    ShizukuAvailability.Unsupported -> Res.string.app_update_shizuku_unsupported
    ShizukuAvailability.NotGranted -> Res.string.app_update_shizuku_denied
    ShizukuAvailability.Granted -> Res.string.app_update_use_shizuku_sum
}
