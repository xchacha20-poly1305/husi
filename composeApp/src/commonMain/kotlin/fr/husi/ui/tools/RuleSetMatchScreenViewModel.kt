package fr.husi.ui.tools

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.husi.bg.routeCustomGeoDir
import fr.husi.bg.routeGeoDir
import fr.husi.core.CoreClient
import fr.husi.ktx.Logs
import fr.husi.ktx.readableMessage
import fr.husi.repository.resolveRepository
import fr.husi.resources.*
import fr.husi.ui.StringOrRes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.core.context.GlobalContext

@Immutable
internal data class RuleSetMatchUiState(
    val keyword: String = "google.com",
    val matched: List<String> = emptyList(),
    val isDoing: Boolean = false,
)

@Immutable
internal sealed interface RuleSetMatchUiEvent {
    class Alert(val message: StringOrRes) : RuleSetMatchUiEvent
}

@Stable
internal class RuleSetMatchScreenViewModel(
    private val coreClient: CoreClient = GlobalContext.get().get(),
) : ViewModel() {
    val uiState: StateFlow<RuleSetMatchUiState>
        field = MutableStateFlow(RuleSetMatchUiState())

    val uiEvent: SharedFlow<RuleSetMatchUiEvent>
        field = MutableSharedFlow<RuleSetMatchUiEvent>()

    fun scan() {
        viewModelScope.launch(Dispatchers.IO) {
            val state = uiState.value
            scan0(state.keyword)
        }
    }

    private suspend fun scan0(keyword: String) {
        uiState.update { it.copy(matched = emptyList(), isDoing = true) }
        try {
            val externalAssetsDir = resolveRepository().externalAssetsDir
            val ruleSetDirs = listOf(
                routeGeoDir(externalAssetsDir),
                routeCustomGeoDir(externalAssetsDir),
            )
            for (dir in ruleSetDirs) {
                val names = coreClient.matchRuleSets(dir, keyword)
                uiState.update { state ->
                    state.copy(matched = state.matched + names)
                }
            }
            if (uiState.value.matched.isEmpty()) {
                uiEvent.emit(RuleSetMatchUiEvent.Alert(StringOrRes.Res(Res.string.not_found)))
            }
        } catch (e: Exception) {
            Logs.e(e)
            uiEvent.emit(RuleSetMatchUiEvent.Alert(StringOrRes.Direct(e.readableMessage)))
        } finally {
            uiState.update { it.copy(isDoing = false) }
        }
    }

    fun setKeyword(keyword: String) {
        uiState.update { it.copy(keyword = keyword) }
    }
}
