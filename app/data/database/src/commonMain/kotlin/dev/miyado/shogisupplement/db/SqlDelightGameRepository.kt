package dev.miyado.shogisupplement.db

import dev.miyado.shogisupplement.board.ShogiBoard
import dev.miyado.shogisupplement.board.ShogiMove
import dev.miyado.shogisupplement.kifu.KifuDecomposer
import dev.miyado.shogisupplement.kifu.KifuSource
import dev.miyado.shogisupplement.kifu.KifTreeParser
import dev.miyado.shogisupplement.pipeline.BlunderReport
import dev.miyado.shogisupplement.text.AppStrings
import dev.miyado.shogisupplement.util.currentEpochSeconds
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

/** 棋譜・悪手レポート・局面評価値のDB永続化リポジトリ（[GameRepository]のSQLDelight実装）。 */
class SqlDelightGameRepository(private val database: ShogiSupplementDatabase) : GameRepository {

    override fun getStudyKif(gameId: Long): String? =
        database.shogiSupplementQueries.getStudyKif(gameId).executeAsOneOrNull()?.effective_kif

    override fun saveStudyKif(gameId: Long, expectedKif: String, kif: String): Boolean =
        database.transactionWithResult {
            val current = getStudyKif(gameId) ?: return@transactionWithResult false
            if (current != expectedKif) return@transactionWithResult false
            val original = getGameById(gameId)?.kifText ?: return@transactionWithResult false
            val baseline = KifTreeParser().parse(original)
            val edited = KifTreeParser().parse(kif)
            require(baseline.mainLineContent() == edited.mainLineContent() && baseline.headers == edited.headers) {
                "Study edits must preserve the analyzed main line"
            }
            database.shogiSupplementQueries.updateStudyKif(kif, gameId)
            if (kif != current) {
                database.studySyncQueries.initializeStudySync(gameId, original)
                database.studySyncQueries.recordStudyChange(gameId)
            }
            true
        }

    override fun getPendingStudyUploads(): List<GameRepository.StudyUploadSnapshot> =
        database.studySyncQueries.getPendingStudyUploads().executeAsList().map {
            GameRepository.StudyUploadSnapshot(it.id, it.content_hash, checkNotNull(it.kif_text),
                checkNotNull(it.study_kif), it.base_kif, it.revision)
        }

    override fun acknowledgeStudyUpload(gameId: Long, revision: Long, uploadedKif: String) {
        database.studySyncQueries.acknowledgeStudyUpload(revision, uploadedKif, gameId, revision, revision)
    }

    @OptIn(kotlin.uuid.ExperimentalUuidApi::class)
    override fun getAnalysisUploadSnapshot(gameId: Long): GameRepository.AnalysisUploadSnapshot? =
        database.transactionWithResult {
            val game = getGameById(gameId) ?: return@transactionWithResult null
            database.analysisSyncGenerationQueries.initializeGeneration(gameId, kotlin.uuid.Uuid.random().toString())
            GameRepository.AnalysisUploadSnapshot(
                game = game,
                reports = getReports(gameId),
                problems = SqlDelightDrillRepository(database).getDrillCandidatesByGame(gameId),
                revision = checkNotNull(getAnalysisRevision(gameId)),
                studyRevision = database.studySyncQueries.getStudyRevision(gameId).executeAsOneOrNull() ?: 0,
                generation = database.analysisSyncGenerationQueries.getGeneration(gameId).executeAsOne(),
            )
        }

    override fun getAnalysisSyncTarget(gameId: Long, userId: String, generation: String): GameRepository.AnalysisSyncTarget? =
        database.analysisSyncTargetQueries.getTarget(gameId, userId, generation)
            .executeAsOneOrNull()?.let {
                GameRepository.AnalysisSyncTarget(it.expected_generation,
                    if (it.study_kif != null && it.study_revision != null) GameRepository.FrozenStudy(it.study_kif, it.study_revision) else null)
            }

    override fun freezeAnalysisSyncTarget(
        gameId: Long, userId: String, generation: String, expectedGeneration: String?,
        study: GameRepository.FrozenStudy?,
    ): GameRepository.AnalysisSyncTarget? = database.transactionWithResult {
        if (database.analysisSyncGenerationQueries.getGeneration(gameId).executeAsOneOrNull() != generation) {
            return@transactionWithResult null
        }
        database.analysisSyncTargetQueries.freezeTarget(gameId, userId, generation, expectedGeneration, study?.kif, study?.revision)
        getAnalysisSyncTarget(gameId, userId, generation)
    }

