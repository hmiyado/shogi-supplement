package dev.miyado.shogisupplement.webApp.mypage

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.miyado.shogisupplement.auth.AuthRepository
import dev.miyado.shogisupplement.crypto.TransferSecretStore
import dev.miyado.shogisupplement.db.GameRecord
import dev.miyado.shogisupplement.download.GameDetailOutcome
import dev.miyado.shogisupplement.download.GameSummaryOutcome
import dev.miyado.shogisupplement.download.GameSummaryService
import dev.miyado.shogisupplement.text.AppStrings
import dev.miyado.shogisupplement.ui.transfercode.TransferCodeInputViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** マイページ（引き継ぎコードでログイン→棋譜一覧→棋譜詳細）の状態遷移を担う。 */
class MyPageViewModel(
    private val scope: CoroutineScope,
    dependencies: MyPageDependencies = MyPageDependencies(),
) {
    var state by mutableStateOf<MyPageUiState>(MyPageUiState.LoggedOut)
        private set

    val transferCodeInputViewModel = TransferCodeInputViewModel(
        authRepository = dependencies.authRepository,
        transferRestoreService = dependencies.transferRestoreService,
    )

    private val authRepository: AuthRepository = dependencies.authRepository
    private val transferSecretStore: TransferSecretStore = dependencies.transferSecretStore
    private val gameSummaryService: GameSummaryService = dependencies.gameSummaryService

    init {
        scope.launch {
            authRepository.currentUser.collect { user ->
                if (user != null) loadGames() else state = MyPageUiState.LoggedOut
            }
        }
    }

    fun openGame(game: GameRecord) {
        val previousGames = (state as? MyPageUiState.GameList)?.games.orEmpty()
        state = MyPageUiState.LoadingDetail
        scope.launch {
            state = when (val outcome = gameSummaryService.getDetail(game.contentHash)) {
                is GameDetailOutcome.Loaded -> MyPageUiState.GameDetailView(outcome.detail, previousGames)
                GameDetailOutcome.NotAuthenticated -> MyPageUiState.LoggedOut
                GameDetailOutcome.NoSecret ->
                    MyPageUiState.Error(AppStrings.MYPAGE_ERROR_NO_SECRET, previousGames)
                GameDetailOutcome.NotFound ->
                    MyPageUiState.Error(AppStrings.MYPAGE_ERROR_NOT_FOUND, previousGames)
                is GameDetailOutcome.NetworkError ->
                    MyPageUiState.Error(AppStrings.MYPAGE_ERROR_NETWORK, previousGames)
            }
        }
    }

    /**
     * 詳細画面・エラー画面から一覧へ戻る。再取得せず保持済みの一覧をそのまま出す
     * （[MyPageUiState.GameDetailView.previousGames]・[MyPageUiState.Error.previousGames]）。
     */
    fun backToGameList() {
        val previousGames = when (val s = state) {
            is MyPageUiState.GameDetailView -> s.previousGames
            is MyPageUiState.Error -> s.previousGames
            else -> null
        } ?: return
        state = MyPageUiState.GameList(previousGames)
    }

    /**
     * 共有ブラウザでの利用を想定し、localStorageの端末シークレットとSupabaseセッションを
     * 両方消す。signOutが失敗してもセッションが残っている可能性があるため成功と偽らない
     * （復号鍵は失敗時も先に消す。鍵さえ無ければこのページから棋譜は読めない）。
     */
    fun logout() {
        scope.launch {
            val result = authRepository.signOut()
            transferSecretStore.clear()
            transferCodeInputViewModel.dismissError()
            state = if (result.isSuccess) {
                MyPageUiState.LoggedOut
            } else {
                MyPageUiState.Error(AppStrings.MYPAGE_ERROR_LOGOUT_FAILED)
            }
        }
    }

    private fun loadGames() {
        state = MyPageUiState.LoadingGames
        scope.launch {
            state = when (val outcome = gameSummaryService.listGames()) {
                is GameSummaryOutcome.Loaded -> MyPageUiState.GameList(outcome.games)
                GameSummaryOutcome.NotAuthenticated -> MyPageUiState.LoggedOut
                GameSummaryOutcome.NoSecret -> MyPageUiState.Error(AppStrings.MYPAGE_ERROR_NO_SECRET)
                is GameSummaryOutcome.NetworkError -> MyPageUiState.Error(AppStrings.MYPAGE_ERROR_NETWORK)
            }
        }
    }
}
