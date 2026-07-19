package dev.miyado.shogisupplement.pipeline

import dev.miyado.shogisupplement.blunder.BlunderJudge
import dev.miyado.shogisupplement.board.ShogiBoard
import dev.miyado.shogisupplement.board.ShogiMove
import dev.miyado.shogisupplement.classify.BlunderClassifier
import dev.miyado.shogisupplement.classify.ClassificationResult
import dev.miyado.shogisupplement.judge.CoefficientTable
import dev.miyado.shogisupplement.judge.Judge
import dev.miyado.shogisupplement.judge.JudgeInput
import dev.miyado.shogisupplement.strength.StrengthEstimate
import dev.miyado.shogisupplement.strength.StrengthEstimator

/**
 * 局面ごとのエンジン評価 → 悪手抽出 → 強さ推定 → 相応判定 のパイプライン。
 *
 * エンジン実行そのものは含まない。入力はエンジン出力データ構造（AnalysisRunner が供給）。
 *
 * 2パス構造:
 *   第1パス: 悪手抽出・分類（BlunderJudge / BlunderClassifier）
 *   第2パス: 強さ推定 → 帯決定 → 相応判定（StrengthEstimator / Judge）
 * 申告レートは帯決定に使わない。過去の累計手数・悪手数を使って実測悪手率を算出する。
 */
object ReportPipeline {

    /**
     * パイプラインの解析結果。
     *
     * @param reports 悪手ごとのレポートリスト（元の手順順）
     * @param strengthEstimate 今局＋過去実績から推定した強さ指標
     * @param thisGameMoves    今局の自分の手数（sides に含まれる側の合計）
     * @param thisGameBlunders 今局の自分の悪手数
     */
    data class AnalysisResult(
        val reports: List<BlunderReport>,
        val strengthEstimate: StrengthEstimate,
        val thisGameMoves: Int,
        val thisGameBlunders: Int,
    ) {
        /** 推定値の lishogi 相当レート（game.rating 保存用）。 */
        val estimatedRating: Int get() = strengthEstimate.rating

        /** 推定に使った集計対象手数（game.rating_sample_moves 保存用）。 */
        val ratingSampleMoves: Int get() = strengthEstimate.totalMoves
    }

    /**
     * 棋譜の悪手一覧（強さ推定に基づく相応判定付き）を返す。
     *
     * @param moves USI 手列（KIF パーサ出力そのもの）
     * @param evals 各局面のエンジン評価（サイズ = moves.size + 1。evals[t] が moves[t] 指す前の評価）
     * @param sides  解析対象 ("sente" / "gote" / 両方)
     * @param coef   係数表
     * @param prevTotalMoves    過去の累計自分の手数（今局を除く）
     * @param prevTotalBlunders 過去の累計自分の悪手数（今局を除く）
     * @return [AnalysisResult]（悪手リスト＋強さ推定）
     */
    fun analyze(
        moves: List<String>,
        evals: List<PositionEval>,
        sides: Set<String> = setOf("sente", "gote"),
        coef: CoefficientTable,
        prevTotalMoves: Int = 0,
        prevTotalBlunders: Int = 0,
    ): AnalysisResult {
        require(evals.size == moves.size + 1) {
            "evals.size (${evals.size}) must equal moves.size + 1 (${moves.size + 1})"
        }

        // ── 第1パス: 悪手抽出・分類 ─────────────────────────────────────────
        val board = ShogiBoard()
        val intermediate = mutableListOf<IntermediateBlunder>()
        var thisGameMoves = 0

        for (t in moves.indices) {
            val side = if (t % 2 == 0) "sente" else "gote"
            val usiStr = moves[t]
            val cur = evals[t]
            val nxt = evals[t + 1]

            val curScore = cur.score
            val nxtScore = nxt.score

            if (side !in sides || curScore == null || nxtScore == null) {
                board.push(ShogiMove.fromUsi(usiStr))
                continue
            }

            thisGameMoves++

            val blunderVerdict = BlunderJudge.judge(curScore, nxtScore)
            if (!blunderVerdict.isBlunder) {
                board.push(ShogiMove.fromUsi(usiStr))
                continue
            }

            val move = ShogiMove.fromUsi(usiStr)
            val classification = BlunderClassifier.classify(board, move, cur, nxt)

            val cpBefore = BlunderJudge.toCp(curScore)
            val cpAfter = BlunderJudge.toCp(nxtScore)
            val lossWp = BlunderJudge.winProb(cpBefore) - BlunderJudge.winProb(-cpAfter)

            intermediate.add(
                IntermediateBlunder(
                    ply = t + 1,
                    side = side,
                    moveUsi = usiStr,
                    bestUsi = cur.pv.firstOrNull(),
                    lossWp = lossWp,
                    classification = classification,
                    bestPv = cur.pv.takeIf { it.isNotEmpty() }?.joinToString(" "),
                    punishPv = nxt.pv.takeIf { it.isNotEmpty() }?.joinToString(" "),
                    judgeInput = JudgeInput(
                        category = classification.category,
                        missedMateIn = classification.missedMateIn,
                    ),
                    cpBefore = cpBefore,
                    cpAfter = cpAfter,
                )
            )
        }

        val thisGameBlunders = intermediate.size

        // ── 第2パス: 強さ推定 → 帯決定 → 相応判定 ──────────────────────────
        val totalMoves = prevTotalMoves + thisGameMoves
        val totalBlunders = prevTotalBlunders + thisGameBlunders
        val observedRate = if (totalMoves > 0) {
            totalBlunders * 1000.0 / totalMoves
        } else {
            // 手数がゼロ（初回・全手スコアなし）は中央帯の悪手率にフォールバック
            coef.rates_per_1000_moves["1600-1899"]?.values?.sum() ?: 61.9
        }
        val strengthEstimate = StrengthEstimator.estimate(observedRate, totalMoves, coef)
        val (bandIdx, _) = coef.bandOf(strengthEstimate.rating)

        val reports = intermediate.map { b ->
            BlunderReport(
                ply = b.ply,
                side = b.side,
                moveUsi = b.moveUsi,
                bestUsi = b.bestUsi,
                lossWp = b.lossWp,
                classification = b.classification,
                judgement = Judge.judge(b.judgeInput, bandIdx, coef),
                bestPv = b.bestPv,
                punishPv = b.punishPv,
                cpBefore = b.cpBefore,
                cpAfter = b.cpAfter,
            )
        }

        return AnalysisResult(
            reports = reports,
            strengthEstimate = strengthEstimate,
            thisGameMoves = thisGameMoves,
            thisGameBlunders = thisGameBlunders,
        )
    }

    /** 第1パスの中間データ。 */
    private data class IntermediateBlunder(
        val ply: Int,
        val side: String,
        val moveUsi: String,
        val bestUsi: String?,
        val lossWp: Double,
        val classification: ClassificationResult,
        val bestPv: String?,
        val punishPv: String?,
        val judgeInput: JudgeInput,
        /** 悪手前局面の評価値（手番側視点 cp）。BlunderReport.cpBefore 参照。 */
        val cpBefore: Int,
        /** 悪手後局面の評価値（次手番側視点 cp）。BlunderReport.cpAfter 参照。 */
        val cpAfter: Int,
    )
}
