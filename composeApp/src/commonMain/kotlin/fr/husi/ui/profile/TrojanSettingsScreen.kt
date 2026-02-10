package fr.husi.ui.profile

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.husi.compose.PasswordPreference
import fr.husi.resources.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrojanSettingsScreen(
    profileId: Long,
    isSubscription: Boolean,
    onResult: (updated: Boolean) -> Unit,
) {
    val viewModel: TrojanSettingsViewModel = viewModel { TrojanSettingsViewModel() }

    LaunchedEffect(profileId, isSubscription) {
        viewModel.initialize(profileId, isSubscription)
    }

    ProfileSettingsScreenScaffold(
        title = Res.string.profile_config,
        viewModel = viewModel,
        onResult = onResult,
    ) { scope, uiState, scrollTo ->
        scope.trojanSettings(uiState as TrojanUiState, viewModel, scrollTo)
    }
}


private fun LazyListScope.trojanSettings(
    uiState: TrojanUiState,
    viewModel: TrojanSettingsViewModel,
    scrollTo: (String) -> Unit,
) {
    headSettings(uiState, viewModel)
    item("password") {
        PasswordPreference(
            value = uiState.password,
            onValueChange = { viewModel.setPassword(it) },
        )
    }
    transportSettings(uiState, viewModel)
    muxSettings(uiState, viewModel)
    tlsSettings(uiState, viewModel, scrollTo)
}

