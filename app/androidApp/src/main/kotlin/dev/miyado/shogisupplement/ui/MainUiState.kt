package dev.miyado.shogisupplement.ui

import dev.miyado.shogisupplement.db.BlunderRecord
import dev.miyado.shogisupplement.db.GameRecord
import dev.miyado.shogisupplement.db.PositionEvalRow
import dev.miyado.shogisupplement.pipeline.ProgressiveReportState
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

    /**
     * 解析中レポート画面。解析開始と同時にこの状態へ遷移し、完了したら
     * [ShowReport] へ差し替わる。GameRecordはDB保存後にしか存在しないため、
     * このデータクラスはGameRecordを持たずKIFパース直後に確定する情報だけで構成する。
     *
     * @param titleHint トップバーの暫定タイトル。確定後の表示名とは一致しないことがある
     */
    data class AnalyzingReport(
        val titleHint: String,
        val moves: List<String>,
        val userSide: String?,
        val progressive: ProgressiveReportState,
    ) : MainUiState()
    data class ShowReport(
        val game: GameRecord,
        val reports: List<BlunderRecord>,
        val flip: Boolean = false,
        val strengthDisplayText: String? = null,
        /** 形勢の表示単位（'cp' = 評価値 / 'wp' = 勝率）。 */
        val evalDisplay: String = "cp",
        /** 全局面評価値（手送り時の形勢表示。空 = 非表示）。 */
        val positionEvals: List<PositionEvalRow> = emptyList(),
        /** エンジン一致率の値表示（例:「62%(31/50)」）。null = 非表示。 */
        val matchRateDisplayText: String? = null,
        /** 悪手率の値表示（例:「12%(3/25)」）。一致率と同じ分母nを使う。null = 非表示。 */
        val blunderRateDisplayText: String? = null,
        /**
         * この画面遷移が[AnalyzingReport]からの解析完了直後かどうか。trueのときだけ
         * 完了通知バナーを一度出す（通知タップ・棋譜一覧経由の表示ではfalseのまま）。
         */
        val justCompleted: Boolean = false,
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
