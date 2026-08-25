package dev.miyado.shogisupplement.db

import dev.miyado.shogisupplement.board.ShogiBoard
import dev.miyado.shogisupplement.board.ShogiMove
import dev.miyado.shogisupplement.kifu.KifuDecomposer
import dev.miyado.shogisupplement.kifu.KifuSource
import dev.miyado.shogisupplement.pipeline.BlunderReport
import dev.miyado.shogisupplement.text.AppStrings
import dev.miyado.shogisupplement.util.currentEpochSeconds
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

/** 棋譜・悪手レポート・局面評価値のDB永続化リポジトリ（[GameRepository]のSQLDelight実装）。 */
class SqlDelightGameRepository(private val database: ShogiSupplementDatabase) : GameRepository {

    override fun savePendingGame(
        fileName: String,
        contentHash: String,
        moves: List<String>,
        headers: Map<String, String>,
        importedAt: Long,
        kifText: String,
        userSide: String?,
        ratingService: String?,
        ratingRaw: Long?,
        ratingRule: String?,
        sourcePlace: String?,
        gameWinner: String?,
        endReason: String?,
    ): Long = database.transactionWithResult {
        database.shogiSupplementQueries.insertGame(
            file_name = fileName,
            content_hash = contentHash,
            move_count = moves.size.toLong(),
            sente_name = headers["先手"],
            gote_name = headers["後手"],
            analyzed_at = importedAt,
            rating = 0,
            rating_sample_moves = null,
            coef_version = "",
            kif_text = kifText,
            moves_usi = Json.encodeToString(moves),
            user_side = userSide,
            rating_service = ratingService,
            rating_raw = ratingRaw,
            rating_rule = ratingRule,
            source_place = sourcePlace,
            game_winner = gameWinner,
            end_reason = endReason,
            analysis_status = GameAnalysisStatus.PENDING.wireValue,
            opening_style = null,
            opening_castle = null,
            opening_tags = null,
        )
        database.shogiSupplementQueries.getLastInsertRowId().executeAsOne()
    }

    override fun saveAnalysis(
        fileName: String,
        contentHash: String,
        moves: List<String>,
        headers: Map<String, String>,
        reports: List<BlunderReport>,
        rating: Int,
        ratingSampleMoves: Int?,
        coefVersion: String,
        analyzedAt: Long,
        kifText: String?,
        userSide: String?,
        ratingService: String?,
        ratingRaw: Long?,
        ratingRule: String?,
        sourcePlace: String?,
        gameWinner: String?,
        endReason: String?,
        openingStyle: String?,
        openingCastle: String?,
        openingTags: String?,
    ): Long {
        // 全局面の SFEN を事前計算: sfenAtPly[i] = i 手目を指す直前の局面
        val sfenAtPly = buildSfenSequence(moves)
        // USI手列をJSON配列として保存
        val movesUsiJson = Json.encodeToString(moves)

        return database.transactionWithResult {
            val pendingId = database.shogiSupplementQueries.getGameByHash(contentHash)
                .executeAsOneOrNull()
                ?.takeIf { it.analysis_status == GameAnalysisStatus.PENDING.wireValue }
                ?.id
            if (pendingId == null) {
                database.shogiSupplementQueries.insertGame(
                    file_name = fileName,
                    content_hash = contentHash,
                    move_count = moves.size.toLong(),
                    sente_name = headers["先手"],
                    gote_name = headers["後手"],
                    analyzed_at = analyzedAt,
                    rating = rating.toLong(),
                    rating_sample_moves = ratingSampleMoves?.toLong(),
                    coef_version = coefVersion,
                    kif_text = kifText,
                    moves_usi = movesUsiJson,
                    user_side = userSide,
                    rating_service = ratingService,
                    rating_raw = ratingRaw,
                    rating_rule = ratingRule,
                    source_place = sourcePlace,
                    game_winner = gameWinner,
                    end_reason = endReason,
                    analysis_status = GameAnalysisStatus.COMPLETED.wireValue,
                    opening_style = openingStyle,
                    opening_castle = openingCastle,
                    opening_tags = openingTags,
                )
            } else {
                database.shogiSupplementQueries.completePendingGame(
                    file_name = fileName,
                    move_count = moves.size.toLong(),
                    sente_name = headers["先手"],
                    gote_name = headers["後手"],
                    analyzed_at = analyzedAt,
                    rating = rating.toLong(),
                    rating_sample_moves = ratingSampleMoves?.toLong(),
                    coef_version = coefVersion,
                    kif_text = kifText,
                    moves_usi = movesUsiJson,
                    user_side = userSide,
                    rating_service = ratingService,
                    rating_raw = ratingRaw,
                    rating_rule = ratingRule,
                    source_place = sourcePlace,
                    game_winner = gameWinner,
                    end_reason = endReason,
                    opening_style = openingStyle,
                    opening_castle = openingCastle,
                    opening_tags = openingTags,
                    id = pendingId,
                )
            }
            val gameId = pendingId ?: database.shogiSupplementQueries.getLastInsertRowId().executeAsOne()

            reports.forEach { report ->
                // report.ply は 1 始まり。直前局面は sfenAtPly[report.ply - 1]
                val sfenBefore = sfenAtPly.getOrElse(report.ply - 1) {
                    "startpos moves " + moves.take(report.ply - 1).joinToString(" ")
                }
                database.shogiSupplementQueries.insertBlunderReport(
                    game_id = gameId,
                    ply = report.ply.toLong(),
                    side = report.side,
                    move_usi = report.moveUsi,
                    best_usi = report.bestUsi,
                    loss_wp = report.lossWp,
                    sfen_before = sfenBefore,
                    category = report.classification.category,
                    diff_material = report.classification.diffMaterial.toLong(),
                    punish_checks = report.classification.punishChecks.toLong(),
                    took_moved_piece = if (report.classification.tookMovedPiece) 1L else 0L,
                    missed_mate_in = report.classification.missedMateIn?.toLong(),
                    verdict = report.judgement.verdict,
                    note = report.judgement.note,
                    problem_type = report.judgement.problem,
                    priority = report.judgement.priority,
                    best_pv = report.bestPv,
                    punish_pv = report.punishPv,
                    cp_before = report.cpBefore?.toLong(),
                    cp_after = report.cpAfter?.toLong(),
                    second_usi = report.secondUsi,
                    second_cp = report.secondCp?.toLong(),
                )
            }

            gameId
        }
    }

