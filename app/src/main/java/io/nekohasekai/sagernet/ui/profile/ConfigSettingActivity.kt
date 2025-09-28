package io.nekohasekai.sagernet.ui.profile

import android.content.Intent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiSymbols
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Outbond
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.fmt.config.ConfigBean
import io.nekohasekai.sagernet.ktx.contentOrUnset
import me.zhanghai.compose.preference.Preference
import me.zhanghai.compose.preference.SwitchPreference
import me.zhanghai.compose.preference.TextFieldPreference

@OptIn(ExperimentalMaterial3Api::class)
class ConfigSettingActivity : ProfileSettingsActivity<ConfigBean>() {

    override val viewModel by viewModels<ConfigSettingsViewModel>()

    override fun LazyListScope.settings(state: ProfileSettingsUiState) {
        state as ConfigUiState

        val config = when (state.type) {
            ConfigBean.TYPE_CONFIG -> state.customConfig
            ConfigBean.TYPE_OUTBOUND -> state.customOutbound
            else -> error("impossible")
        }

        item("name") {
            TextFieldPreference(
                value = state.name,
                onValueChange = { viewModel.setName(it) },
                title = { Text(stringResource(R.string.profile_name)) },
                textToValue = { it },
                icon = { Icon(Icons.Filled.EmojiSymbols, null) },
                summary = { Text(LocalContext.current.contentOrUnset(state.name)) },
                valueToText = { it },
            )
        }
        item("outbound_only") {
            SwitchPreference(
                value = state.type == ConfigBean.TYPE_OUTBOUND,
                onValueChange = {
                    viewModel.setType(
                        if (it) {
                            ConfigBean.TYPE_OUTBOUND
                        } else {
                            ConfigBean.TYPE_CONFIG
                        }
                    )
                },
                title = { Text(stringResource(R.string.is_outbound_only)) },
                icon = { Icon(Icons.Filled.Outbond, null) },
            )
        }
        item("config") {
            Preference(
                title = { Text(stringResource(R.string.custom_config)) },
                icon = { Icon(Icons.Filled.Layers, null) },
                summary = {

                    val text = if (config.isBlank()) {
                        stringResource(androidx.preference.R.string.not_set)
                    } else {
                        stringResource(R.string.lines, config.count { it == 'n' } + 1)
                    }
                    Text(text)
                },
                onClick = {
                    editConfig.launch(
                        Intent(this@ConfigSettingActivity, ConfigEditActivity::class.java)
                            .putExtra(ConfigEditActivity.EXTRA_CUSTOM_CONFIG, config)
                    )
                },
            )
        }
    }

    private val editConfig = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (it.resultCode == RESULT_OK) {
            viewModel.setConfig(it.data!!.getStringExtra(ConfigEditActivity.EXTRA_CUSTOM_CONFIG)!!)
        }
    }


}