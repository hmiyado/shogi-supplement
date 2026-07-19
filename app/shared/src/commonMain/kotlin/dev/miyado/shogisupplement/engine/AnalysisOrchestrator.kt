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
import dev.miyado.shogisupplement.pipeline.PositionEval
import dev.miyado.shogisupplement.pipeline.ReportPipeline
import dev.miyado.shogisupplement.text.AppStrings
import dev.miyado.shogisupplement.util.sha256Hex

/**
 * 「1局のKIFを解析してDBに保存するまで」を共通化したオーケストレータ。
 *
 * KIFパース→エンジン解析→悪手判定→強さ推定→DB保存のコア部分を担う。
 * URI読み込み・フォアグラウンド通知・ファイル名解決・自動アップロードなどの
 * Android専用処理は含まない。iOS（クリップボード取込）・Android（AnalysisService）の
 * 両方から使う。
 *
 * 注入界面:
 * - [engineFactory] / [disposeEngine]: 局ごとにエンジンを取得・解放するファクトリ形式。
 *   - Android: engineFactory = 毎局 [UsiEngineProcess] を新規プロセスとして起動する
 *     （[dev.miyado.shogisupplement.engine.createAndroidAnalysisRunner] 参照）。
 *     disposeEngine = 既定値の `{ it.quit() }`（毎局プロセスを終了する既存挙動を保存）。
 *   - iOS: engineFactory = 常駐1インスタンス（[dev.miyado.shogisupplement.engine.IosEngineHost]）を
 *     返しつつ [Engine.newGame] で局の区切りをつける。disposeEngine = no-op（quitしない。
 *     `UsiEngineInProcess` はプロセス内で一度しか起動できないため）。
 * - [repository]: [GameRepository]（重複チェック・過去局集計・保存）
 * - [coefTable]: 係数表（判定ロジックの不変条件はこのオーケストレータでは一切変更しない）
 * - [workers]: 並列エンジン数（Android=4・iOS=1が既定運用）
 * - [onProgress]: (done, total) の進捗コールバック
 *
 * 判定ロジック・係数表・解析条件（go nodes 400000 / Threads=1 / MultiPV=2 / FV_SCALE=20）は
 * 一切変更しない。それらは [Engine] 実装（[UsiEngineProcess] / [UsiEngineInProcess]）と
 * [ReportPipeline] にすでに実装済みのものをそのまま使う。
 */
class AnalysisOrchestrator(
    private val repository: GameRepository,
    private val coefTable: CoefficientTable,
    private val workers: Int,
    private val engineFactory: () -> Engine,
    private val disposeEngine: (Engine) -> Unit = { it.quit() },
    private val crashReporter: CrashReporter = NoopCrashReporter,
) {

    /** [analyzeAndSave] の結果。 */
    sealed class Outcome {
        /**
         * 解析完了（または既存game_idが見つかったため再解析をスキップ）。
         * @param alreadyExisted true = content_hash が既存レコードと一致し、再解析せずそのgame_idを返した
         */
        data class Completed(val gameId: Long, val alreadyExisted: Boolean) : Outcome()

        /** 解析失敗。 */
        data class Failed(val message: String) : Outcome()
    }

    /**
     * KIFテキストを解析し、結果をDBへ保存する。
     *
     * @param kifContent KIF原文
     * @param fileName 表示用ファイル名
     * @param userSide ユーザーの側（"sente"/"gote"/null=両側を対象に解析）
     * @param ratingService レートのサービス名（申告のみ・相応判定には使わない）
     * @param ratingRaw サービス上のraw値
     * @param ratingRule ルール文字列
     * @param onProgress (done, total) の進捗コールバック
     */
    suspend fun analyzeAndSave(
        kifContent: String,
        fileName: String,
        userSide: String? = null,
        ratingService: String? = null,
        ratingRaw: Long? = null,
        ratingRule: String? = null,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
    ): Outcome {
        return try {
            val contentHash = sha256Hex(kifContent)

            // 重複チェック
            val existingId = repository.getByHash(contentHash)
            if (existingId != null) {
                return Outcome.Completed(existingId, alreadyExisted = true)
            }

            // KIFパース
            val game = KifParser().parse(kifContent)

            // エンジン解析（局ごとにエンジンを取得・解放するファクトリ形式。挙動不変条件は
            // AnalysisRunner/Engine実装側で保証される）
            val runner = AnalysisRunner(
                workers = workers,
                crashReporter = crashReporter,
                engineFactory = engineFactory,
                disposeEngine = disposeEngine,
            )
            val allPv = runner.analyzeGame(game.moves, onProgress)

            // PvInfo → PositionEval 変換
            val evals = allPv.map { pvList ->
                val pv1 = pvList.firstOrNull { it.multipv == 1 }
                PositionEval(score = pv1?.score, pv = pv1?.pv ?: emptyList())
            }

            // 過去局の累計手数・悪手数を取得（userSide が設定されている局のみ）
            val prevTotalMoves = if (userSide != null) repository.getPrevTotalMoves() else 0
            val prevTotalBlunders = if (userSide != null) repository.getPrevTotalBlunders() else 0

            // 悪手レポート生成（2パス: 悪手抽出 → 強さ推定 → 相応判定）
            val sides = if (userSide != null) setOf(userSide) else setOf("sente", "gote")
            val analysisResult = ReportPipeline.analyze(
                moves = game.moves,
                evals = evals,
                sides = sides,
                coef = coefTable,
                prevTotalMoves = prevTotalMoves,
                prevTotalBlunders = prevTotalBlunders,
            )

            // DB保存（kif_text + moves_usi も保存、game.rating は推定値）
            val gameId = repository.saveAnalysis(
                fileName = fileName,
                contentHash = contentHash,
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
                sourcePlace = game.headers["場所"],
                gameWinner = game.winner,
                endReason = game.endReason,
            )

            // 全局面の評価値を sente 視点に正規化して保存
            // t=0: 先手番（評価値そのまま）、t=1: 後手番（評価値を反転）
            val positionEvalRows = evals.mapIndexedNotNull { t, posEval ->
                val score = posEval.score ?: return@mapIndexedNotNull null
                val flip = t % 2 == 1 // 後手番なら反転
                when (score) {
                    is Score.Cp -> PositionEvalRow(
                        ply = t,
                        scoreCp = BlunderJudge.toCp(score).let { if (flip) -it else it },
                        mateIn = null,
                    )
                    is Score.Mate -> PositionEvalRow(
                        ply = t,
                        scoreCp = null,
                        mateIn = score.plies.let { if (flip) -it else it },
                    )
                }
            }
            repository.savePositionEvals(gameId, positionEvalRows)

            Outcome.Completed(gameId, alreadyExisted = false)
        } catch (e: Exception) {
            if (!e.isAlreadyReported()) {
                crashReporter.captureException(e)
            }
            Outcome.Failed(e.message ?: AppStrings.UNKNOWN_ERROR)
        }
    }
}