    /** デモ/開発用フィクスチャ投入ヘルパー（iOSデモのドリルブートストラップ用）。 */
    override fun seedFixtureBlunder(
        fileName: String,
        contentHash: String,
        rating: Int,
        coefVersion: String,
        report: BlunderReport,
        sfenBefore: String,
        userSide: String?,
        senteName: String?,
        goteName: String?,
        analyzedAt: Long,
    ): Long {
        return database.transactionWithResult {
            database.shogiSupplementQueries.insertGame(
                file_name = fileName,
                content_hash = contentHash,
                move_count = report.ply.toLong(),
                sente_name = senteName,
                gote_name = goteName,
                analyzed_at = analyzedAt,
                rating = rating.toLong(),
                rating_sample_moves = null,
                coef_version = coefVersion,
                kif_text = null,
                moves_usi = null,
                user_side = userSide,
                rating_service = null,
                rating_raw = null,
                rating_rule = null,
                source_place = null,
                game_winner = null,
                end_reason = null,
                analysis_status = GameAnalysisStatus.COMPLETED.wireValue,
                opening_style = null,
                opening_castle = null,
                opening_tags = null,
            )
            val gameId = database.shogiSupplementQueries.getLastInsertRowId().executeAsOne()

            database.shogiSupplementQueries.insertBlunderReport(
                game_id = gameId,
                ply = report.ply.toLong(),
                side = report.side,
                move_usi = report.moveUsi,
                best_usi = report.bestUsi,
                loss_wp = report.lossWp,
                sfen_before = sfenBefore,
                category = report.classification.category,
                diff_material = report.classification.diffMaterial.toLong(),
                punish_checks = report.classification.punishChecks.toLong(),
                took_moved_piece = if (report.classification.tookMovedPiece) 1L else 0L,
                missed_mate_in = report.classification.missedMateIn?.toLong(),
                verdict = report.judgement.verdict,
                note = report.judgement.note,
                problem_type = report.judgement.problem,
                priority = report.judgement.priority,
                best_pv = report.bestPv,
                punish_pv = report.punishPv,
                cp_before = report.cpBefore?.toLong(),
                cp_after = report.cpAfter?.toLong(),
                second_usi = report.secondUsi,
                second_cp = report.secondCp?.toLong(),
            )

            gameId
        }
    }

    /**
     * コンテンツハッシュで既存のgame_idを検索する（重複解析の回避）。
     * 見つからなければ null を返す。
     */
    override fun getByHash(contentHash: String): Long? {
        return database.shogiSupplementQueries
            .getGameByHash(contentHash)
            .executeAsOneOrNull()
            ?.id
    }

    /** 全ゲームレコードを解析日時降順で返す。 */
    override fun getAllGames(): List<GameRecord> {
        return database.shogiSupplementQueries
            .getAllGames()
            .executeAsList()
            .map { it.toGameRecord() }
    }

