package dev.miyado.shogisupplement.db

import dev.miyado.shogisupplement.board.ShogiBoard
import dev.miyado.shogisupplement.board.ShogiMove
import dev.miyado.shogisupplement.pipeline.BlunderReport
import dev.miyado.shogisupplement.text.AppStrings
import dev.miyado.shogisupplement.util.currentEpochSeconds
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

/**
 * 棋譜・悪手レポート・局面評価値のDB永続化リポジトリ。
 */
class GameRepository(private val database: ShogiSupplementDatabase) {

    /**
     * 解析結果をDBに保存し、新しい game_id を返す。
     *
     * @param fileName ファイル名（表示用）
     * @param contentHash KIFコンテンツのSHA-256ハッシュ（重複検出用）
     * @param moves USI手列
     * @param headers KIFヘッダ（先手/後手名など）
     * @param reports 悪手レポートリスト
     * @param rating ユーザーレート
     * @param coefVersion 係数バージョン
     * @param analyzedAt 解析時刻（Unix epoch秒）
     * @param kifText KIF原文（今後の解析分から保存。旧解析はnull）
     * @param userSide ユーザーの側（"sente"/"gote"/null）
     * @param ratingService レートのサービス名（"lishogi"/"shogi_wars"/"kiou"）
     * @param ratingRaw サービス上のraw値（ウォーズは段級位を整数エンコード）
     * @return 新しく作成されたgame_id
     */
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
    ): Long {
        // 全局面の SFEN を事前計算: sfenAtPly[i] = i 手目を指す直前の局面
        val sfenAtPly = buildSfenSequence(moves)
        // USI手列をJSON配列として保存
        val movesUsiJson = Json.encodeToString(moves)

        return database.transactionWithResult {
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
            )
            val gameId = database.shogiSupplementQueries.getLastInsertRowId().executeAsOne()

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
                )
            }

            gameId
        }
    }

    /**
     * デモ/開発用フィクスチャ投入ヘルパー（iOSデモのドリルブートストラップ用）。
     *
     * [saveAnalysis] は指し手列（moves）から全局面のSFENを再計算して sfen_before を決めるが、
     * KIF実データを持たない合成フィクスチャ（iOSデモの初回DB空対策）では、指し手列を持たずに
     * 特定の局面SFENをそのまま保存したい。そのため sfenBefore を呼び出し元から直接受け取る
     * 専用の1件投入ヘルパーとして分離してある（[saveAnalysis] 自体のロジック・シグネチャは変更しない）。
     *
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
            )

            gameId
        }
    }

    /**
     * コンテンツハッシュで既存のgame_idを検索する（重複解析の回避）。
     * 見つからなければ null を返す。
     */
    fun getByHash(contentHash: String): Long? {
        return database.shogiSupplementQueries
            .getGameByHash(contentHash)
            .executeAsOneOrNull()
            ?.id
    }

    /** 全ゲームレコードを解析日時降順で返す。 */
    fun getAllGames(): List<GameRecord> {
        return database.shogiSupplementQueries
            .getAllGames()
            .executeAsList()
            .map { it.toGameRecord() }
    }

    /** 指定IDのゲームレコードを返す。見つからなければ null。 */
    fun getGameById(gameId: Long): GameRecord? {
        return database.shogiSupplementQueries
            .getGameById(gameId)
            .executeAsOneOrNull()
            ?.toGameRecord()
    }

    /** uploaded_at が NULL のゲームレコードを解析日時降順で返す。 */
    fun getNotUploadedGames(): List<GameRecord> {
        return database.shogiSupplementQueries
            .getGamesNotUploaded()
            .executeAsList()
            .map { it.toGameRecord() }
    }

    /** アップロード済みゲームの件数を返す（uploaded_at が設定されているもの）。 */
    fun getUploadedGameCount(): Int =
        getAllGames().count { it.uploadedAt != null }

    /** user_side が設定されているゲームレコードを解析日時降順で返す。 */
    fun getGamesWithUserSide(): List<GameRecord> {
        return getAllGames().filter { it.userSide != null }
    }

    /** アップロード成功時刻を記録する（Unix epoch 秒）。 */
    fun updateUploadedAt(gameId: Long, epochSeconds: Long) {
        database.shogiSupplementQueries.updateUploadedAt(epochSeconds, gameId)
    }

    /** ゲームの user_side / rating_service / rating_raw を更新する。 */
    fun updateUserSide(gameId: Long, userSide: String?, ratingService: String?, ratingRaw: Long?) {
        database.shogiSupplementQueries.updateUserSide(userSide, ratingService, ratingRaw, gameId)
    }

    /**
     * 全ゲームの uploaded_at を NULL にリセットする。
     * アカウント削除成功時に呼ぶ（サーバー側データが消えたため、
     * 再アップロード可能な状態に戻す）。端末内の棋譜・解析・ドリルはそのまま。
     */
    fun resetAllUploadedAt() {
        database.shogiSupplementQueries.resetAllUploadedAt()
    }

    /** 指定ゲームの悪手レポートリストを返す（ply昇順）。 */
    fun getReports(gameId: Long): List<BlunderRecord> {
        return database.shogiSupplementQueries
            .getBlundersByGameId(gameId)
            .executeAsList()
            .map { it.toBlunderRecord() }
    }

    /** 指定ゲームの指定サイドの悪手件数を返す。 */
    fun getBlunderCountBySide(gameId: Long, side: String): Int {
        return database.shogiSupplementQueries
            .getBlunderCountBySide(gameId, side)
            .executeAsOne()
            .toInt()
    }

    /**
     * best_pv をオンデマンド延長後に更新する。
     * @param blunderId blunder_report.id
     * @param newPv 新しい best_pv 文字列（スペース区切り USI 手列）
     */
    fun updateBestPv(blunderId: Long, newPv: String) {
        database.shogiSupplementQueries.updateBestPv(newPv, blunderId)
    }

    /**
     * 過去の累計自分の手数を返す（user_side が設定されている局のみ、自分の手番分のみ）。
     * 初回解析（過去局なし）は 0 を返す。
     */
    fun getPrevTotalMoves(): Int {
        return database.shogiSupplementQueries
            .getPrevTotalMoves()
            .executeAsOne()
            .toInt()
    }

    /**
     * 過去の累計自分の悪手数を返す（user_side が設定されている局のみ）。
     * 初回解析（過去局なし）は 0 を返す。
     */
    fun getPrevTotalBlunders(): Int {
        return database.shogiSupplementQueries
            .getPrevTotalBlunders()
            .executeAsOne()
            .toInt()
    }

    // ─── position_eval（全局面評価値）────────────────────────────────────────────

    /**
     * 全局面の評価値を一括保存する（先手視点 cp に正規化済み）。
     *
     * - ply = evals リストのインデックス（0 = 初期局面、N = N 手後の局面）
     * - scoreCp: 先手視点 cp（正 = 先手優勢）。詰み局面は null
     * - mateIn: 詰みまでの手数（正 = 先手が詰ます、負 = 後手が詰ます）。非詰みは null
     *
     * 同一 (game_id, ply) は OR REPLACE で上書きされる。
     */
    fun savePositionEvals(gameId: Long, rows: List<PositionEvalRow>) {
        database.transaction {
            rows.forEach { row ->
                database.shogiSupplementQueries.insertPositionEval(
                    game_id = gameId,
                    ply = row.ply.toLong(),
                    score_cp = row.scoreCp?.toLong(),
                    mate_in = row.mateIn?.toLong(),
                )
            }
        }
    }

    /**
     * 指定ゲームの全局面評価値を ply 昇順で返す。
     */
    fun getPositionEvals(gameId: Long): List<PositionEvalRow> {
        return database.shogiSupplementQueries
            .getPositionEvalsByGameId(gameId)
            .executeAsList()
            .map { PositionEvalRow(ply = it.ply.toInt(), scoreCp = it.score_cp?.toInt(), mateIn = it.mate_in?.toInt()) }
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
    sourcePlace = source_place,
    gameWinner = game_winner,
    endReason = end_reason,
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
)

