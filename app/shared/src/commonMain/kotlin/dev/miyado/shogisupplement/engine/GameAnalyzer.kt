package dev.miyado.shogisupplement.engine

/**
 * 1局の全局面を解析して局面ごとの結果を返す契約。
 *
 * 実装は端末解析の [AnalysisRunner] とサーバー解析の [RemoteAnalysisRunner] の2つで、
 * どちらを渡しても [AnalysisOrchestrator] 以下（悪手判定・強さ推定・DB保存）は同じ経路を通る。
 * 解析条件（go nodes 400000 / Threads=1 / MultiPV=2 / FV_SCALE=20）は両実装で一致させてあり、
 * 同じ棋譜からは同じ結果が返る。
 */
interface GameAnalyzer {

    /**
     * @param moves 棋譜の USI 手列
     * @param onPositionResult 局面ごとの中間結果コールバック。実装は最終的に総局面数ぶん
     *   1回ずつ呼ぶ（到着順・粒度は問わない——[AnalysisRunner] も[RemoteAnalysisRunner]も
     *   局面が解け次第・並列ワーカーの完了順不同のまま呼ぶ。応答に局面単位の中間結果が
     *   一切含まれない場合でも契約を満たせるよう、[RemoteAnalysisRunner]はストリーム
     *   終端の一括結果で未到着分だけ補って呼ぶフォールバックを持つ）。
     *   [onProgress] より前に置くのは、[onProgress] だけを渡す既存呼び出し側の
     *   トレーリングラムダ構文（末尾引数扱い）を崩さないため
     * @param onProgress (done, total) の進捗コールバック
     * @return 局面インデックス順（0手目=初期局面〜N手目）の結果リスト。各要素はその局面の MultiPV 結果
     */
    suspend fun analyzeGame(
        moves: List<String>,
        onPositionResult: ((ply: Int, pvs: List<PvInfo>) -> Unit)? = null,
        onProgress: ((done: Int, total: Int) -> Unit)? = null,
    ): List<List<PvInfo>>
}
