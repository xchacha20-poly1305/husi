@file:OptIn(ExperimentalMaterial3Api::class)

package fr.husi.ui.configuration

import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.rememberViewModelStoreOwner

@Composable
fun ProfileSelectSheet(
    preSelected: Long?,
    onDismiss: () -> Unit,
    onSelected: (Long) -> Unit,
) {
    // The sheet lives outside the navigation display, so without an owner of its own its
    // view models would be kept by the host until the whole window goes away.
    CompositionLocalProvider(
        LocalViewModelStoreOwner provides rememberViewModelStoreOwner(),
    ) {
        val state = rememberProfilePickerState(preSelected = preSelected)

        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = rememberBottomSheetState(
                initialValue = SheetValue.Hidden,
                enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
            ),
        ) {
            ProfilePickerContent(
                state = state,
                onDismiss = onDismiss,
                onSelected = onSelected,
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.9f),
                bottomPadding = 0.dp,
            )
        }
    }
}
