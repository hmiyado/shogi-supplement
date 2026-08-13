package dev.miyado.shogisupplement.ui.restore

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.miyado.shogisupplement.download.GameDownloadOutcome
import dev.miyado.shogisupplement.download.GameDownloadService
import dev.miyado.shogisupplement.download.GameImportOutcome
import dev.miyado.shogisupplement.download.ReconstructedGame
import dev.miyado.shogisupplement.text.AppStrings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** 復元成功画面（棋譜ダウンロード）の状態。 */
sealed class GameRestoreUiState {
    /** サーバー上の件数を確認中。 */
    data object Loading : GameRestoreUiState()

    /** 件数確認済み・ダウンロード開始待ち。 */
    data class Ready(val count: Int) : GameRestoreUiState()

    data class Downloading(val done: Int, val total: Int) : GameRestoreUiState()

    data class Completed(val total: Int, val succeeded: Int, val failed: Int) : GameRestoreUiState()

    data class Error(val message: String) : GameRestoreUiState()
}

/**
 * 復元成功画面（引き継ぎコード復元→棋譜ダウンロード）のViewModel。
 *
 * エンジン解析（1局ぶんの取込コールバック）はプラットフォームに依存させたくないため
 * コンストラクタ引数として受け取る（[GameDownloadService] のKDoc参照）。
 */
class GameRestoreViewModel(
    private val gameDownloadService: GameDownloadService,
    private val importGame: suspend (ReconstructedGame) -> GameImportOutcome,
) : ViewModel() {

    private val _uiState = MutableStateFlow<GameRestoreUiState>(GameRestoreUiState.Loading)
    val uiState: StateFlow<GameRestoreUiState> = _uiState

    init {
        loadCount()
    }

    fun retry() = loadCount()

    private fun loadCount() {
        viewModelScope.launch {
            _uiState.value = GameRestoreUiState.Loading
            gameDownloadService.countRemoteGames().fold(
                onSuccess = { count -> _uiState.value = GameRestoreUiState.Ready(count) },
                onFailure = { _uiState.value = GameRestoreUiState.Error(AppStrings.GAME_RESTORE_ERROR_NETWORK) },
            )
        }
    }

    fun startDownload() {
        val ready = _uiState.value as? GameRestoreUiState.Ready ?: return
        viewModelScope.launch {
            _uiState.value = GameRestoreUiState.Downloading(done = 0, total = ready.count)
            val outcome = gameDownloadService.downloadAndImport(
                onProgress = { done, total -> _uiState.value = GameRestoreUiState.Downloading(done, total) },
                importGame = importGame,
            )
            _uiState.value = when (outcome) {
                is GameDownloadOutcome.Completed ->
                    GameRestoreUiState.Completed(outcome.total, outcome.succeeded, outcome.failed)
                GameDownloadOutcome.NotAuthenticated ->
                    GameRestoreUiState.Error(AppStrings.GAME_RESTORE_ERROR_NOT_AUTHENTICATED)
                GameDownloadOutcome.NoSecret ->
                    GameRestoreUiState.Error(AppStrings.GAME_RESTORE_ERROR_NO_SECRET)
                is GameDownloadOutcome.NetworkError ->
                    GameRestoreUiState.Error(AppStrings.GAME_RESTORE_ERROR_NETWORK)
            }
        }
    }

    companion object {
        fun factory(
            gameDownloadService: GameDownloadService,
            importGame: suspend (ReconstructedGame) -> GameImportOutcome,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer { GameRestoreViewModel(gameDownloadService, importGame) }
        }
    }
}
