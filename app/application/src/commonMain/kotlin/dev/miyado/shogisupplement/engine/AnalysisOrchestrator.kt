package dev.miyado.shogisupplement.engine

import dev.miyado.shogisupplement.blunder.BlunderJudge
import dev.miyado.shogisupplement.blunder.Score
import dev.miyado.shogisupplement.crash.CrashReporter
import dev.miyado.shogisupplement.crash.NoopCrashReporter
import dev.miyado.shogisupplement.crash.isAlreadyReported
import dev.miyado.shogisupplement.db.GameRepository
import dev.miyado.shogisupplement.db.PositionEvalRow
import dev.miyado.shogisupplement.judge.CoefficientTable
import dev.miyado.shogisupplement.kifu.KifParser
import dev.miyado.shogisupplement.kifu.KifuDecomposer
import dev.miyado.shogisupplement.kifu.KifuParseException
import dev.miyado.shogisupplement.pipeline.ReportPipeline
import dev.miyado.shogisupplement.pipeline.toPositionEval
import dev.miyado.shogisupplement.text.AppStrings
import dev.miyado.shogisupplement.util.sha256Hex

/**
 * 判定ロジック・係数表・解析条件（go nodes 400000 / Threads=1 / MultiPV=2 / FV_SCALE=20）は
 * [GameAnalyzer] と [ReportPipeline] が保持し、この層では変更しない。
 */
class AnalysisOrchestrator(
    private val repository: GameRepository,
    private val coefTable: CoefficientTable,
    private val analyzer: GameAnalyzer,
    private val crashReporter: CrashReporter = NoopCrashReporter,
) {

    /** [analyzeAndSave] の結果。 */
    sealed class Outcome {
        /**
         * 解析完了（または既存game_idが見つかったため再解析をスキップ）。
         * @param alreadyExisted true = content_hash が既存レコードと一致し、再解析せずそのgame_idを返した
         */
        data class Completed(val gameId: Long, val alreadyExisted: Boolean) : Outcome()

        /**
         * @param message 表示用メッセージ（日本語）。[RemoteAnalysisException] 由来は
         *   [RemoteAnalysisErrorMapper] でローカライズ済み、それ以外は例外の生メッセージ。
         * @param reason 失敗理由。省略時は [AnalysisFailureReason.Unknown]。
         */
        data class Failed(
            val message: String,
            val reason: AnalysisFailureReason = AnalysisFailureReason.Unknown,
        ) : Outcome()
    }

    /**
     * @param contentHash nullならKIFから算出。非nullは再構成で原本と書式が変わる場合の原本ハッシュ。
     * @param sourcePlaceOverride nullならKIFから判定。非nullは再構成で失われた出典の正規化済み値。
     * @param onPositionResult 局面ごとの中間結果。既定のno-opでは全局面完了後のみ評価・保存する。
     */
    suspend fun analyzeAndSave(
        kifContent: String,
        fileName: String,
        userSide: String? = null,
        ratingService: String? = null,
        ratingRaw: Long? = null,
        ratingRule: String? = null,
        contentHash: String? = null,
        sourcePlaceOverride: String? = null,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
        onPositionResult: (ply: Int, pvs: List<PvInfo>) -> Unit = { _, _ -> },
    ): Outcome {
        return try {
            val effectiveContentHash = contentHash ?: sha256Hex(kifContent)

            val existingId = repository.getByHash(effectiveContentHash)
            val existing = existingId?.let(repository::getGameById)
            if (existingId != null && existing?.analysisStatus == dev.miyado.shogisupplement.db.GameAnalysisStatus.COMPLETED) {
                return Outcome.Completed(existingId, alreadyExisted = true)
            }

            val game = KifParser().parse(kifContent)

            val allPv = analyzer.analyzeGame(
                moves = game.moves,
                onPositionResult = onPositionResult,
                onProgress = onProgress,
            )

            // 再解析なしで第2候補まで判定できるよう、MultiPV=2の結果を保持する。
            val evals = allPv.map { pvList -> pvList.toPositionEval() }

            val sides = if (userSide != null) setOf(userSide) else setOf("sente", "gote")
            val analysisResult = ReportPipeline.analyze(
                moves = game.moves,
                evals = evals,
                sides = sides,
                coef = coefTable,
            )

            val gameId = repository.saveAnalysis(
                fileName = fileName,
                contentHash = effectiveContentHash,
                moves = game.moves,
                headers = game.headers,
                reports = analysisResult.reports,
                rating = analysisResult.estimatedRating,
                ratingSampleMoves = analysisResult.ratingSampleMoves,
                coefVersion = coefTable.version,
                kifText = kifContent,
                userSide = userSide,
                ratingService = ratingService,
                ratingRaw = ratingRaw,
                ratingRule = ratingRule,
                // 「場所」には対局識別URLが入り得るため保存せず、正規化した出典だけを残す。
                sourcePlace = sourcePlaceOverride
                    ?: KifuDecomposer.classifySource(kifContent, game.headers["場所"]).wireValue,
                gameWinner = game.winner,
                endReason = game.endReason,
            )

            // 評価値はsente視点に正規化し、後からの計算に必要な第2候補も保存する。
            val positionEvalRows = evals.mapIndexedNotNull { t, posEval ->
                val score = posEval.score ?: return@mapIndexedNotNull null
                val flip = t % 2 == 1
                val bestUsi = posEval.pv.firstOrNull()
                val (secondScoreCp, secondMateIn) = when (val pv2Score = posEval.pv2Score) {
                    null -> null to null
                    is Score.Cp -> BlunderJudge.toCp(pv2Score).let { if (flip) -it else it } to null
                    is Score.Mate -> null to pv2Score.plies.let { if (flip) -it else it }
                }
                when (score) {
                    is Score.Cp -> PositionEvalRow(
                        ply = t,
                        scoreCp = BlunderJudge.toCp(score).let { if (flip) -it else it },
                        mateIn = null,
                        bestUsi = bestUsi,
                        secondScoreCp = secondScoreCp,
                        secondMateIn = secondMateIn,
                        secondUsi = posEval.pv2MoveUsi,
                    )
                    is Score.Mate -> PositionEvalRow(
                        ply = t,
                        scoreCp = null,
                        mateIn = score.plies.let { if (flip) -it else it },
                        bestUsi = bestUsi,
                        secondScoreCp = secondScoreCp,
                        secondMateIn = secondMateIn,
                        secondUsi = posEval.pv2MoveUsi,
                    )
                }
            }
            repository.savePositionEvals(gameId, positionEvalRows)

            Outcome.Completed(gameId, alreadyExisted = false)
        } catch (e: kotlinx.coroutines.CancellationException) {
            // キャンセルは失敗結果へ変換せず、構造化並行性を保つため必ず伝播させる。
            throw e
        } catch (e: Exception) {
            // 想定内のリモート失敗は二重報告しない。KIF解析失敗は棋譜断片を含み得るため送信しない。
            // その他の未報告例外だけをクラッシュレポートへ送る。
            val expected = e is RemoteAnalysisException || e is KifuParseException
            if (!expected && !e.isAlreadyReported()) {
                crashReporter.captureException(e)
            }
            val message = if (e is RemoteAnalysisException) {
                RemoteAnalysisErrorMapper.map(e)
            } else {
                e.message ?: AppStrings.UNKNOWN_ERROR
            }
            Outcome.Failed(message, AnalysisFailureReason.from(e))
        }
    }
}
