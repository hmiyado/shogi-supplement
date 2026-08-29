package dev.miyado.shogisupplement.db

import dev.miyado.shogisupplement.pipeline.BlunderReport
import dev.miyado.shogisupplement.util.currentEpochSeconds

/** 棋譜・悪手レポート・局面評価の永続化。 */
interface GameRepository {

    fun savePendingGame(
        fileName: String,
        contentHash: String,
        moves: List<String>,
        headers: Map<String, String>,
        importedAt: Long = currentEpochSeconds(),
        kifText: String,
        userSide: String?,
        ratingService: String? = null,
        ratingRaw: Long? = null,
        ratingRule: String? = null,
        sourcePlace: String? = null,
        gameWinner: String? = null,
        endReason: String? = null,
        senteRating: Long? = null,
        goteRating: Long? = null,
        timeControlRaw: String? = null,
        timeControlByoyomiRaw: String? = null,
    ): Long = error("Pending games are not supported by this repository")

    /** 解析結果を保存し、新しい game_id を返す。 */

    fun saveAnalysis(
        fileName: String,
        contentHash: String,
        moves: List<String>,
        headers: Map<String, String>,
        reports: List<BlunderReport>,
        rating: Int,
        ratingSampleMoves: Int? = null,
        coefVersion: String,
        analyzedAt: Long = currentEpochSeconds(),
        kifText: String? = null,
        userSide: String? = null,
        ratingService: String? = null,
        ratingRaw: Long? = null,
        ratingRule: String? = null,
        sourcePlace: String? = null,
        gameWinner: String? = null,
        endReason: String? = null,
        openingStyle: String? = null,
        openingCastle: String? = null,
        openingTags: String? = null,
        senteRating: Long? = null,
        goteRating: Long? = null,
        timeControlRaw: String? = null,
        timeControlByoyomiRaw: String? = null,
    ): Long

    /**
     * デモ/開発用フィクスチャ投入ヘルパー（iOSデモのドリルブートストラップ用）。
     * @return 新しく作成された game_id
     */
    fun seedFixtureBlunder(
        fileName: String,
        contentHash: String,
        rating: Int,
        coefVersion: String,
        report: BlunderReport,
        sfenBefore: String,
        userSide: String? = null,
        senteName: String? = null,
        goteName: String? = null,
        analyzedAt: Long = currentEpochSeconds(),
    ): Long

    /**
     * コンテンツハッシュで既存のgame_idを検索する（重複解析の回避）。
     * 見つからなければ null を返す。
     */
    fun getByHash(contentHash: String): Long?

    /** 全ゲームレコードを解析日時降順で返す。 */
    fun getAllGames(): List<GameRecord>

    /** 指定IDのゲームレコードを返す。見つからなければ null。 */
    fun getGameById(gameId: Long): GameRecord?

    /** uploaded_at が NULL のゲームレコードを解析日時降順で返す。 */
    fun getNotUploadedGames(): List<GameRecord>

    /** アップロード済みゲームの件数を返す（uploaded_at が設定されているもの）。 */
    fun getUploadedGameCount(): Int

    /** user_side が設定されているゲームレコードを解析日時降順で返す。 */
    fun getGamesWithUserSide(): List<GameRecord>

    fun getPendingGames(): List<GameRecord> =
        getAllGames().filter { it.analysisStatus == GameAnalysisStatus.PENDING }

    /** アップロード成功時刻を記録する（Unix epoch 秒）。 */
    fun updateUploadedAt(gameId: Long, epochSeconds: Long)

    /** ゲームの user_side / rating_service / rating_raw を更新する。 */
    fun updateUserSide(gameId: Long, userSide: String?, ratingService: String?, ratingRaw: Long?)

    /** ゲームの対局者名を更新する。ローカルのみで、サーバーに保存済みの記録は変更しない。 */
    fun updateGamePlayers(gameId: Long, senteName: String?, goteName: String?)

    /**
     * 全ゲームの uploaded_at を NULL にリセットする。
     * アカウント削除成功時に呼ぶ（サーバー側データが消えたため、
     * 再アップロード可能な状態に戻す）。端末内の棋譜・解析・ドリルはそのまま。
     */
    fun resetAllUploadedAt()

    /** 指定ゲームの悪手レポートリストを返す（ply昇順）。 */
    fun getReports(gameId: Long): List<BlunderRecord>

    /**
     * best_pv をオンデマンド延長後に更新する。
     * @param blunderId blunder_report.id
     * @param newPv 新しい best_pv 文字列（スペース区切り USI 手列）
     */
    fun updateBestPv(blunderId: Long, newPv: String)

    /**
     * 全局面の評価値を一括保存する（先手視点 cp に正規化済み）。
     * 同一 (game_id, ply) は OR REPLACE で上書きされる。
     */
    fun savePositionEvals(gameId: Long, rows: List<PositionEvalRow>)

    /** 指定ゲームの全局面評価値を ply 昇順で返す。 */
    fun getPositionEvals(gameId: Long): List<PositionEvalRow>

    /** 指定ゲームを悪手レポート・局面評価・ドリル履歴も含めてカスケード削除する。 */
    fun deleteGame(gameId: Long)

    /** 端末内のデータをすべて消す（デバッグ画面の初期状態からの動作確認用）。 */
    fun deleteAllLocalData()
}
