package io.nekohasekai.sagernet.ui.tools

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cached
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.compose.SimpleIconButton
import io.nekohasekai.sagernet.compose.paddingExceptBottom
import io.nekohasekai.sagernet.compose.theme.AppTheme
import io.nekohasekai.sagernet.ktx.blankAsNull
import io.nekohasekai.sagernet.repository.TempRepository
import io.nekohasekai.sagernet.repository.repo
import io.nekohasekai.sagernet.ui.ComposeActivity

class VPNScannerActivity : ComposeActivity() {

    private val viewModel by viewModels<VPNScannerActivityViewModel>()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            AppTheme {
                VPNScannerScreen(
                    viewModel = viewModel,
                    onBackPress = { onBackPressedDispatcher.onBackPressed() },
                )
            }
        }

        if (savedInstanceState == null) {
            viewModel.scanVPN()
        }
    }

}

private const val TYPE_ITEM_CARD = 0
private const val TYPE_SPACER = 1

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun VPNScannerScreen(
    modifier: Modifier = Modifier,
    viewModel: VPNScannerActivityViewModel,
    onBackPress: () -> Unit,
) {
    val listState = rememberLazyListState()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isScanning = uiState.progress != null

    val windowInsets = WindowInsets.safeDrawing
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    LaunchedEffect(uiState.appInfos.size) {
        if (uiState.appInfos.isNotEmpty()) {
            listState.animateScrollToItem(uiState.appInfos.lastIndex)
        }
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            Box(
                modifier = Modifier.fillMaxWidth(),
            ) {
                TopAppBar(
                    title = { Text(stringResource(R.string.scan_vpn_app)) },
                    navigationIcon = {
                        SimpleIconButton(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                            onClick = onBackPress,
                        )
                    },
                    actions = {
                        SimpleIconButton(
                            imageVector = Icons.Filled.Cached,
                            contentDescription = stringResource(R.string.refresh),
                            enabled = !isScanning,
                            onClick = { viewModel.scanVPN() },
                        )
                    },
                    windowInsets = windowInsets.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                    scrollBehavior = scrollBehavior,
                )

                uiState.progress?.let {
                    LinearWavyProgressIndicator(
                        progress = { it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter),
                    )
                }
            }
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .paddingExceptBottom(innerPadding),
            state = listState,
        ) {
            items(
                items = uiState.appInfos,
                key = { it.packageInfo.packageName },
                contentType = { TYPE_ITEM_CARD },
            ) {
                val context = LocalContext.current

                ElevatedCard(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .clickable {
                            context.startActivity(
                                Intent()
                                    .setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                                    .setData(
                                        Uri.fromParts(
                                            "package", it.packageInfo.packageName, null
                                        )
                                    )
                            )
                        },
                ) {
                    Column {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        ) {
                            Image(
                                painter = rememberDrawablePainter(it.icon),
                                contentDescription = null,
                                modifier = Modifier
                                    .size(40.dp)
                                    .padding(top = 4.dp),
                            )

                            Spacer(modifier = Modifier.width(12.dp))

                            Column {
                                SelectionContainer {
                                    Text(
                                        text = it.label,
                                        style = MaterialTheme.typography.titleMedium,
                                    )
                                }
                                SelectionContainer {
                                    Text(
                                        text = it.packageInfo.packageName,
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                            }
                        }

                        KeyValueLine(
                            key = stringResource(R.string.vpn_app_type),
                            value = it.vpnType.appType
                                ?: stringResource(R.string.vpn_app_type_other),
                        )
                        KeyValueLine(
                            key = stringResource(R.string.vpn_core_type),
                            value = it.vpnType.coreType?.coreType
                                ?: stringResource(R.string.vpn_core_type_unknown),
                        )
                        it.vpnType.coreType?.corePath?.blankAsNull()?.let { corePath ->
                            KeyValueLine(
                                key = stringResource(R.string.vpn_core_path),
                                value = corePath,
                            )
                        }
                        it.vpnType.coreType?.goVersion?.blankAsNull()?.let { goVersion ->
                            KeyValueLine(
                                key = stringResource(R.string.vpn_golang_version),
                                value = goVersion,
                            )
                        }
                    }
                }
            }

            item("navigation_space", TYPE_SPACER) {
                Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
            }
        }
    }
}

@Composable
private fun KeyValueLine(key: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = key,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
        )
        SelectionContainer {
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.End,
            )
        }
    }
}

@Preview
@Composable
private fun PreviewVPNScannerScreen() {
    val context = LocalContext.current
    repo = TempRepository(context)

    VPNScannerScreen(
        viewModel = VPNScannerActivityViewModel(),
        onBackPress = {},
    )
}