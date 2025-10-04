package io.nekohasekai.sagernet.ui.profile

import android.content.Intent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EmojiSymbols
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ernestoyaquello.dragdropswipelazycolumn.DragDropSwipeLazyColumn
import com.ernestoyaquello.dragdropswipelazycolumn.DraggableSwipeableItem
import com.ernestoyaquello.dragdropswipelazycolumn.config.DraggableSwipeableItemColors
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.database.ProxyEntity
import io.nekohasekai.sagernet.database.displayType
import io.nekohasekai.sagernet.fmt.internal.ChainBean
import io.nekohasekai.sagernet.ktx.contentOrUnset
import io.nekohasekai.sagernet.ui.configuration.ProfileSelectActivity
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.delay
import me.zhanghai.compose.preference.TextFieldPreference

@OptIn(ExperimentalMaterial3Api::class)
class ChainSettingsActivity : ProfileSettingsActivity<ChainBean>() {

    override val viewModel by viewModels<ChainSettingsViewModel>()

    override val title = R.string.chain_settings

    override fun LazyListScope.settings(state: ProfileSettingsUiState) {
        state as ChainUiState

        item("name", 0) {
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

        item("divider", 1) {
            HorizontalDivider()
        }

        item("add_profile", 2) {
            ElevatedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(4.dp)
                    .clickable {
                        viewModel.replacing = -1
                        selectProfileForAdd.launch(
                            Intent(
                                this@ChainSettingsActivity,
                                ProfileSelectActivity::class.java,
                            )
                        )
                    },
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                ),
            ) {
                Text(
                    text = stringResource(id = R.string.add_profile),
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontWeight = FontWeight.Bold
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
                items = state.profiles.toImmutableList(),
                key = { it.id },
                contentType = { 0 },
                userScrollEnabled = false,
                onIndicesChangedViaDragAndDrop = { viewModel.submitList(it.map { it.value }) },
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
                                    contentAlignment = Alignment.CenterEnd
                                ) {
                                    Icon(Icons.Default.Delete, null)
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
                            ProfileCard(i, profile)
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
    private fun ProfileCard(index: Int, profile: ProxyEntity) {
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
                        IconButton(onClick = {
                            viewModel.replacing = index
                            selectProfileForAdd.launch(
                                Intent(
                                    this@ChainSettingsActivity,
                                    ProfileSelectActivity::class.java,
                                ).putExtra(ProfileSelectActivity.EXTRA_SELECTED, profile),
                            )
                        }) {
                            Icon(
                                imageVector = Icons.Filled.Edit,
                                contentDescription = stringResource(R.string.edit),
                                tint = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        IconButton(onClick = { viewModel.remove(index) }) {
                            Icon(
                                imageVector = Icons.Filled.Delete,
                                contentDescription = stringResource(R.string.delete),
                                tint = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = profile.displayType(LocalContext.current),
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

    val selectProfileForAdd =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (it.resultCode == RESULT_OK) {
                val id = it.data!!.getLongExtra(ProfileSelectActivity.EXTRA_PROFILE_ID, 0)
                viewModel.onSelectProfile(id)
            }
        }

}