    /** 指定IDのゲームレコードを返す。見つからなければ null。 */
    override fun getGameById(gameId: Long): GameRecord? {
        return database.shogiSupplementQueries
            .getGameById(gameId)
            .executeAsOneOrNull()
            ?.toGameRecord()
    }

    /** uploaded_at が NULL のゲームレコードを解析日時降順で返す。 */
    override fun getNotUploadedGames(): List<GameRecord> {
        return database.shogiSupplementQueries
            .getGamesNotUploaded()
            .executeAsList()
            .map { it.toGameRecord() }
    }

    /** アップロード済みゲームの件数を返す（uploaded_at が設定されているもの）。 */
    override fun getUploadedGameCount(): Int =
        getAllGames().count { it.uploadedAt != null }

    /** user_side が設定されているゲームレコードを解析日時降順で返す。 */
    override fun getGamesWithUserSide(): List<GameRecord> {
        return getAllGames().filter {
            it.userSide != null && it.analysisStatus == GameAnalysisStatus.COMPLETED
        }
    }

    /** アップロード成功時刻を記録する（Unix epoch 秒）。 */
    override fun updateUploadedAt(gameId: Long, epochSeconds: Long) {
        database.shogiSupplementQueries.updateUploadedAt(epochSeconds, gameId)
    }

    /** ゲームの user_side / rating_service / rating_raw を更新する。 */
    override fun updateUserSide(gameId: Long, userSide: String?, ratingService: String?, ratingRaw: Long?) {
        database.shogiSupplementQueries.updateUserSide(userSide, ratingService, ratingRaw, gameId)
    }

    /**
     * 全ゲームの uploaded_at を NULL にリセットする。
     * アカウント削除成功時に呼ぶ（サーバー側データが消えたため、
     * 再アップロード可能な状態に戻す）。端末内の棋譜・解析・ドリルはそのまま。
     */
    override fun resetAllUploadedAt() {
        database.shogiSupplementQueries.resetAllUploadedAt()
    }

    /** 指定ゲームの悪手レポートリストを返す（ply昇順）。 */
    override fun getReports(gameId: Long): List<BlunderRecord> {
        return database.shogiSupplementQueries
            .getBlundersByGameId(gameId)
            .executeAsList()
            .map { it.toBlunderRecord() }
    }

    /**
     * best_pv をオンデマンド延長後に更新する。
     * @param blunderId blunder_report.id
     * @param newPv 新しい best_pv 文字列（スペース区切り USI 手列）
     */
    override fun updateBestPv(blunderId: Long, newPv: String) {
        database.shogiSupplementQueries.updateBestPv(newPv, blunderId)
    }

    // ─── position_eval（全局面評価値）────────────────────────────────────────────

    /** 全局面の評価値を一括保存する（先手視点 cp に正規化済み）。 */
    override fun savePositionEvals(gameId: Long, rows: List<PositionEvalRow>) {
        database.transaction {
            rows.forEach { row ->
                database.shogiSupplementQueries.insertPositionEval(
                    game_id = gameId,
                    ply = row.ply.toLong(),
                    score_cp = row.scoreCp?.toLong(),
                    mate_in = row.mateIn?.toLong(),
                    best_usi = row.bestUsi,
                    second_score_cp = row.secondScoreCp?.toLong(),
                    second_mate_in = row.secondMateIn?.toLong(),
                    second_usi = row.secondUsi,
                )
            }
        }
    }

    /** 指定ゲームの全局面評価値を ply 昇順で返す。 */
    override fun getPositionEvals(gameId: Long): List<PositionEvalRow> {
        return database.shogiSupplementQueries
            .getPositionEvalsByGameId(gameId)
            .executeAsList()
            .map {
                PositionEvalRow(
                    ply = it.ply.toInt(),
                    scoreCp = it.score_cp?.toInt(),
                    mateIn = it.mate_in?.toInt(),
                    bestUsi = it.best_usi,
                    secondUsi = it.second_usi,
                )
            }
    }

    override fun deleteGame(gameId: Long) {
        database.transaction {
            database.shogiSupplementQueries.deleteDrillAttemptsByGameId(gameId)
            database.shogiSupplementQueries.deleteBlunderReportsByGameId(gameId)
            database.shogiSupplementQueries.deletePositionEvalsByGameId(gameId)
            database.shogiSupplementQueries.deleteGameById(gameId)
        }
    }

