package io.nekohasekai.sagernet.ui.tools

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.compose.extraBottomPadding
import io.nekohasekai.sagernet.compose.rememberScrollHideState

@Composable
internal fun NetworkScreen(
    modifier: Modifier = Modifier,
    onVisibleChange: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val visible by rememberScrollHideState(scrollState)

    LaunchedEffect(visible) {
        onVisibleChange(visible)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(extraBottomPadding())
            .padding(horizontal = 16.dp),
    ) {
        Spacer(modifier = Modifier.height(16.dp))
        ActivityCard(
            title = stringResource(R.string.stun_test),
            description = stringResource(R.string.stun_test_summary),
            launch = {
                context.startActivity(Intent(context, StunActivity::class.java))
            },
        )
        ActivityCard(
            title = stringResource(R.string.get_cert),
            description = stringResource(R.string.get_cert_summary),
            launch = {
                context.startActivity(Intent(context, GetCertActivity::class.java))
            },
        )
        ActivityCard(
            title = stringResource(R.string.scan_vpn_app),
            description = stringResource(R.string.scan_vpn_app_introduce),
            launch = {
                context.startActivity(Intent(context, VPNScannerActivity::class.java))
            },
        )
        ActivityCard(
            title = stringResource(R.string.speed_test),
            description = "",
            launch = {
                context.startActivity(Intent(context, SpeedtestActivity::class.java))
            },
        )
        ActivityCard(
            title = stringResource(R.string.rule_set_match),
            description = "",
            launch = {
                context.startActivity(Intent(context, RuleSetMatchActivity::class.java))
            },
        )
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun ActivityCard(
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
                    Text(stringResource(R.string.start))
                }
            }
        }
    }
}