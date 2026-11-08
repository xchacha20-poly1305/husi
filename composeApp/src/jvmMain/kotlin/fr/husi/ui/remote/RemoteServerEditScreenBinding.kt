package fr.husi.ui.remote

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun RemoteServerEditScreen(
    serverId: Long,
    onBackPress: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RemoteServerEditViewModel = viewModel {
        RemoteServerEditViewModel(serverId)
    },
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    RemoteServerEditContent(
        uiState = uiState,
        onBackPress = onBackPress,
        onSave = { viewModel.save(onSaved = onBackPress) },
        onSetName = viewModel::setName,
        onSetUrl = viewModel::setUrl,
        onSetSecret = viewModel::setSecret,
        onTestConnection = viewModel::testConnection,
        modifier = modifier,
    )
}
