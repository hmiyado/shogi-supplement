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
 * - [analyzer]: 局面解析の実行者。端末解析（[AnalysisRunner]）とサーバー解析
 *   （[RemoteAnalysisRunner]）のどちらを渡しても以降の処理は変わらない。
 * - [repository]: [GameRepository]（重複チェック・過去局集計・保存）
 * - [coefTable]: 係数表（判定ロジックの不変条件はこのオーケストレータでは一切変更しない）
 * - [onProgress]: (done, total) の進捗コールバック
 *
 * 判定ロジック・係数表・解析条件（go nodes 400000 / Threads=1 / MultiPV=2 / FV_SCALE=20）は
 * 一切変更しない。それらは [GameAnalyzer] 実装と [ReportPipeline] にすでに実装済みのものを
 * そのまま使う。
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
         * 解析失敗。
         * @param message 表示用メッセージ（日本語）。[RemoteAnalysisException] 由来は
         *   [RemoteAnalysisErrorMapper] でローカライズ済み、それ以外は例外の生メッセージ。
         * @param reason 失敗理由の型。既定値 [AnalysisFailureReason.Unknown] のため、reason を
         *   参照しない既存呼び出し側（Android の AnalysisService・デバッグレシーバ）は
         *   message だけを見続けても壊れない。
         */
        data class Failed(
            val message: String,
            val reason: AnalysisFailureReason = AnalysisFailureReason.Unknown,
        ) : Outcome()
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

            val allPv = analyzer.analyzeGame(game.moves, onProgress)

            // PvInfo → PositionEval 変換（MultiPV=2 で解析済みのため pv2 も保持する。
            // ドリルの一次判定＝pv1/pv2 圏内かどうかの端末内判定に使う）
            val evals = allPv.map { pvList ->
                val pv1 = pvList.firstOrNull { it.multipv == 1 }
                val pv2 = pvList.firstOrNull { it.multipv == 2 }
                PositionEval(
                    score = pv1?.score,
                    pv = pv1?.pv ?: emptyList(),
                    pv2Score = pv2?.score,
                    pv2MoveUsi = pv2?.pv?.firstOrNull(),
                )
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
                // 生の「場所」ヘッダはローカルDBにも残さない（lishogiでは対局を一意特定できる
                // URLが入るため）。判定は KifuDecomposer.classifySource に一本化し、
                // アップロード用の分解処理と同じ結果になるようにする。
                sourcePlace = KifuDecomposer.classifySource(kifContent, game.headers["場所"]).wireValue,
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
            // RemoteAnalysisException はサーバーが理由を明示して返した想定内の失敗
            // （401/403/429/400=クライアント起因、EngineFailure=サーバー側で記録済み、
            // ConnectionLost=ネットワーク事情）のため、二重報告を避けてcaptureExceptionしない。
            // それ以外の例外（KIFパース失敗・DB保存失敗・端末エンジン内部エラー等）は従来どおり送信する。
            if (e !is RemoteAnalysisException && !e.isAlreadyReported()) {
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
