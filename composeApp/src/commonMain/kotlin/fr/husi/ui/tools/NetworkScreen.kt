package fr.husi.ui.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import fr.husi.compose.BoxedVerticalScrollbar
import fr.husi.compose.CapsuleTopBar
import fr.husi.compose.SimpleIconButton
import fr.husi.compose.material3.Button
import fr.husi.compose.material3.Text
import fr.husi.compose.withNavigation
import fr.husi.resources.Res
import fr.husi.resources.arrow_back
import fr.husi.resources.back
import fr.husi.resources.get_cert
import fr.husi.resources.get_cert_summary
import fr.husi.resources.rule_set_match
import fr.husi.resources.speed_test
import fr.husi.resources.start
import fr.husi.resources.stun_test
import fr.husi.resources.stun_test_summary
import fr.husi.resources.tools_network
import fr.husi.ui.NavRoutes
import io.github.oikvpqya.compose.fastscroller.material3.defaultMaterialScrollbarStyle
import io.github.oikvpqya.compose.fastscroller.rememberScrollbarAdapter
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource

@Composable
internal fun NetworkScreen(
    modifier: Modifier = Modifier,
    onBackPress: () -> Unit,
    onOpenTool: (NavRoutes.ToolsPage) -> Unit,
) {
    val scrollState = rememberScrollState()
    val windowInsets = WindowInsets.safeDrawing
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            CapsuleTopBar(
                navigationIcon = {
                    SimpleIconButton(
                        imageVector = vectorResource(Res.drawable.arrow_back),
                        contentDescription = stringResource(Res.string.back),
                        onClick = onBackPress,
                    )
                },
                title = { Text(stringResource(Res.string.tools_network)) },
                windowInsets = windowInsets.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        val contentPadding = innerPadding.withNavigation()
        val layoutDirection = LocalLayoutDirection.current
        Row(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .verticalScroll(scrollState)
                    .padding(
                        start = contentPadding.calculateStartPadding(layoutDirection),
                        end = contentPadding.calculateEndPadding(layoutDirection),
                    )
                    .padding(horizontal = 16.dp),
            ) {
                Spacer(modifier = Modifier.height(contentPadding.calculateTopPadding()))
                Spacer(modifier = Modifier.height(16.dp))
                ActivityCard(
                    title = stringResource(Res.string.stun_test),
                    description = stringResource(Res.string.stun_test_summary),
                    launch = {
                        onOpenTool(NavRoutes.ToolsPage.Stun)
                    },
                )
                ActivityCard(
                    title = stringResource(Res.string.get_cert),
                    description = stringResource(Res.string.get_cert_summary),
                    launch = {
                        onOpenTool(NavRoutes.ToolsPage.GetCert)
                    },
                )
                PlatformNetworkTools(onOpenTool)
                ActivityCard(
                    title = stringResource(Res.string.speed_test),
                    description = "",
                    launch = {
                        onOpenTool(NavRoutes.ToolsPage.SpeedTest)
                    },
                )
                ActivityCard(
                    title = stringResource(Res.string.rule_set_match),
                    description = "",
                    launch = {
                        onOpenTool(NavRoutes.ToolsPage.RuleSetMatch)
                    },
                )
                Spacer(modifier = Modifier.height(16.dp))
                Spacer(modifier = Modifier.height(contentPadding.calculateBottomPadding()))
            }

            BoxedVerticalScrollbar(
                modifier = Modifier
                    .padding(contentPadding)
                    .fillMaxHeight(),
                adapter = rememberScrollbarAdapter(scrollState = scrollState),
                style = defaultMaterialScrollbarStyle().copy(
                    thickness = 12.dp,
                ),
            )
        }
    }
}

@Composable
internal fun ActivityCard(
    modifier: Modifier = Modifier,
    title: String,
    description: String,
    launch: () -> Unit,
) {
    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
            )
            if (description.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                Button(
                    onClick = launch,
                ) {
                    Text(stringResource(Res.string.start))
                }
            }
        }
    }
}
