package dev.miyado.shogisupplement.pipeline

import dev.miyado.shogisupplement.blunder.BlunderJudge
import dev.miyado.shogisupplement.engine.PvInfo

/**
 * プログレッシブ解析表示のアキュムレータ（純粋・プラットフォーム非依存）。
 *
 * 局面ごとの解析結果を任意の順序・任意の粒度（1局面ずつ／全局面まとめて）で受け取り、
 * in-order watermark方式で「先頭（ply=0）から連続して確定した区間」だけを公開する。
 * 解析の実行順・並列度・到着粒度に依存しない設計のため、順不同着で局面が届く実装からも
 * ストリーム終端で全局面が一括到着する実装からも、同じ [withPosition] 呼び出しだけで駆動できる。
 *
 * @param moves 棋譜のUSI手列（KIFパース直後、エンジン解析の開始前から確定している）
 * @param total 局面数（= moves.size + 1。0手目=初期局面を含む）
 * @param confirmedThrough watermark。[0, confirmedThrough) 番目の局面が反映済み
 * @param confirmed 到着済み局面（ply → 変換済み [PositionEval]）。反映済み区間の外の
 *   バッファ分も含む（watermarkが追いつくまで保持する）
 */
data class ProgressiveReportState(
    val moves: List<String>,
    val total: Int = moves.size + 1,
    val confirmedThrough: Int = 0,
    private val confirmed: Map<Int, PositionEval> = emptyMap(),
) {
    /**
     * 到着済み局面数（out-of-orderでも受信済みなら数える）。
     * [confirmedThrough]（連続確定区間のwatermark）とは別軸として意図的に分けている——
     * 順不同着では両者は乖離しうる（例: 末尾のplyだけ先着すると doneCount=1 だが
     * confirmedThrough=0 のまま）。
     */
    val doneCount: Int get() = confirmed.size

    /** 全局面が確定済みか。 */
    val isComplete: Boolean get() = confirmedThrough >= total

    /**
     * 局面 [ply] の解析結果を1件反映する。純粋関数（副作用なし・新しいインスタンスを返す）。
     *
     * 範囲外の ply、または既知の ply の再通知は無視する（再送・再解析で同じ ply が
     * 複数回届いても watermark が壊れないようにするため）。
     */
    fun withPosition(ply: Int, pvs: List<PvInfo>): ProgressiveReportState {
        if (ply !in 0 until total || confirmed.containsKey(ply)) return this
        val next = confirmed + (ply to pvs.toPositionEval())
        var watermark = confirmedThrough
        while (watermark < total && next.containsKey(watermark)) watermark++
        return copy(confirmed = next, confirmedThrough = watermark)
    }

    /** 反映済み区間（[0, confirmedThrough)）の評価。 */
    val revealedEvals: List<PositionEval>
        get() = (0 until confirmedThrough).map { confirmed.getValue(it) }

    /**
     * 反映済み区間内で悪手と判定された指し手のply（1-indexed。moves[ply-1]が悪手）。
     *
     * バンド別の相応判定（強さ推定=今局全体の特徴量が必要）は行わない。ここでの判定は
     * 隣接する2局面だけで決まる生の悪手検出（[BlunderJudge.judge]）に留める——band確定を
     * 待たずに反映済み区間だけから計算できることが、このアキュムレータをwatermarkの
     * 進行と独立に保つ前提のため。
     */
    val revealedBlunderPlies: Set<Int>
        get() = buildSet {
            for (ply in 1 until confirmedThrough) {
                val cur = confirmed.getValue(ply - 1)
                val nxt = confirmed.getValue(ply)
                val curScore = cur.score ?: continue
                val nxtScore = nxt.score ?: continue
                val moveUsi = moves.getOrNull(ply - 1) ?: continue
                val verdict = BlunderJudge.judge(curScore, nxtScore, moveUsi, cur.pv.firstOrNull())
                if (verdict.isBlunder) add(ply)
            }
        }

    companion object {
        /** 解析開始直後の初期状態（何も反映されていない）。 */
        fun initial(moves: List<String>): ProgressiveReportState = ProgressiveReportState(moves = moves)
    }
}
