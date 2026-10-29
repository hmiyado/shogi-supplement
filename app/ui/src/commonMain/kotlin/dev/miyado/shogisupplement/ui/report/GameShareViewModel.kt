package dev.miyado.shogisupplement.ui.report

import dev.miyado.shogisupplement.gameshare.GameShareDraft
import dev.miyado.shogisupplement.gameshare.GameShareResult
import dev.miyado.shogisupplement.gameshare.GameShareService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class GameSharePhase { None, Creating, Active, CreateFailed, SourceMissing }

data class GameShareUiState(
    val phase: GameSharePhase = GameSharePhase.None,
    val id: String? = null,
    val url: String? = null,
) {
    val isBusy: Boolean get() = phase == GameSharePhase.Creating
}

class GameShareViewModel(
    private val scope: CoroutineScope,
    private val service: GameShareService,
) {
    private val _state = MutableStateFlow(GameShareUiState())
    val state: StateFlow<GameShareUiState> = _state.asStateFlow()

    fun reset() {
        if (!_state.value.isBusy) _state.value = GameShareUiState()
    }

    fun create(draft: GameShareDraft) {
        if (_state.value.isBusy) return
        _state.value = GameShareUiState(phase = GameSharePhase.Creating)
        scope.launch {
            try {
                _state.value = when (val result = service.create(draft)) {
                    is GameShareResult.Success -> {
                        val url = GameShareLink.urlFor(result.value.id, service.sharePageUrl ?: GameShareLink.WEB_STUDY_URL)
                        if (url == null) GameShareUiState(GameSharePhase.CreateFailed)
                        else GameShareUiState(GameSharePhase.Active, result.value.id, url)
                    }
                    GameShareResult.SourceMissing -> GameShareUiState(GameSharePhase.SourceMissing)
                    else -> GameShareUiState(GameSharePhase.CreateFailed)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _state.value = GameShareUiState(GameSharePhase.CreateFailed)
            }
        }
    }
}