/**
 * 保存済み note の表記を現行の表示形式に正規化する。
 * - 詰み手数バケット表記（1手/3手/5手/7手+）→ missed_mate_in の実手数
 * - 帯名のレート表記（例 1600-1899）→ 偏差値帯ラベル
 * DBマイグレーションで一括書き換えしない理由: note は自由文で、置換対象の特定を
 * 保存済み文字列だけに依存させると係数表やバケット定義の変更に追随できない。
 * 読み出し時置換なら missed_mate_in・帯名マップを常に正として表示できる。
 */
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

/** ゲームレコードのドメインモデル（UI用）。 */
data class GameRecord(
    val id: Long,
    val fileName: String,
    val contentHash: String,
    val moveCount: Long,
    val senteName: String?,
    val goteName: String?,
    val analyzedAt: Long,
    val rating: Long,
    /** rating推定に使った集計対象手数（自分の手のみ・今局＋過去累計）。フィクスチャ投入等ではnull。 */
    val ratingSampleMoves: Long? = null,
    val coefVersion: String,
    val kifText: String? = null,
    val uploadedAt: Long? = null,
    val movesUsi: List<String> = emptyList(),
    val userSide: String? = null,
    val ratingService: String? = null,
    val ratingRaw: Long? = null,
    val ratingRule: String? = null,
    /** KIFの「場所」ヘッダ値（将棋ウォーズ等）。ヘッダなし→null。 */
    val sourcePlace: String? = null,
    /** 勝者（"sente"/"gote"/null）。 */
    val gameWinner: String? = null,
    /** 終局語（"投了"/"切れ負け"等）。 */
    val endReason: String? = null,
)

/** 悪手レポートのドメインモデル（UI用）。 */
data class BlunderRecord(
    val id: Long,
    val gameId: Long,
    val ply: Long,
    val side: String,
    val moveUsi: String,
    val bestUsi: String?,
    val lossWp: Double,
    val sfenBefore: String,
    val category: String,
    val diffMaterial: Long,
    val punishChecks: Long,
    val tookMovedPiece: Boolean,
    val missedMateIn: Long?,
    val verdict: String,
    val note: String,
    val problemType: String,
    val priority: Double,
    val bestPv: String? = null,
    val punishPv: String? = null,
    /**
     * 悪手前局面の評価値（手番側視点 cp。BlunderJudge.toCp 準拠）。
     * 新規解析分から保存。既存レコードは null（→ cp モードでも勝率表示にフォールバック）。
     */
    val cpBefore: Long? = null,
    /**
     * 悪手後局面の評価値（次手番側視点 cp。BlunderJudge.toCp 準拠）。
     * 損失 cp = cpBefore + cpAfter（cpAfter は相手視点なので加算して手番側の損失量になる）。
     */
    val cpAfter: Long? = null,
)

/**
 * 局面評価値のドメインモデル（position_eval テーブル対応）。
 *
 * 先手視点に正規化されている（正 = 先手優勢）。
 * - scoreCp: 評価値 cp。詰み局面は null にして mateIn を使う。
 * - mateIn: 詰みまでの手数（正 = 先手が詰ます、負 = 後手が詰ます）。非詰み局面は null。
 */
data class PositionEvalRow(
    val ply: Int,
    val scoreCp: Int?,
    val mateIn: Int?,
)
