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
     * @param onProgress (done, total) の進捗コールバック
     * @return 局面インデックス順（0手目=初期局面〜N手目）の結果リスト。各要素はその局面の MultiPV 結果
     */
    suspend fun analyzeGame(
        moves: List<String>,
        onProgress: ((done: Int, total: Int) -> Unit)? = null,
    ): List<List<PvInfo>>
}