    override fun getAnalysisDeleteTarget(gameId: Long, userId: String, generation: String): GameRepository.AnalysisDeleteTarget? =
        database.analysisDeleteTargetQueries.getTarget(gameId, userId, generation).executeAsOneOrNull()?.let {
            GameRepository.AnalysisDeleteTarget(it.expected_generation, it.request_id)
        }

    override fun freezeAnalysisDeleteTarget(
        gameId: Long, userId: String, generation: String, target: GameRepository.AnalysisDeleteTarget,
    ): GameRepository.AnalysisDeleteTarget? = database.transactionWithResult {
        if (database.analysisSyncGenerationQueries.getGeneration(gameId).executeAsOneOrNull() != generation) return@transactionWithResult null
        database.analysisDeleteTargetQueries.freezeTarget(gameId, userId, generation, target.expectedGeneration, target.requestId)
        getAnalysisDeleteTarget(gameId, userId, generation)
    }

    override fun getAnalysisRemoteBase(gameId: Long, userId: String): GameRepository.AnalysisRemoteBase? =
        database.analysisRemoteBaseQueries.getBase(gameId, userId).executeAsOneOrNull()?.let {
            GameRepository.AnalysisRemoteBase(it.generation)
        }

    override fun confirmRestoredGame(gameId: Long, userId: String, generation: String?, epochSeconds: Long) {
        database.transaction {
            if (getGameById(gameId)?.analysisStatus != GameAnalysisStatus.PENDING) return@transaction
            database.analysisRemoteBaseQueries.saveBase(gameId, userId, generation)
            markRestoredPendingGameUploaded(gameId, epochSeconds)
        }
    }

    override fun acknowledgeAnalysisGeneration(gameId: Long, userId: String, generation: String, revision: Long, epochSeconds: Long) {
        database.transaction {
            if (database.analysisSyncGenerationQueries.getGeneration(gameId).executeAsOneOrNull() != generation || getAnalysisRevision(gameId) != revision) return@transaction
            database.analysisRemoteBaseQueries.saveBase(gameId, userId, generation)
            markAnalysisUploaded(gameId, revision, epochSeconds)
        }
    }

    override fun getAppliedAnalysis(contentHash: String, requestId: String): Long? =
        database.shogiSupplementQueries.getAppliedAnalysisRequest(contentHash, requestId).executeAsOneOrNull()

