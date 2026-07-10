package fr.husi.ui.profile

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import fr.husi.compose.IconMaskColors
import fr.husi.compose.MaskedIcon
import fr.husi.compose.material3.Text
import fr.husi.compose.preferenceGroup
import fr.husi.ktx.contentOrUnset
import fr.husi.resources.Res
import fr.husi.resources.emoji_symbols
import fr.husi.resources.profile_config
import fr.husi.resources.profile_name
import fr.husi.ui.NavRoutes
import me.zhanghai.compose.preference.TextFieldPreference
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DirectSettingsScreen(
    profileId: Long,
    isSubscription: Boolean,
    onResult: (updated: Boolean) -> Unit,
    onOpenConfigEditor: (NavRoutes.ConfigEditor) -> Unit,
) {
    val viewModel: DirectSettingsViewModel =
        profileEditorViewModel(profileId = profileId, isSubscription = isSubscription) {
            DirectSettingsViewModel()
        }

    ProfileSettingsScreenScaffold(
        title = Res.string.profile_config,
        viewModel = viewModel,
        onResult = onResult,
        onOpenConfigEditor = onOpenConfigEditor,
    ) { uiState, _ ->
        directSettings(uiState as DirectUiState, viewModel)
    }
}

private fun LazyListScope.directSettings(
    uiState: DirectUiState,
    viewModel: DirectSettingsViewModel,
) {
    preferenceGroup {
        TextFieldPreference(
            value = uiState.name,
            onValueChange = { viewModel.setName(it) },
            title = { Text(stringResource(Res.string.profile_name)) },
            textToValue = { it },
            icon = {
                MaskedIcon(
                    Res.drawable.emoji_symbols,
                    color = IconMaskColors.IconCyan,
                )
            },
            summary = { Text(contentOrUnset(uiState.name)) },
            valueToText = { it },
        )
    }
}