    override fun deleteAllLocalData() {
        database.transaction {
            database.shogiSupplementQueries.deleteAllBlunderReports()
            database.shogiSupplementQueries.deleteAllPositionEvals()
            database.shogiSupplementQueries.deleteAllDrillAttempts()
            database.shogiSupplementQueries.deleteAllGames()
            database.shogiSupplementQueries.deleteAllServiceRanks()
            database.shogiSupplementQueries.deleteAllServiceAccounts()
            database.shogiSupplementQueries.deleteAllUserSettings()
        }
    }
}

// --- SQLDelight生成型 → ドメイン型への変換 ---
// internal: DrillRepository（getDrillCandidates）からも悪手レコード変換を再利用するため。

internal fun Game.toGameRecord() = GameRecord(
    id = id,
    fileName = file_name,
    contentHash = content_hash,
    moveCount = move_count,
    senteName = sente_name,
    goteName = gote_name,
    analyzedAt = analyzed_at,
    rating = rating,
    ratingSampleMoves = rating_sample_moves,
    coefVersion = coef_version,
    kifText = kif_text,
    uploadedAt = uploaded_at,
    movesUsi = moves_usi?.let {
        runCatching { Json.decodeFromString<List<String>>(it) }.getOrElse { emptyList() }
    } ?: emptyList(),
    userSide = user_side,
    ratingService = rating_service,
    ratingRaw = rating_raw,
    ratingRule = rating_rule,
    sourcePlace = normalizeLegacySourcePlace(source_place),
    gameWinner = game_winner,
    endReason = end_reason,
    analysisStatus = GameAnalysisStatus.fromWireValue(analysis_status),
    openingStyle = opening_style,
    openingCastle = opening_castle,
    openingTags = opening_tags,
)

internal fun Blunder_report.toBlunderRecord() = BlunderRecord(
    id = id,
    gameId = game_id,
    ply = ply,
    side = side,
    moveUsi = move_usi,
    bestUsi = best_usi,
    lossWp = loss_wp,
    sfenBefore = convertLegacySfen(sfen_before),
    category = category,
    diffMaterial = diff_material,
    punishChecks = punish_checks,
    tookMovedPiece = took_moved_piece != 0L,
    missedMateIn = missed_mate_in,
    verdict = verdict,
    note = normalizeLegacyNote(note, missed_mate_in),
    problemType = problem_type,
    priority = priority,
    bestPv = best_pv,
    punishPv = punish_pv,
    cpBefore = cp_before,
    cpAfter = cp_after,
    secondUsi = second_usi,
    secondCp = second_cp,
)

/** 保存済み note の表記を現行の表示形式に正規化する。 */
private fun normalizeLegacyNote(note: String, missedMateIn: Long?): String {
    var s = note
    if (missedMateIn != null) {
        s = s.replace(Regex("の(?:1|3|5|7)手\\+?詰の"), "の${missedMateIn}手詰の")
    }
    for ((band, label) in AppStrings.bandDeviationLabels) {
        s = s.replace("($band)", "($label)")
    }
    return s
}

/** 保存済み source_place の表記を [KifuSource] の正規化値（wireValue）に揃える。 */
private fun normalizeLegacySourcePlace(sourcePlace: String?): String? {
    if (sourcePlace == null) return null
    if (KifuSource.entries.any { it.wireValue == sourcePlace }) return sourcePlace
    return KifuDecomposer.classifySource(rawText = "", place = sourcePlace).wireValue
}

// ─── SFEN ヘルパー ───────────────────────────────────────────────────────

/**
 * 棋譜の全局面 SFEN を返す。
 * sfenAtPly[i] = i 番目の指し手を指す直前の局面（i=0 が初期局面）。
 * 途中で不正な指し手があった場合はそこで打ち切り、残りは getOrElse のフォールバックに任せる。
 */
private fun buildSfenSequence(moves: List<String>): List<String> {
    val board = ShogiBoard()
    val result = ArrayList<String>(moves.size + 1)
    result.add(board.toSfen())
    for (usiStr in moves) {
        try {
            board.push(ShogiMove.fromUsi(usiStr))
            result.add(board.toSfen())
        } catch (_: Exception) {
            break
        }
    }
    return result
}

/**
 * 旧形式（"startpos moves ..."）の sfen_before を SFEN に変換する。
 * 既に SFEN 形式（"lnsgkgsnl/..."で始まる）の場合はそのまま返す。
 */
private fun convertLegacySfen(sfenBefore: String): String {
    if (!sfenBefore.startsWith("startpos")) return sfenBefore
    val parts = sfenBefore.split(" ")
    val moveList = if (parts.size > 2 && parts[1] == "moves") parts.drop(2) else emptyList()
    val board = ShogiBoard()
    for (usiStr in moveList) {
        board.push(ShogiMove.fromUsi(usiStr))
    }
    return board.toSfen()

}