    /**
     * game・悪手・局面評価を同じSQLDelightトランザクションに含める。
     * saveAnalysis側のトランザクションはSQLDelightのネストしたトランザクションとして
     * 外側へ参加するため、局面評価の挿入失敗時も解析本体を残さない。
     */
    override fun saveAnalysisAtomically(request: GameRepository.AnalysisSaveRequest): Long =
        database.transactionWithResult {
            request.requestId?.let { requestId ->
                getAppliedAnalysis(request.contentHash, requestId)?.let { return@transactionWithResult it }
            }
            val gameId = saveAnalysis(
                fileName = request.fileName,
                contentHash = request.contentHash,
                moves = request.moves,
                headers = request.headers,
                reports = request.reports,
                rating = request.rating,
                ratingSampleMoves = request.ratingSampleMoves,
                coefVersion = request.coefVersion,
                analyzedAt = request.analyzedAt,
                kifText = request.kifText,
                userSide = request.userSide,
                ratingService = request.ratingService,
                ratingRaw = request.ratingRaw,
                ratingRule = request.ratingRule,
                ratingDeclaredAt = request.ratingDeclaredAt,
                sourcePlace = request.sourcePlace,
                gameWinner = request.gameWinner,
                endReason = request.endReason,
                openingStyle = request.openingStyle,
                openingCastle = request.openingCastle,
                openingTags = request.openingTags,
                senteRating = request.senteRating,
                goteRating = request.goteRating,
                timeControlRaw = request.timeControlRaw,
                timeControlByoyomiRaw = request.timeControlByoyomiRaw,
                engineMetaJson = request.engineMetaJson,
            )
            request.positionEvalRows.forEach { row ->
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
            request.requestId?.let {
                database.shogiSupplementQueries.insertAppliedAnalysisRequest(request.contentHash, it, gameId)
            }
            gameId
        }

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
        ratingDeclaredAt: Long?,
        sourcePlace: String?,
        gameWinner: String?,
        endReason: String?,
        senteRating: Long?,
        goteRating: Long?,
        timeControlRaw: String?,
        timeControlByoyomiRaw: String?,
        studyKif: String?,
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
            rating_declared_at = ratingDeclaredAt,
            source_place = sourcePlace,
            game_winner = gameWinner,
            end_reason = endReason,
            analysis_status = GameAnalysisStatus.PENDING.wireValue,
            opening_style = null,
            opening_castle = null,
            opening_tags = null,
            sente_rating = senteRating,
            gote_rating = goteRating,
            time_control_raw = timeControlRaw,
            time_control_byoyomi_raw = timeControlByoyomiRaw,
            engine_meta_json = null,
        )
        val gameId = database.shogiSupplementQueries.getLastInsertRowId().executeAsOne()
        if (studyKif != null) {
            check(saveStudyKif(gameId, kifText, studyKif)) { "Study restore failed" }
            val revision = database.studySyncQueries.getStudyRevision(gameId).executeAsOneOrNull() ?: 0
            acknowledgeStudyUpload(gameId, revision, studyKif)
        }
        gameId
    }

    @OptIn(kotlin.uuid.ExperimentalUuidApi::class)
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
        ratingDeclaredAt: Long?,
        sourcePlace: String?,
        gameWinner: String?,
        endReason: String?,
        openingStyle: String?,
        openingCastle: String?,
        openingTags: String?,
        senteRating: Long?,
        goteRating: Long?,
        timeControlRaw: String?,
        timeControlByoyomiRaw: String?,
        engineMetaJson: String?,
    ): Long {
        // 全局面の SFEN を事前計算: sfenAtPly[i] = i 手目を指す直前の局面
        val sfenAtPly = buildSfenSequence(moves)
        // USI手列をJSON配列として保存
        val movesUsiJson = Json.encodeToString(moves)

        return database.transactionWithResult {
            val existing = database.shogiSupplementQueries.getGameByHash(contentHash).executeAsOneOrNull()
            val replaceId = existing?.id
            if (replaceId == null) {
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
                    rating_declared_at = ratingDeclaredAt,
                    source_place = sourcePlace,
                    game_winner = gameWinner,
                    end_reason = endReason,
                    analysis_status = GameAnalysisStatus.COMPLETED.wireValue,
                    opening_style = openingStyle,
                    opening_castle = openingCastle,
                    opening_tags = openingTags,
                    sente_rating = senteRating,
                    gote_rating = goteRating,
                    time_control_raw = timeControlRaw,
                    time_control_byoyomi_raw = timeControlByoyomiRaw,
                    engine_meta_json = engineMetaJson,
                )
            } else {
                // 再解析は同じgameを正本として上書きする。先に派生結果とドリル履歴を
                // 消すことで、旧解析の問題・解答が新版結果へ混ざらないようにする。
                database.shogiSupplementQueries.deleteDrillAttemptsByGameId(replaceId)
                database.shogiSupplementQueries.deleteBlunderReportsByGameId(replaceId)
                database.shogiSupplementQueries.deletePositionEvalsByGameId(replaceId)
                database.shogiSupplementQueries.replaceAnalysisGame(
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
                    rating_declared_at = ratingDeclaredAt ?: existing.rating_declared_at,
                    source_place = sourcePlace,
                    game_winner = gameWinner,
                    end_reason = endReason,
                    opening_style = openingStyle,
                    opening_castle = openingCastle,
                    opening_tags = openingTags,
                    sente_rating = senteRating,
                    gote_rating = goteRating,
                    time_control_raw = timeControlRaw,
                    time_control_byoyomi_raw = timeControlByoyomiRaw,
                    engine_meta_json = engineMetaJson,
                    id = replaceId,
                )
            }
            val gameId = replaceId ?: database.shogiSupplementQueries.getLastInsertRowId().executeAsOne()
            database.analysisSyncGenerationQueries.replaceGeneration(gameId, kotlin.uuid.Uuid.random().toString())

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
                rating_declared_at = null,
                source_place = null,
                game_winner = null,
                end_reason = null,
                analysis_status = GameAnalysisStatus.COMPLETED.wireValue,
                opening_style = null,
                opening_castle = null,
                opening_tags = null,
                sente_rating = null,
                gote_rating = null,
                time_control_raw = null,
                time_control_byoyomi_raw = null,
                engine_meta_json = null,
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

    override fun getRecentGames(limit: Int): List<GameRecord> {
        if (limit <= 0) return emptyList()
        return database.shogiSupplementQueries
            .getRecentGames(limit.toLong())
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
        return database.shogiSupplementQueries
            .getGamesWithUserSide()
            .executeAsList()
            .map { it.toGameRecord() }
    }

    /** アップロード成功時刻を記録する（Unix epoch 秒）。 */
    override fun updateUploadedAt(gameId: Long, epochSeconds: Long) {
        database.shogiSupplementQueries.updateUploadedAt(epochSeconds, gameId)
    }

    override fun markRestoredPendingGameUploaded(gameId: Long, epochSeconds: Long) {
        database.shogiSupplementQueries.markRestoredPendingGameUploaded(epochSeconds, gameId)
    }

    override fun getAnalysisRevision(gameId: Long): Long? =
        database.shogiSupplementQueries.getAnalysisRevision(gameId).executeAsOneOrNull()

    override fun markAnalysisUploaded(gameId: Long, revision: Long, epochSeconds: Long) {
        database.shogiSupplementQueries.markAnalysisUploaded(epochSeconds, gameId, revision)
    }

    /** ゲームの user_side / rating_service / rating_raw を更新する。 */
    override fun updateUserSide(gameId: Long, userSide: String?, ratingService: String?, ratingRaw: Long?) {
        database.shogiSupplementQueries.updateUserSide(userSide, ratingService, ratingRaw, gameId)
    }

    override fun updateGamePlayers(gameId: Long, senteName: String?, goteName: String?) {
        database.shogiSupplementQueries.updateGamePlayers(senteName, goteName, gameId)
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

    override fun getBlunderCounts(): Map<Long, Int> {
        return database.shogiSupplementQueries
            .getBlunderCountAll()
            .executeAsList()
            .associate { it.game_id to it.blunder_count.toInt() }
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
            database.analysisSyncGenerationQueries.deleteGeneration(gameId)
            database.analysisSyncTargetQueries.deleteTargets(gameId)
            database.analysisDeleteTargetQueries.deleteTargets(gameId)
            database.analysisRemoteBaseQueries.deleteBases(gameId)
            database.shogiSupplementQueries.deleteAppliedAnalysisRequestsByGame(gameId)
            database.shogiSupplementQueries.deleteDrillAttemptsByGameId(gameId)
            database.shogiSupplementQueries.deleteBlunderReportsByGameId(gameId)
            database.shogiSupplementQueries.deletePositionEvalsByGameId(gameId)
            database.shogiSupplementQueries.deleteGameById(gameId)
        }
    }

    override fun deleteAllLocalData() {
        database.transaction {
            database.analysisSyncGenerationQueries.deleteAllGenerations()
            database.analysisSyncTargetQueries.deleteAllTargets()
            database.analysisDeleteTargetQueries.deleteAllTargets()
            database.analysisRemoteBaseQueries.deleteAllBases()
            database.shogiSupplementQueries.deleteAllAppliedAnalysisRequests()
            database.shogiSupplementQueries.deleteAllBlunderReports()
            database.shogiSupplementQueries.deleteAllPositionEvals()
            database.shogiSupplementQueries.deleteAllDrillAttempts()
            database.shogiSupplementQueries.deleteAllGames()
            database.shogiSupplementQueries.deleteAllServiceRanks()
            database.shogiSupplementQueries.deleteAllServiceAccounts()
            database.shogiSupplementQueries.deleteAllRatingDeclarationHistory()
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
    ratingDeclaredAt = rating_declared_at,
    sourcePlace = normalizeLegacySourcePlace(source_place),
    gameWinner = game_winner,
    endReason = end_reason,
    analysisStatus = GameAnalysisStatus.fromWireValue(analysis_status),
    openingStyle = opening_style,
    openingCastle = opening_castle,
    openingTags = opening_tags,
    senteRating = sente_rating,
    goteRating = gote_rating,
    timeControlRaw = time_control_raw,
    timeControlByoyomiRaw = time_control_byoyomi_raw,
    engineMetaJson = engine_meta_json,
    studyKif = study_kif,
)

/** user_sideがNULLでないことをSQL条件に含むクエリの生成型は専用型になるため、同じドメイン変換を明示する。 */
internal fun GetGamesWithUserSide.toGameRecord() = GameRecord(
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
    ratingDeclaredAt = rating_declared_at,
    sourcePlace = normalizeLegacySourcePlace(source_place),
    gameWinner = game_winner,
    endReason = end_reason,
    analysisStatus = GameAnalysisStatus.fromWireValue(analysis_status),
    openingStyle = opening_style,
    openingCastle = opening_castle,
    openingTags = opening_tags,
    senteRating = sente_rating,
    goteRating = gote_rating,
    timeControlRaw = time_control_raw,
    timeControlByoyomiRaw = time_control_byoyomi_raw,
    engineMetaJson = engine_meta_json,
    studyKif = study_kif,
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
