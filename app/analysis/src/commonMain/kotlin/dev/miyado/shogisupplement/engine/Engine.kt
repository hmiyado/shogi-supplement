package dev.miyado.shogisupplement.engine

import dev.miyado.shogisupplement.blunder.Score

/**
 * USI infoの1本のPV。
 *
 * @property multipv MultiPV番号（1始まり）
 * @property score 手番側視点のスコア
 * @property pv 読み筋（USI手列）
 * @property nodes 探索ノード数
 */
data class PvInfo(
    val multipv: Int,
    val score: Score,
    val pv: List<String>,
    val nodes: Long,
)

/** USIエンジンの抽象化。解析条件はnodes固定、Threads=1、MultiPV=2、FV_SCALE=20とする。 */
interface Engine {
    /** 初期局面から手を進めた局面を固定ノードで解析する。 @param moves USI手列。 @param nodes 探索ノード数。 @return MultiPV分のPV。 */
    fun analyze(moves: List<String>, nodes: Int = DEFAULT_NODES): List<PvInfo>

    /**
     * SFEN局面を固定ノードで解析する。 @param sfen SFEN文字列。 @param additionalMoves 追加のUSI手列。
     * @param nodes 探索ノード数。 @param multiPv 候補手の本数（[MULTI_PV] 以外はgoldenパリティの
     * 対象外である検討モードだけが渡してよい）。 @return multiPv 分のPV（出せた本数まで）。
     */
    fun analyzeSfen(
        sfen: String,
        additionalMoves: List<String> = emptyList(),
        nodes: Int = DEFAULT_NODES,
        multiPv: Int = MULTI_PV,
    ): List<PvInfo>

    /** エンジンプロセス/インスタンスの終了。 */
    fun quit()

    /** 局の区切りとしてUSI "usinewgame"を送る。iOSの常駐エンジンではisready後に送る。 */
    fun newGame()

    companion object {
        const val DEFAULT_NODES = 400_000
        const val MULTI_PV = 2

        /**
         * 検討モードの候補手の本数。[MULTI_PV] と違い解析結果の保存には関わらないため、
         * goldenパリティの不変条件（[EngineInvariants]）には含めない。
         */
        const val STUDY_MULTI_PV = 3
    }
}
