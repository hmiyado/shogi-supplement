package dev.miyado.shogisupplement.db

import dev.miyado.shogisupplement.pipeline.BlunderReport
import dev.miyado.shogisupplement.util.currentEpochSeconds

/** 棋譜・悪手レポート・局面評価の永続化。 */
interface GameRepository {

    /** 編集済みの検討KIF。未編集なら解析元のKIFを返す。 */
    fun getStudyKif(gameId: Long): String? = error("Study persistence is not supported")

    /** 読込時のKIFと一致する場合だけ保存する。本譜・解析結果は変更しない。 */
    fun saveStudyKif(gameId: Long, expectedKif: String, kif: String): Boolean = error("Study persistence is not supported")

    data class StudyUploadSnapshot(
        val gameId: Long,
        val contentHash: String,
        val originalKif: String,
        val studyKif: String,
        val expectedRemoteKif: String,
        val revision: Long,
    )

    /** 解析・ドリルとは独立した未送信の検討文書。 */
    fun getPendingStudyUploads(): List<StudyUploadSnapshot> = emptyList()
    fun acknowledgeStudyUpload(gameId: Long, revision: Long, uploadedKif: String) = Unit

    data class AnalysisUploadSnapshot(
        val game: GameRecord,
        val reports: List<BlunderRecord>,
        val problems: List<BlunderRecord>,
        val revision: Long,
        val studyRevision: Long = 0,
        val generation: String? = null,
    )

    /** 棋譜・レポート・問題・更新番号を同一DBトランザクションで取得する。 */
    fun getAnalysisUploadSnapshot(gameId: Long): AnalysisUploadSnapshot? = null

    /** null世代を確認済みであることと、まだ送信先を確認していないことを区別する。 */
    data class FrozenStudy(val kif: String, val revision: Long)
    data class AnalysisRemoteBase(val generation: String?)
    fun getAnalysisRemoteBase(gameId: Long, userId: String): AnalysisRemoteBase? = null
    fun confirmRestoredGame(gameId: Long, userId: String, generation: String?, epochSeconds: Long) {
        markRestoredPendingGameUploaded(gameId, epochSeconds)
    }
    fun acknowledgeAnalysisGeneration(gameId: Long, userId: String, generation: String, revision: Long, epochSeconds: Long) {
        markAnalysisUploaded(gameId, revision, epochSeconds)
    }
    data class AnalysisDeleteTarget(val expectedGeneration: String?, val requestId: String)
    fun getAnalysisDeleteTarget(gameId: Long, userId: String, generation: String): AnalysisDeleteTarget? = null
    fun freezeAnalysisDeleteTarget(gameId: Long, userId: String, generation: String, target: AnalysisDeleteTarget): AnalysisDeleteTarget? = null
    data class AnalysisSyncTarget(val expectedGeneration: String?, val study: FrozenStudy? = null)

    fun getAnalysisSyncTarget(gameId: Long, userId: String, generation: String): AnalysisSyncTarget? = null

    /** 最初の送信前に比較対象を固定する。再送で変更せず、古いローカル世代は拒否する。 */
    fun freezeAnalysisSyncTarget(
        gameId: Long, userId: String, generation: String, expectedGeneration: String?,
        study: FrozenStudy? = null,
    ): AnalysisSyncTarget? = null

    /** 解析結果を一括して永続化するための入力。 */
    data class AnalysisSaveRequest(
        val fileName: String,
        val contentHash: String,
        val moves: List<String>,
        val headers: Map<String, String>,
        val reports: List<BlunderReport>,
        val rating: Int,
        val ratingSampleMoves: Int? = null,
        val coefVersion: String,
        val analyzedAt: Long = currentEpochSeconds(),
        val kifText: String? = null,
        val userSide: String? = null,
        val ratingService: String? = null,
        val ratingRaw: Long? = null,
        val ratingRule: String? = null,
        val ratingDeclaredAt: Long? = null,
        val sourcePlace: String? = null,
        val gameWinner: String? = null,
        val endReason: String? = null,
        val openingStyle: String? = null,
        val openingCastle: String? = null,
        val openingTags: String? = null,
        val senteRating: Long? = null,
        val goteRating: Long? = null,
        val timeControlRaw: String? = null,
        val timeControlByoyomiRaw: String? = null,
        val engineMetaJson: String? = null,
        val positionEvalRows: List<PositionEvalRow> = emptyList(),
        val requestId: String? = null,
    )

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
        ratingDeclaredAt: Long? = null,
        sourcePlace: String? = null,
        gameWinner: String? = null,
        endReason: String? = null,
        senteRating: Long? = null,
        goteRating: Long? = null,
        timeControlRaw: String? = null,
        timeControlByoyomiRaw: String? = null,
        studyKif: String? = null,
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
        ratingDeclaredAt: Long? = null,
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
        engineMetaJson: String? = null,
    ): Long

    /** 解析本体と派生した局面評価を同一トランザクションで保存する。 */
    fun saveAnalysisAtomically(request: AnalysisSaveRequest): Long

    /** 保存済み要求の再送では解析・派生履歴の置換を繰り返さない。 */
    fun getAppliedAnalysis(contentHash: String, requestId: String): Long? = null

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

    /** 解析日時が新しい順で、指定件数だけゲームレコードを返す。 */
    fun getRecentGames(limit: Int): List<GameRecord> = getAllGames().take(limit)

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

    /** 復元した未解析棋譜のみ送信済みにする。解析完了後の結果には触れない。 */
    fun markRestoredPendingGameUploaded(gameId: Long, epochSeconds: Long): Unit =
        error("Restored pending games are not supported by this repository")

    /** 送信対象を読み込む前に取得する、ローカル解析結果の更新番号。 */
    fun getAnalysisRevision(gameId: Long): Long? = null

    /** 送信開始後に解析結果が置換された場合は送信済みにしない。 */
    fun markAnalysisUploaded(gameId: Long, revision: Long, epochSeconds: Long) {}

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

    /** 棋譜IDごとの悪手件数。棋譜一覧の絞り込み結果に対する悪手率の分子。 */
    fun getBlunderCounts(): Map<Long, Int>

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
