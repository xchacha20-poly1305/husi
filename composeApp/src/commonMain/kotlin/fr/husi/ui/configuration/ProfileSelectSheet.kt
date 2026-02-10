package fr.husi.ui.configuration

import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.husi.ui.MainViewModel
import kotlin.random.Random
import kotlin.reflect.KClass


@Composable
fun ProfileSelectSheet(
    mainViewModel: MainViewModel,
    preSelected: Long?,
    onDismiss: () -> Unit,
    onSelected: (Long) -> Unit,
) {
    val sessionKey = remember { Random.nextInt().toString() }
    val vm: ConfigurationScreenViewModel = viewModel(
        key = "profile-select-$sessionKey",
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: KClass<T>, extras: CreationExtras): T {
                return ConfigurationScreenViewModel(onSelected) as T
            }
        },
    )
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(
            skipPartiallyExpanded = true,
        ),
    ) {
        ConfigurationScreen(
            mainViewModel = mainViewModel,
            onNavigationClick = onDismiss,
            selectCallback = onSelected,
            vm = vm,
            preSelected = preSelected,
        )
    }
}
