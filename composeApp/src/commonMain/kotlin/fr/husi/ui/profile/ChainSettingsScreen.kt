package fr.husi.ui.profile

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ernestoyaquello.dragdropswipelazycolumn.DragDropSwipeLazyColumn
import com.ernestoyaquello.dragdropswipelazycolumn.DraggableSwipeableItem
import com.ernestoyaquello.dragdropswipelazycolumn.config.DraggableSwipeableItemColors
import fr.husi.compose.TooltipIconButton
import fr.husi.database.ProxyEntity
import fr.husi.database.displayType
import fr.husi.ktx.contentOrUnset
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.delay
import me.zhanghai.compose.preference.TextFieldPreference
import fr.husi.resources.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChainSettingsScreen(
    profileId: Long,
    isSubscription: Boolean,
    onOpenProfileSelect: (preSelected: Long?, onSelected: (Long) -> Unit) -> Unit,
    onResult: (updated: Boolean) -> Unit,
) {
    val viewModel: ChainSettingsViewModel = viewModel { ChainSettingsViewModel() }

    LaunchedEffect(profileId, isSubscription) {
        viewModel.initialize(profileId, isSubscription)
    }

    ProfileSettingsScreenScaffold(
        title = Res.string.chain_settings,
        viewModel = viewModel,
        onResult = onResult,
    ) { scope, uiState, _ ->
        scope.chainSettings(
            uiState = uiState as ChainUiState,
            viewModel = viewModel,
            onAdd = {
                viewModel.replacing = -1
                onOpenProfileSelect(null) { id ->
                    viewModel.onSelectProfile(id)
                }
            },
            onReplace = { index, profileIdForPreselect ->
                viewModel.replacing = index
                onOpenProfileSelect(profileIdForPreselect.takeIf { it > 0 }) { id ->
                    viewModel.onSelectProfile(id)
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
private fun LazyListScope.chainSettings(
    uiState: ChainUiState,
    viewModel: ChainSettingsViewModel,
    onAdd: () -> Unit,
    onReplace: (index: Int, profileId: Long) -> Unit,
) {
    item("name", 0) {
        TextFieldPreference(
            value = uiState.name,
            onValueChange = { viewModel.setName(it) },
            title = { Text(stringResource(Res.string.profile_name)) },
            textToValue = { it },
            icon = { Icon(vectorResource(Res.drawable.emoji_symbols), null) },
            summary = { Text(contentOrUnset(uiState.name)) },
            valueToText = { it },
        )
    }

    item("divider", 1) {
        HorizontalDivider()
    }

    item("add_profile", 2) {
        ElevatedCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp)
                .clickable(onClick = onAdd),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
            ),
        ) {
            Text(
                text = stringResource(Res.string.add_profile),
                modifier = Modifier.padding(16.dp),
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontWeight = FontWeight.Bold,
                ),
            )
        }
    }

    item("list", 3) {
        val density = LocalDensity.current
        val windowInfo = LocalWindowInfo.current
        val maxHeight =
            with(density) { windowInfo.containerSize.height.toDp() }.takeIf { it > 0.dp }
                ?: 480.dp
        DragDropSwipeLazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = maxHeight),
            items = uiState.profiles.toImmutableList(),
            key = { it.id },
            contentType = { 0 },
            userScrollEnabled = false,
            onIndicesChangedViaDragAndDrop = { viewModel.submitReorder(it) },
        ) { i, profile ->
            val swipeState = rememberSwipeToDismissBoxState()
            var visible by remember { mutableStateOf(true) }
            DraggableSwipeableItem(
                modifier = Modifier.animateDraggableSwipeableItem(),
                colors = DraggableSwipeableItemColors.createRemembered(
                    containerBackgroundColor = Color.Transparent,
                    containerBackgroundColorWhileDragged = Color.Transparent,
                ),
            ) {
                AnimatedVisibility(
                    visible = visible,
                    exit = shrinkVertically(animationSpec = tween(200)) + fadeOut(),
                ) {
                    SwipeToDismissBox(
                        state = swipeState,
                        enableDismissFromStartToEnd = true,
                        enableDismissFromEndToStart = true,
                        backgroundContent = {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 16.dp),
                                contentAlignment = Alignment.CenterEnd,
                            ) {
                                Icon(vectorResource(Res.drawable.delete), null)
                            }
                        },
                        onDismiss = { value ->
                            when (value) {
                                SwipeToDismissBoxValue.StartToEnd,
                                SwipeToDismissBoxValue.EndToStart,
                                    -> visible = false

                                else -> {}
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .dragDropModifier(),
                    ) {
                        ChainProfileCard(
                            profile = profile,
                            onReplace = { onReplace(i, profile.id) },
                            onRemove = { viewModel.remove(i) },
                        )
                    }
                }
            }
            LaunchedEffect(visible) {
                if (!visible) {
                    delay(220)
                    viewModel.remove(i)
                }
            }
        }
    }
}

@Composable
private fun ChainProfileCard(
    profile: ProxyEntity,
    onReplace: () -> Unit,
    onRemove: () -> Unit,
) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 4.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = profile.displayName(),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    TooltipIconButton(
                        onClick = onReplace,
                        icon = vectorResource(Res.drawable.edit),
                        contentDescription = stringResource(Res.string.edit),
                        colors = IconButtonDefaults.iconButtonColors(
                            contentColor = MaterialTheme.colorScheme.onSurface,
                        ),
                    )
                    TooltipIconButton(
                        onClick = onRemove,
                        icon = vectorResource(Res.drawable.delete),
                        contentDescription = stringResource(Res.string.delete),
                        colors = IconButtonDefaults.iconButtonColors(
                            contentColor = MaterialTheme.colorScheme.onSurface,
                        ),
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = profile.displayType(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline,
                )

            }
        }
    }
}
