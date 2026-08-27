package dev.miyado.shogisupplement.webApp.mypage

import dev.miyado.shogisupplement.db.GameRecord
import dev.miyado.shogisupplement.download.GameDetail

/** 「マイページ」（引き継ぎコードでログインして自分の棋譜一覧を見る）の表示状態。 */
sealed class MyPageUiState {
    data object LoggedOut : MyPageUiState()
    data object LoadingGames : MyPageUiState()

    /** [games] は一覧表示だけでなく、詳細表示の呼び出し元へ戻るためにも保持する。 */
    data class GameList(val games: List<GameRecord>) : MyPageUiState()
    data object LoadingDetail : MyPageUiState()
    data class GameDetailView(val detail: GameDetail, val previousGames: List<GameRecord>) : MyPageUiState()

    /** [previousGames] が非nullなら「一覧へ戻る」導線を出す（詳細取得失敗等、一覧から遷移した場合）。 */
    data class Error(val message: String, val previousGames: List<GameRecord>? = null) : MyPageUiState()
}
