package dev.miyado.shogisupplement.ui

import dev.miyado.shogisupplement.db.BlunderRecord
import dev.miyado.shogisupplement.db.GameRecord
import dev.miyado.shogisupplement.db.PositionEvalRow
import dev.miyado.shogisupplement.ui.home.StrengthCardData
import dev.miyado.shogisupplement.ui.home.TodaysDrillHint

/** メイン画面のUI状態。 */
sealed class MainUiState {
    object Loading : MainUiState()
    data class Home(
        val pastGames: List<GameRecord>,
        val isLoggedIn: Boolean = false,
        val isUploading: Boolean = false,
        val strengthCard: StrengthCardData? = null,
        val todaysDrillHint: TodaysDrillHint? = null,
    ) : MainUiState()
    data class Analyzing(val done: Int, val total: Int) : MainUiState()
    data class ShowReport(
        val game: GameRecord,
        val reports: List<BlunderRecord>,
        val flip: Boolean = false,
        val strengthDisplayText: String? = null,
        /** 形勢の表示単位（'cp' = 評価値 / 'wp' = 勝率）。 */
        val evalDisplay: String = "cp",
        /** 全局面評価値（手送り時の形勢表示。空 = 非表示）。 */
        val positionEvals: List<PositionEvalRow> = emptyList(),
    ) : MainUiState()
    data class Error(val message: String, val pastGames: List<GameRecord> = emptyList()) : MainUiState()
    /** ドリル画面に遷移する。 */
    object Drill : MainUiState()
    /** アカウント画面に遷移する。 */
    object Account : MainUiState()
    /** OSSライセンス一覧画面に遷移する（戻り先は設定画面）。 */
    object Licenses : MainUiState()
    /** 設定画面（棋力・アカウント・規約・ライセンスの集約ハブ）。 */
    object Settings : MainUiState()
    /** デバッグ画面（BuildConfig.DEBUG のみ表示）。 */
    object Debug : MainUiState()
    /** 棋譜一覧画面。 */
    data class GameList(
        val games: List<GameRecord>,
        /** ログイン中かつ未アップロードがある場合のカウント（0 = ボタン非表示）。 */
        val pendingUploadCount: Int = 0,
        val isUploading: Boolean = false,
        val uploadResult: String? = null,
    ) : MainUiState()
}
