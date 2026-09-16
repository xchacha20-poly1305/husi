package fr.husi.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import fr.husi.bg.ApkInstallResult
import fr.husi.bg.AppUpdateInfo
import fr.husi.bg.AppUpdateInstaller
import fr.husi.bg.downloadAppUpdate
import fr.husi.compose.ScrollableDialog
import fr.husi.compose.material3.Icon
import fr.husi.compose.material3.Text
import fr.husi.ktx.Logs
import fr.husi.ktx.readableMessage
import fr.husi.permission.AppPermission
import fr.husi.permission.LocalPermissionPlatform
import fr.husi.repository.resolveRepository
import fr.husi.resources.Res
import fr.husi.resources.app_update_available
import fr.husi.resources.app_update_download
import fr.husi.resources.app_update_downloading
import fr.husi.resources.app_update_install_failed
import fr.husi.resources.app_update_installing
import fr.husi.resources.app_update_no_matching_asset
import fr.husi.resources.app_update_open_release
import fr.husi.resources.app_update_skip_version
import fr.husi.resources.permission_denied
import fr.husi.resources.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource
import kotlin.math.roundToInt

private sealed interface AppUpdateStep {
    data object Idle : AppUpdateStep

    data class Downloading(val progress: Float) : AppUpdateStep

    data object Installing : AppUpdateStep
}

@Composable
fun AppUpdateDialog(
    info: AppUpdateInfo,
    onDismissRequest: () -> Unit,
    onSkipVersion: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val snackbar = LocalSnackbarEmitter.current
    val uriHandler = LocalUriHandler.current
    val permission = LocalPermissionPlatform.current

    var step by remember { mutableStateOf<AppUpdateStep>(AppUpdateStep.Idle) }
    val busy = step != AppUpdateStep.Idle

    val canDownload = info.downloadUrl != null && AppUpdateInstaller.isSupported

    fun startUpdate() {
        step = AppUpdateStep.Downloading(0f)
        scope.launch {
            try {
                val apk = downloadAppUpdate(
                    info = info,
                    cacheDir = resolveRepository().cacheDir,
                    updateProgress = { step = AppUpdateStep.Downloading(it) },
                )
                step = AppUpdateStep.Installing
                when (val result = AppUpdateInstaller.install(apk)) {
                    ApkInstallResult.Success -> onDismissRequest()
                    is ApkInstallResult.Failed -> snackbar.show(
                        StringOrRes.ResWithParams(
                            Res.string.app_update_install_failed,
                            result.message,
                        ),
                    )
                }
            } catch (e: Exception) {
                Logs.e("install app update", e)
                snackbar.show(StringOrRes.Direct(e.readableMessage))
            } finally {
                step = AppUpdateStep.Idle
            }
        }
    }

    fun requestInstallPermissionThenUpdate() {
        scope.launch {
            if (AppUpdateInstaller.installsWithShizuku() ||
                permission.hasPermission(AppPermission.InstallPackages)
            ) {
                startUpdate()
                return@launch
            }
            permission.requestPermission(AppPermission.InstallPackages) { granted ->
                if (granted) {
                    startUpdate()
                } else {
                    snackbar.show(StringOrRes.Res(Res.string.permission_denied))
                }
            }
        }
    }

    ScrollableDialog(
        onDismissRequest = { if (!busy) onDismissRequest() },
        confirmButton = {
            if (canDownload) {
                TextButton(onClick = ::requestInstallPermissionThenUpdate, enabled = !busy) {
                    Text(stringResource(Res.string.app_update_download))
                }
            } else {
                TextButton(
                    onClick = {
                        uriHandler.openUri(info.releaseUrl)
                        onDismissRequest()
                    },
                ) {
                    Text(stringResource(Res.string.app_update_open_release))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onSkipVersion, enabled = !busy) {
                Text(stringResource(Res.string.app_update_skip_version))
            }
        },
        icon = { Icon(vectorResource(Res.drawable.update), null) },
        title = { Text(stringResource(Res.string.app_update_available, info.version)) },
        textPadding = PaddingValues(horizontal = 24.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            if (!canDownload) {
                Text(stringResource(Res.string.app_update_no_matching_asset))
            }
            Text(info.releaseNotes)
            when (val current = step) {
                AppUpdateStep.Idle -> Unit

                is AppUpdateStep.Downloading -> {
                    Text(
                        stringResource(
                            Res.string.app_update_downloading,
                            current.progress.roundToInt(),
                        ),
                        modifier = Modifier.padding(top = 16.dp),
                    )
                    LinearWavyProgressIndicator(
                        progress = { current.progress / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                    )
                }

                AppUpdateStep.Installing -> {
                    Text(
                        stringResource(Res.string.app_update_installing),
                        modifier = Modifier.padding(top = 16.dp),
                    )
                    LinearWavyProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                    )
                }
            }
        }
    }
}
