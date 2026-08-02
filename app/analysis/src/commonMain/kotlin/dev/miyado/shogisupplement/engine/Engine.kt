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

/**
 * USIエンジンブリッジの抽象化。
 *
 * 実装はプラットフォーム側:
 * - Android: nativeLibraryDir の libyaneuraou_usi.so を別プロセスexec
 * - iOS: プロセス内で動作させるエンジン（in-process）
 *
 * 解析条件の不変条件: nodes固定・Threads=1・MultiPV=2・FV_SCALE=20 を実装側で守ること。
 */
interface Engine {
    /**
     * 平手初期局面から [moves] を進めた局面を固定ノードで解析する。
     *
     * @param moves USI手列（空なら初期局面）
     * @param nodes 探索ノード数（既定400_000。係数表との整合のため変更禁止）
     * @return MultiPV分のPV（multipv昇順）
     */
    fun analyze(moves: List<String>, nodes: Int = DEFAULT_NODES): List<PvInfo>

    /**
     * SFEN 文字列で指定した局面（オプションで追加手を進めた後）を固定ノードで解析する。
     * ドリル判定のように任意局面を出発点とする解析に使う。
     *
     * @param sfen SFEN 文字列
     * @param additionalMoves SFEN 後にさらに進める USI 手列（省略可）
     * @param nodes 探索ノード数
     * @return MultiPV分のPV（multipv昇順）
     */
    fun analyzeSfen(
        sfen: String,
        additionalMoves: List<String> = emptyList(),
        nodes: Int = DEFAULT_NODES,
    ): List<PvInfo>

    /** エンジンプロセス/インスタンスの終了。 */
    fun quit()

    /**
     * 局の区切り（USI "usinewgame"）。新しい対局の解析を始める前に呼ぶ。
     *
     * - Android（[quit] 後に毎局プロセスを再作成する運用）では create() 内で既に
     *   送信済みのため呼び出し必須ではないが、二重送信しても無害（USIプロトコル上冪等）。
     * - iOS（プロセス内エンジンを [quit] できず全局で使い回す運用）では、局ごとに
     *   [AnalysisOrchestrator] のエンジンファクトリから明示的に呼ばれることを想定する。
     */
    fun newGame()

    companion object {
        const val DEFAULT_NODES = 400_000
        const val MULTI_PV = 2
    }
}
